package dev.jobflow.jobs;

import java.net.URI;
import java.time.LocalDate;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobService {
  private final ApplicationRepository applications;
  private final TimelineEventRepository events;

  public JobService(ApplicationRepository applications, TimelineEventRepository events) {
    this.applications = applications; this.events = events;
  }

  @Transactional
  public ApplicationEntity capture(String tenantId, String userId, CaptureRequest request, String idempotencyKey) {
    String payloadHash = payloadHash(request);
    var existing = applications.findByTenantIdAndUserIdAndIdempotencyKey(tenantId, userId, idempotencyKey);
    if (existing.isPresent()) {
      if (existing.get().getIdempotencyPayloadHash() != null && !existing.get().getIdempotencyPayloadHash().equals(payloadHash)) throw new InvalidRequestException("idempotency key was reused with a different payload");
      return existing.get();
    }
    ApplicationEntity entity = new ApplicationEntity();
    entity.setTenantId(tenantId); entity.setUserId(userId); entity.setCompany(blankAsUnknown(request.company()));
    entity.setRole(request.role().trim()); entity.setJobUrl(request.url().toString()); entity.setCaptureTitle(request.title().trim());
    entity.setDescriptionPreview(emptyToNull(request.descriptionPreview())); entity.setCapturedAt(Instant.parse(request.capturedAt())); entity.setSource(request.source().trim());
    entity.setLocation(emptyToNull(request.location())); entity.setIdempotencyKey(idempotencyKey); entity.setIdempotencyPayloadHash(payloadHash); entity.setStatus(ApplicationStatus.CAPTURED);
    ApplicationEntity saved = applications.save(entity);
    events.save(new TimelineEventEntity(tenantId, userId, saved.getId(), "JOB_CAPTURED", "Job captured from " + saved.getSource()));
    return saved;
  }

  public List<ApplicationEntity> list(String tenantId, String userId) { return applications.findByTenantIdAndUserIdOrderByUpdatedAtDesc(tenantId, userId); }

  @Transactional
  public ApplicationEntity update(String tenantId, String userId, UUID id, UpdateRequest request) {
    ApplicationEntity entity = applications.findByTenantIdAndUserIdAndId(tenantId, userId, id).orElseThrow(() -> new NotFoundException("application not found"));
    if (request.status() != null) entity.setStatus(request.status());
    if (request.nextFollowUpDate() != null) entity.setNextFollowUpDate(request.nextFollowUpDate());
    if (request.company() != null && !request.company().isBlank()) entity.setCompany(request.company().trim());
    if (request.role() != null && !request.role().isBlank()) entity.setRole(request.role().trim());
    ApplicationEntity saved = applications.save(entity);
    events.save(new TimelineEventEntity(tenantId, userId, saved.getId(), "APPLICATION_UPDATED", "Application details updated by the user"));
    return saved;
  }

  @Transactional
  public ApplicationEntity importReviewedMessage(String tenantId, String userId, ReviewedMessageRequest request) {
    if (request.mode() == ReviewImportMode.CREATE) {
      String idempotencyKey = "review:" + request.reviewId();
      var existing = applications.findByTenantIdAndUserIdAndIdempotencyKey(tenantId, userId, idempotencyKey);
      if (existing.isPresent()) return existing.get();
      ApplicationEntity entity = new ApplicationEntity();
      entity.setTenantId(tenantId); entity.setUserId(userId);
      entity.setCompany(blankAsUnknown(request.company())); entity.setRole(requireValue(request.role(), "role"));
      entity.setCaptureTitle(entity.getRole()); entity.setCapturedAt(Instant.now()); entity.setSource("gmail-review");
      entity.setStatus(ApplicationStatus.CAPTURED); entity.setIdempotencyKey(idempotencyKey); entity.setIdempotencyPayloadHash(request.reviewId().toString());
      ApplicationEntity saved = applications.save(entity);
      events.save(new TimelineEventEntity(tenantId, userId, saved.getId(), "EMAIL_REVIEWED", "Reviewed Gmail message " + request.messageId()));
      return saved;
    }
    UUID applicationId = request.applicationId();
    if (applicationId == null) throw new InvalidRequestException("applicationId is required when merging");
    ApplicationEntity entity = applications.findByTenantIdAndUserIdAndId(tenantId, userId, applicationId).orElseThrow(() -> new NotFoundException("application not found"));
    if (request.company() != null && !request.company().isBlank()) entity.setCompany(request.company().trim());
    if (request.role() != null && !request.role().isBlank()) entity.setRole(request.role().trim());
    String summary = "Reviewed Gmail message " + request.messageId();
    if (!events.existsByTenantIdAndUserIdAndApplicationIdAndTypeAndSummary(tenantId, userId, applicationId, "EMAIL_REVIEWED", summary)) events.save(new TimelineEventEntity(tenantId, userId, applicationId, "EMAIL_REVIEWED", summary));
    return applications.save(entity);
  }

  public List<TimelineEventEntity> timeline(String tenantId, String userId, UUID id) {
    applications.findByTenantIdAndUserIdAndId(tenantId, userId, id).orElseThrow(() -> new NotFoundException("application not found"));
    return events.findByTenantIdAndUserIdAndApplicationIdOrderByOccurredAtAsc(tenantId, userId, id);
  }

  private static String blankAsUnknown(String value) { return value == null || value.isBlank() ? "Unknown company" : value.trim(); }
  private static String emptyToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
  private static String requireValue(String value, String field) { if (value == null || value.isBlank()) throw new InvalidRequestException(field + " is required"); return value.trim(); }
  private static String payloadHash(CaptureRequest request) {
    String canonical = String.join("\u001f", request.url().toString(), request.title(), emptyToNull(request.company()), request.role(), emptyToNull(request.location()), request.source(), emptyToNull(request.descriptionPreview()), request.capturedAt());
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
      return java.util.HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  public record CaptureRequest(URI url, String title, String company, String role, String location, String source, String descriptionPreview, String capturedAt) {}
  public record UpdateRequest(ApplicationStatus status, String company, String role, LocalDate nextFollowUpDate) {}
  public record ReviewedMessageRequest(ReviewImportMode mode, UUID reviewId, String suggestionId, String messageId, String company, String role, LocalDate applicationDate, UUID applicationId) {}
  public static class NotFoundException extends RuntimeException { public NotFoundException(String message) { super(message); } }
  public static class InvalidRequestException extends RuntimeException { public InvalidRequestException(String message) { super(message); } }
}
