package dev.jobflow.jobs;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@RestController
@RequestMapping("/api/v1/applications")
public class JobController {
  private final JobService service;
  public JobController(JobService service) { this.service = service; }

  @GetMapping
  public List<ApplicationResponse> list(@AuthenticationPrincipal Jwt principal) {
    return service.list(tenantId(principal), userId(principal)).stream().map(ApplicationResponse::from).toList();
  }

  @PostMapping("/capture")
  @ResponseStatus(HttpStatus.CREATED)
  public ApplicationResponse capture(@AuthenticationPrincipal Jwt principal, @RequestHeader("X-Idempotency-Key") String idempotencyKey, @Valid @RequestBody CapturePayload payload) {
    if (!payload.url().getScheme().equalsIgnoreCase("http") && !payload.url().getScheme().equalsIgnoreCase("https")) throw new JobService.InvalidRequestException("only http and https job URLs are supported");
    return ApplicationResponse.from(service.capture(tenantId(principal), userId(principal), new JobService.CaptureRequest(payload.url(), payload.title(), payload.company(), payload.role(), payload.location(), payload.source(), payload.descriptionPreview(), payload.capturedAt()), idempotencyKey));
  }

  @PatchMapping("/{id}")
  public ApplicationResponse update(@AuthenticationPrincipal Jwt principal, @PathVariable("id") UUID id, @Valid @RequestBody JobService.UpdateRequest request) {
    return ApplicationResponse.from(service.update(tenantId(principal), userId(principal), id, request));
  }

  @PostMapping("/from-reviewed-message")
  @ResponseStatus(HttpStatus.CREATED)
  public ApplicationResponse importReviewedMessage(@AuthenticationPrincipal Jwt principal, @Valid @RequestBody JobService.ReviewedMessageRequest request) {
    if (request.mode() == null || request.reviewId() == null || request.messageId() == null || request.messageId().isBlank()) throw new JobService.InvalidRequestException("mode, reviewId, and messageId are required");
    return ApplicationResponse.from(service.importReviewedMessage(tenantId(principal), userId(principal), request));
  }

  @GetMapping("/{id}/timeline")
  public List<TimelineResponse> timeline(@AuthenticationPrincipal Jwt principal, @PathVariable("id") UUID id) {
    return service.timeline(tenantId(principal), userId(principal), id).stream().map(event -> new TimelineResponse(event.getId(), event.getType(), event.getSummary(), event.getOccurredAt())).toList();
  }

  private static String userId(Jwt principal) { if (principal == null || principal.getSubject() == null || principal.getSubject().isBlank()) throw new JobService.InvalidRequestException("token subject is required"); return principal.getSubject(); }
  private static String tenantId(Jwt principal) {
    if (principal == null) throw new JobService.InvalidRequestException("authenticated principal is required");
    String tenant = principal.getClaimAsString("tenant_id");
    if (tenant == null || tenant.isBlank()) tenant = principal.getClaimAsString("https://jobflow.app/tenant_id");
    if (tenant != null && !tenant.isBlank()) return tenant;
    String subject = userId(principal);
    try {
      return "personal:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(subject.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 is unavailable", impossible);
    }
  }

  public record CapturePayload(@NotNull URI url, @NotBlank @Size(max = 240) String title, @Size(max = 240) String company, @NotBlank @Size(max = 240) String role, @Size(max = 240) String location, @NotBlank @Size(max = 80) String source, @Size(max = 2_000) String descriptionPreview, @NotBlank @Size(max = 40) String capturedAt) {}
  public record ApplicationResponse(UUID id, String company, String role, String jobUrl, String source, String location, String captureTitle, String descriptionPreview, Instant capturedAt, ApplicationStatus status, Instant updatedAt, String sourceThreadId, String sourceMessageId, String sourceDirection) {
    static ApplicationResponse from(ApplicationEntity entity) { return new ApplicationResponse(entity.getId(), entity.getCompany(), entity.getRole(), entity.getJobUrl(), entity.getSource(), entity.getLocation(), entity.getCaptureTitle(), entity.getDescriptionPreview(), entity.getCapturedAt(), entity.getStatus(), entity.getUpdatedAt(), entity.getSourceThreadId(), entity.getSourceMessageId(), entity.getSourceDirection()); }
  }
  public record TimelineResponse(UUID id, String type, String summary, Instant occurredAt) {}
}
