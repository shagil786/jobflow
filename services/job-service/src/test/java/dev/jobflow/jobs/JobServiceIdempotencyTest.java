package dev.jobflow.jobs;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(JobService.class)
class JobServiceIdempotencyTest {
  @Autowired JobService service;

  @Test
  void rejectsReusingAnIdempotencyKeyForADifferentPayload() {
    var original = new JobService.CaptureRequest(URI.create("https://jobs.example.test/one"), "Role one", "Company", "Role one", null, "extension", null, "2026-08-21T10:00:00Z");
    var changed = new JobService.CaptureRequest(URI.create("https://jobs.example.test/two"), "Role two", "Company", "Role two", null, "extension", null, "2026-08-21T10:00:00Z");

    service.capture("tenant-a", "user-a", original, "key-1");

    assertThatThrownBy(() -> service.capture("tenant-a", "user-a", changed, "key-1"))
      .isInstanceOf(JobService.InvalidRequestException.class)
      .hasMessage("idempotency key was reused with a different payload");
  }
}
