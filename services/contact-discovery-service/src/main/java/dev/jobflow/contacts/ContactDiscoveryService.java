package dev.jobflow.contacts;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContactDiscoveryService {
  private final EnrichmentRunRepository runs;
  private final ContactCandidateRepository contacts;
  private final List<ContactDiscoveryAdapter> adapters;
  private final Executor executor;

  @Autowired
  public ContactDiscoveryService(EnrichmentRunRepository runs, ContactCandidateRepository contacts, List<ContactDiscoveryAdapter> adapters, Executor executor) {
    this.runs = runs; this.contacts = contacts; this.adapters = adapters; this.executor = executor;
  }

  ContactDiscoveryService(EnrichmentRunRepository runs, ContactCandidateRepository contacts, List<ContactDiscoveryAdapter> adapters) {
    this(runs, contacts, adapters, Runnable::run);
  }

  @Transactional
  public EnrichmentRunEntity enrich(String tenantId, String userId, UUID applicationId, Request request, String idempotencyKey) {
    requireOwner(tenantId, userId); if (applicationId == null) throw new InvalidRequestException("applicationId is required");
    if (idempotencyKey == null || idempotencyKey.isBlank()) throw new InvalidRequestException("X-Idempotency-Key is required");
    String requestHash = hash(request);
    var existing = runs.findByTenantIdAndUserIdAndApplicationIdAndIdempotencyKey(tenantId, userId, applicationId, idempotencyKey);
    if (existing.isPresent()) {
      if (!existing.get().getRequestHash().equals(requestHash)) throw new InvalidRequestException("idempotency key was reused with a different payload");
      return existing.get();
    }
    EnrichmentRunEntity run = runs.save(new EnrichmentRunEntity(applicationId, tenantId, userId, idempotencyKey, requestHash));
    executor.execute(() -> processRun(run, tenantId, userId, request));
    return run;
  }

  void processRun(EnrichmentRunEntity run, String tenantId, String userId, Request request) {
    try {
    run.advance(EnrichmentStatus.PUBLIC_SEARCH);
    runs.save(run);
      DiscoveryRequest discoveryRequest = new DiscoveryRequest(request.publicSources() == null ? List.of() : request.publicSources(), request.gmailEvidence() == null ? List.of() : request.gmailEvidence());
    List<DiscoveredContact> discovered = new ArrayList<>();
    for (ContactDiscoveryAdapter adapter : adapters) {
      if (adapter instanceof GmailContactDiscoveryAdapter) { run.advance(EnrichmentStatus.GMAIL_SEARCH); runs.save(run); }
      if (adapter instanceof ProviderContactDiscoveryAdapter provider && provider.enabled()) { run.advance(EnrichmentStatus.PROVIDER_SEARCH); runs.save(run); }
      discovered.addAll(adapter.discover(discoveryRequest));
    }
    run.advance(EnrichmentStatus.VERIFYING); runs.save(run);
    int persisted = 0; int verified = 0;
    for (DiscoveredContact contact : deduplicate(discovered)) {
      if (!ContactSafetyPolicy.canPersist(contact)) continue;
      var prior = contacts.findByTenantIdAndUserIdAndApplicationIdAndContentHash(tenantId, userId, run.getApplicationId(), contact.contentHash());
      if (prior.isEmpty()) { contacts.save(new ContactCandidateEntity(run.getApplicationId(), tenantId, userId, contact)); persisted++; }
      if (contact.status() == ContactStatus.VERIFIED) verified++;
    }
    ContactCandidateEntity best = contacts.findByTenantIdAndUserIdAndApplicationIdOrderByConfidenceDescCreatedAtAsc(tenantId, userId, run.getApplicationId()).stream()
        .filter(candidate -> candidate.getStatus() == ContactStatus.VERIFIED && candidate.getAllowedUse() == AllowedUse.OUTREACH_DRAFT && candidate.getConfidence() >= 0.85)
        .findFirst().orElse(null);
    if (best != null) { best.preselect(); contacts.save(best); }
    run.complete(verified > 0 ? EnrichmentStatus.READY : EnrichmentStatus.NO_VERIFIED_CONTACT, persisted, verified);
    runs.save(run);
    } catch (RuntimeException exception) {
      run.complete(EnrichmentStatus.FAILED, 0, 0);
      runs.save(run);
    }
  }

  @Transactional(readOnly = true)
  public List<ContactCandidateEntity> list(String tenantId, String userId, UUID applicationId) {
    requireOwner(tenantId, userId); return contacts.findByTenantIdAndUserIdAndApplicationIdOrderByConfidenceDescCreatedAtAsc(tenantId, userId, applicationId);
  }

  @Transactional(readOnly = true)
  public EnrichmentRunEntity latestRun(String tenantId, String userId, UUID applicationId) {
    requireOwner(tenantId, userId);
    return runs.findFirstByTenantIdAndUserIdAndApplicationIdOrderByCreatedAtDesc(tenantId, userId, applicationId).orElseThrow(() -> new NotFoundException("enrichment run not found"));
  }

  @Transactional
  public ContactCandidateEntity select(String tenantId, String userId, UUID applicationId, UUID contactId) {
    requireOwner(tenantId, userId);
    ContactCandidateEntity candidate = contacts.findById(contactId).orElseThrow(() -> new NotFoundException("contact not found"));
    if (!tenantId.equals(candidate.getTenantId()) || !userId.equals(candidate.getUserId()) || !applicationId.equals(candidate.getApplicationId())) throw new NotFoundException("contact not found");
    if (candidate.getStatus() == ContactStatus.REJECTED || candidate.getStatus() == ContactStatus.EXPIRED || candidate.getAllowedUse() != AllowedUse.OUTREACH_DRAFT || candidate.getEmail() == null) throw new InvalidRequestException("contact cannot be selected for outreach");
    contacts.findByTenantIdAndUserIdAndApplicationIdOrderByConfidenceDescCreatedAtAsc(tenantId, userId, applicationId).forEach(other -> { if (!other.getId().equals(contactId)) other.unselect(); });
    candidate.select(); return contacts.save(candidate);
  }

  @Transactional
  public ContactCandidateEntity preselect(String tenantId, String userId, UUID applicationId, UUID contactId) {
    requireOwner(tenantId, userId);
    ContactCandidateEntity candidate = contacts.findById(contactId).orElseThrow(() -> new NotFoundException("contact not found"));
    if (!tenantId.equals(candidate.getTenantId()) || !userId.equals(candidate.getUserId()) || !applicationId.equals(candidate.getApplicationId())) throw new NotFoundException("contact not found");
    if (candidate.getStatus() != ContactStatus.VERIFIED || candidate.getAllowedUse() != AllowedUse.OUTREACH_DRAFT || candidate.getEmail() == null || candidate.getConfidence() < 0.85) throw new InvalidRequestException("contact does not meet preselection policy");
    contacts.findByTenantIdAndUserIdAndApplicationIdOrderByConfidenceDescCreatedAtAsc(tenantId, userId, applicationId).forEach(other -> { if (!other.getId().equals(contactId)) other.unpreselect(); });
    candidate.preselect(); return contacts.save(candidate);
  }

  private List<DiscoveredContact> deduplicate(List<DiscoveredContact> contacts) {
    return contacts.stream().filter(Objects::nonNull).collect(java.util.stream.Collectors.toMap(DiscoveredContact::contentHash, c -> c, (left, right) -> left.confidence() >= right.confidence() ? left : right)).values().stream().toList();
  }
  private String hash(Request request) { try { String value = String.valueOf(request); return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException(e); } }
  private void requireOwner(String tenantId, String userId) { if (tenantId == null || tenantId.isBlank() || userId == null || userId.isBlank()) throw new InvalidRequestException("authenticated tenant and user are required"); }
  public record Request(List<PublicSource> publicSources, List<GmailEvidence> gmailEvidence) {}
  public static class InvalidRequestException extends RuntimeException { public InvalidRequestException(String message) { super(message); } }
  public static class NotFoundException extends RuntimeException { public NotFoundException(String message) { super(message); } }
}
