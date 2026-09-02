package dev.jobflow.contacts;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/applications")
public class ContactController {
  private final ContactDiscoveryService service;
  public ContactController(ContactDiscoveryService service) { this.service = service; }

  @PostMapping("/{applicationId}/enrichment")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public EnrichmentResponse enrich(@AuthenticationPrincipal Jwt principal, @PathVariable("applicationId") UUID applicationId, @RequestHeader("X-Idempotency-Key") String idempotencyKey, @Valid @RequestBody EnrichmentPayload payload) {
    var run = service.enrich(tenantId(principal), userId(principal), applicationId, new ContactDiscoveryService.Request(payload.publicSources() == null ? List.of() : payload.publicSources(), payload.gmailEvidence() == null ? List.of() : payload.gmailEvidence()), idempotencyKey);
    return EnrichmentResponse.from(run);
  }

  @GetMapping("/{applicationId}/contacts")
  public List<ContactResponse> contacts(@AuthenticationPrincipal Jwt principal, @PathVariable("applicationId") UUID applicationId) {
    return service.list(tenantId(principal), userId(principal), applicationId).stream().map(ContactResponse::from).toList();
  }

  @GetMapping("/{applicationId}/enrichment")
  public EnrichmentResponse enrichment(@AuthenticationPrincipal Jwt principal, @PathVariable("applicationId") UUID applicationId) {
    return EnrichmentResponse.from(service.latestRun(tenantId(principal), userId(principal), applicationId));
  }

  @PostMapping("/{applicationId}/contacts/{contactId}/select")
  public ContactResponse select(@AuthenticationPrincipal Jwt principal, @PathVariable("applicationId") UUID applicationId, @PathVariable("contactId") UUID contactId, @RequestHeader(value = "X-Idempotency-Key", required = false) String ignored) {
    return ContactResponse.from(service.select(tenantId(principal), userId(principal), applicationId, contactId));
  }

  @PostMapping("/{applicationId}/contacts/{contactId}/preselect")
  public ContactResponse preselect(@AuthenticationPrincipal Jwt principal, @PathVariable("applicationId") UUID applicationId, @PathVariable("contactId") UUID contactId) {
    return ContactResponse.from(service.preselect(tenantId(principal), userId(principal), applicationId, contactId));
  }

  private static String userId(Jwt jwt) { if (jwt == null || jwt.getSubject() == null || jwt.getSubject().isBlank()) throw new ContactDiscoveryService.InvalidRequestException("token subject is required"); return jwt.getSubject(); }
  private static String tenantId(Jwt jwt) { String tenant = jwt == null ? null : jwt.getClaimAsString("tenant_id"); if (tenant == null || tenant.isBlank()) tenant = jwt == null ? null : jwt.getClaimAsString("https://jobflow.app/tenant_id"); if (tenant != null && !tenant.isBlank()) return tenant; try { return "personal:" + java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(userId(jwt).getBytes(java.nio.charset.StandardCharsets.UTF_8))); } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); } }

  public record EnrichmentPayload(String correlationId, List<PublicSource> publicSources, List<GmailEvidence> gmailEvidence) {}
  public record EnrichmentResponse(UUID enrichmentId, UUID applicationId, EnrichmentStatus status, int discoveredContacts, int verifiedContacts, Instant completedAt) { static EnrichmentResponse from(EnrichmentRunEntity run) { return new EnrichmentResponse(run.getId(), run.getApplicationId(), run.getStatus(), run.getDiscoveredContacts(), run.getVerifiedContacts(), run.getCompletedAt()); } }
  public record ContactResponse(UUID contactId, UUID applicationId, String name, String role, String company, String email, String profileUrl, String sourceUrl, ContactSourceType sourceType, String evidence, String sourceMessageId, String sourceThreadId, double confidence, ContactStatus status, String verificationStatus, String provider, String providerVersion, AllowedUse allowedUse, boolean selected, boolean preselected, Instant createdAt) { static ContactResponse from(ContactCandidateEntity c) { return new ContactResponse(c.getId(), c.getApplicationId(), c.getName(), c.getRole(), c.getCompany(), c.getEmail(), c.getProfileUrl(), c.getSourceUrl(), c.getSourceType(), c.getEvidence(), c.getSourceMessageId(), c.getSourceThreadId(), c.getConfidence(), c.getStatus(), c.getVerificationStatus(), c.getProvider(), c.getProviderVersion(), c.getAllowedUse(), c.isSelected(), c.isPreselected(), c.getCreatedAt()); } }
}
