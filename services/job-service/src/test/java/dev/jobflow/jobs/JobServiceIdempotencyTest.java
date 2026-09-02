package dev.jobflow.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(JobService.class)
class JobServiceIdempotencyTest {
  @Autowired JobService service;
  @Autowired ApplicationRepository applications;
  @Autowired TimelineEventRepository events;

  @Test
  void rejectsReusingAnIdempotencyKeyForADifferentPayload() {
    var original = new JobService.CaptureRequest(URI.create("https://jobs.example.test/one"), "Role one", "Company", "Role one", null, "extension", null, "2026-08-21T10:00:00Z");
    var changed = new JobService.CaptureRequest(URI.create("https://jobs.example.test/two"), "Role two", "Company", "Role two", null, "extension", null, "2026-08-21T10:00:00Z");

    service.capture("tenant-a", "user-a", original, "key-1");

    assertThatThrownBy(() -> service.capture("tenant-a", "user-a", changed, "key-1"))
      .isInstanceOf(JobService.InvalidRequestException.class)
      .hasMessage("idempotency key was reused with a different payload");
  }

  @Test
  void createsReviewedMessageOnlyOnceForTheSameReview() {
    UUID reviewId = UUID.randomUUID();
    var request = new JobService.ReviewedMessageRequest(ReviewImportMode.CREATE, reviewId, "suggestion-1", "message-1", "Example Corp", "Frontend Engineer", null, null);

    ApplicationEntity first = service.importReviewedMessage("tenant-a", "user-a", request);
    ApplicationEntity replay = service.importReviewedMessage("tenant-a", "user-a", request);

    assertThat(replay.getId()).isEqualTo(first.getId());
    assertThat(events.findByTenantIdAndUserIdAndApplicationIdOrderByOccurredAtAsc("tenant-a", "user-a", first.getId())).hasSize(1);
  }

  @Test
  void mergingReviewedMessageIsTenantScopedAndTimelineIdempotent() {
    var capture = new JobService.CaptureRequest(URI.create("https://jobs.example.test/one"), "Role one", "Company", "Role one", null, "extension", null, "2026-08-21T10:00:00Z");
    ApplicationEntity application = service.capture("tenant-a", "user-a", capture, "key-merge");
    UUID reviewId = UUID.randomUUID();
    var request = new JobService.ReviewedMessageRequest(ReviewImportMode.MERGE, reviewId, "suggestion-1", "message-merge", "Updated Corp", "Updated Role", null, application.getId());

    service.importReviewedMessage("tenant-a", "user-a", request);
    service.importReviewedMessage("tenant-a", "user-a", request);

    assertThat(application.getCompany()).isEqualTo("Updated Corp");
    assertThat(events.findByTenantIdAndUserIdAndApplicationIdOrderByOccurredAtAsc("tenant-a", "user-a", application.getId()))
        .extracting(TimelineEventEntity::getType)
        .containsExactly("JOB_CAPTURED", "EMAIL_REVIEWED");
    assertThatThrownBy(() -> service.importReviewedMessage("tenant-b", "user-b", request))
        .isInstanceOf(JobService.NotFoundException.class);
  }

  @Test
  void laterEmployerRejectionClosesEarlierActiveRecordsAcrossEmailEvents() {
    var capture = new JobService.CaptureRequest(
        URI.create("https://jobs.example.test/zuora"), "Software Engineer III", "Zuora",
        "Software Engineer III", null, "extension", null, "2026-08-01T10:00:00Z");
    ApplicationEntity captured = service.capture("tenant-a", "user-a", capture, "capture-zuora");

    var interview = new InternalApplicationController.AutoPromoteRequest(
        "tenant-a", "user-a", "suggestion-interview", "message-interview", "thread-zuora",
        "INBOUND", "Zuora", "Software Engineer III", "INTERVIEW_INVITATION", null);
    ApplicationEntity interviewRecord = service.importAutoPromoted("tenant-a", "user-a", interview);

    var rejection = new InternalApplicationController.AutoPromoteRequest(
        "tenant-a", "user-a", "suggestion-rejection", "message-rejection", "thread-rejection",
        "INBOUND", "zuora", "Software Engineer III", "REJECTION", null);
    ApplicationEntity rejectionRecord = service.importAutoPromoted("tenant-a", "user-a", rejection);

    assertThat(applications.findByTenantIdAndUserId("tenant-a", "user-a"))
        .extracting(ApplicationEntity::getStatus)
        .containsOnly(ApplicationStatus.REJECTED);
    assertThat(captured.getStatus()).isEqualTo(ApplicationStatus.REJECTED);
    assertThat(interviewRecord.getStatus()).isEqualTo(ApplicationStatus.REJECTED);
    assertThat(rejectionRecord.getStatus()).isEqualTo(ApplicationStatus.REJECTED);
    assertThat(events.findByTenantIdAndUserIdAndApplicationIdOrderByOccurredAtAsc(
        "tenant-a", "user-a", captured.getId()))
        .extracting(TimelineEventEntity::getType)
        .contains("EMPLOYER_REJECTED");

    ApplicationEntity replay = service.importAutoPromoted("tenant-a", "user-a", rejection);
    assertThat(replay.getId()).isEqualTo(rejectionRecord.getId());
  }
}
