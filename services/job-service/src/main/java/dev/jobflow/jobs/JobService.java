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
      ApplicationEntity prior = existing.get();
      if (prior.getIdempotencyPayloadHash() != null && !prior.getIdempotencyPayloadHash().equals(payloadHash)) {
        if (!sameCaptureDetails(prior, request)) throw new InvalidRequestException("idempotency key was reused with a different payload");
        // Migrate records created by the old timestamp-sensitive hash without
        // creating a duplicate application.
        prior.setIdempotencyPayloadHash(payloadHash);
        return applications.save(prior);
      }
      return prior;
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
      entity.setSourceMessageId(emptyToNull(request.messageId()));
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
    if (entity.getSourceMessageId() == null) entity.setSourceMessageId(emptyToNull(request.messageId()));
    String summary = "Reviewed Gmail message " + request.messageId();
    if (!events.existsByTenantIdAndUserIdAndApplicationIdAndTypeAndSummary(tenantId, userId, applicationId, "EMAIL_REVIEWED", summary)) events.save(new TimelineEventEntity(tenantId, userId, applicationId, "EMAIL_REVIEWED", summary));
    return applications.save(entity);
  }

  @Transactional
  public ApplicationEntity importAutoPromoted(String tenantId, String userId, InternalApplicationController.AutoPromoteRequest request) {
    String idempotencyKey = "auto:gmail:" + request.suggestionId();
    var existing = applications.findByTenantIdAndUserIdAndIdempotencyKey(tenantId, userId, idempotencyKey);
    if (existing.isPresent()) {
      if ("REJECTION".equals(request.intent())) reconcileRejectedEmployer(tenantId, userId, request.company());
      return existing.get();
    }
    ApplicationEntity entity = new ApplicationEntity();
    entity.setTenantId(tenantId); entity.setUserId(userId);
    entity.setCompany(blankAsUnknown(request.company())); entity.setRole(requireValue(request.role(), "role"));
    entity.setCaptureTitle(entity.getRole()); entity.setCapturedAt(Instant.now()); entity.setSource("gmail-auto");
    entity.setSourceThreadId(emptyToNull(request.threadId())); entity.setSourceMessageId(emptyToNull(request.messageId()));
    entity.setSourceDirection(normalizeDirection(request.direction()));
    entity.setStatus(statusFor(request.intent())); entity.setIdempotencyKey(idempotencyKey); entity.setIdempotencyPayloadHash(request.suggestionId());
    ApplicationEntity saved = applications.save(entity);
    events.save(new TimelineEventEntity(tenantId, userId, saved.getId(), "EMAIL_AUTO_PROMOTED", "Gmail thread promoted from message " + request.messageId()));
    if ("REJECTION".equals(request.intent())) reconcileRejectedEmployer(tenantId, userId, request.company());
    return saved;
  }

  private void reconcileRejectedEmployer(String tenantId, String userId, String company) {
    String rejectedCompany = companyKey(company);
    if (rejectedCompany.isBlank() || "unknown company".equals(rejectedCompany)) return;
    applications.findByTenantIdAndUserId(tenantId, userId).stream()
        .filter(application -> companyKey(application.getCompany()).equals(rejectedCompany))
        .filter(application -> application.getStatus() != ApplicationStatus.REJECTED && application.getStatus() != ApplicationStatus.CLOSED)
        .forEach(application -> {
          application.setStatus(ApplicationStatus.REJECTED);
          applications.save(application);
          events.save(new TimelineEventEntity(tenantId, userId, application.getId(), "EMPLOYER_REJECTED", "Employer rejection reconciled from Gmail evidence"));
        });
  }

  private static ApplicationStatus statusFor(String intent) {
    if (intent == null) return ApplicationStatus.CAPTURED;
    return switch (intent) {
      case "APPLICATION_CONFIRMATION" -> ApplicationStatus.APPLIED;
      case "INTERVIEW_INVITATION", "INTERVIEW_RESCHEDULE", "INTERVIEW_FEEDBACK", "ASSESSMENT_INVITATION" -> ApplicationStatus.INTERVIEW;
      case "REJECTION" -> ApplicationStatus.REJECTED;
      case "RECRUITER_OUTREACH", "RECRUITER_REPLY", "FOLLOW_UP_REQUEST" -> ApplicationStatus.OUTREACH;
      default -> ApplicationStatus.CAPTURED;
    };
  }

  public List<TimelineEventEntity> timeline(String tenantId, String userId, UUID id) {
    applications.findByTenantIdAndUserIdAndId(tenantId, userId, id).orElseThrow(() -> new NotFoundException("application not found"));
    return events.findByTenantIdAndUserIdAndApplicationIdOrderByOccurredAtAsc(tenantId, userId, id);
  }

  private static String blankAsUnknown(String value) { return value == null || value.isBlank() ? "Unknown company" : value.trim(); }
  private static String emptyToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
  private static boolean sameCaptureDetails(ApplicationEntity prior, CaptureRequest request) {
    return java.util.Objects.equals(prior.getJobUrl(), request.url().toString())
        && java.util.Objects.equals(prior.getCaptureTitle(), emptyToNull(request.title()))
        && java.util.Objects.equals(prior.getCompany(), emptyToNull(request.company()) == null ? "Unknown company" : emptyToNull(request.company()))
        && java.util.Objects.equals(prior.getRole(), emptyToNull(request.role()))
        && java.util.Objects.equals(prior.getLocation(), emptyToNull(request.location()))
        && java.util.Objects.equals(prior.getSource(), emptyToNull(request.source()))
        && java.util.Objects.equals(prior.getDescriptionPreview(), emptyToNull(request.descriptionPreview()));
  }
  private static String requireValue(String value, String field) { if (value == null || value.isBlank()) throw new InvalidRequestException(field + " is required"); return value.trim(); }
  private static String normalizeDirection(String value) { return "INBOUND".equals(value) || "OUTBOUND".equals(value) ? value : null; }
  private static String companyKey(String value) { return value == null ? "" : value.trim().toLowerCase().replaceAll("[^a-z0-9]+", " ").trim(); }
  private static String payloadHash(CaptureRequest request) {
    // capturedAt is generated by the client and changes on every retry. It is
    // record metadata, not request identity; including it breaks idempotent
    // retries for the same captured role.
    String canonical = String.join("\u001f", request.url().toString(), request.title(), emptyToNull(request.company()), request.role(), emptyToNull(request.location()), request.source(), emptyToNull(request.descriptionPreview()));
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
