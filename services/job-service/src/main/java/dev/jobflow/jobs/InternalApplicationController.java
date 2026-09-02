package dev.jobflow.jobs;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/v1/applications")
class InternalApplicationController {
  private final JobService service;
  private final String key;

  InternalApplicationController(JobService service, @Value("${JOBFLOW_INTERNAL_SERVICE_KEY}") String key) {
    if (key == null || key.isBlank()) throw new IllegalArgumentException("JOBFLOW_INTERNAL_SERVICE_KEY is required");
    this.service = service; this.key = key;
  }

  @PostMapping("/auto-promote")
  @ResponseStatus(HttpStatus.CREATED)
  JobController.ApplicationResponse autoPromote(@RequestHeader("X-Internal-Service-Key") String provided,
      @RequestBody AutoPromoteRequest request) {
    authorize(provided);
    if (request.tenantId() == null || request.tenantId().isBlank() || request.userId() == null || request.userId().isBlank())
      throw new JobService.InvalidRequestException("tenantId and userId are required");
    if (request.suggestionId() == null || request.suggestionId().isBlank() || request.messageId() == null || request.messageId().isBlank())
      throw new JobService.InvalidRequestException("suggestionId and messageId are required");
    return JobController.ApplicationResponse.from(service.importAutoPromoted(request.tenantId(), request.userId(), request));
  }

  private void authorize(String provided) {
    if (provided == null || !MessageDigest.isEqual(key.getBytes(StandardCharsets.UTF_8), provided.getBytes(StandardCharsets.UTF_8)))
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "internal authorization failed");
  }

  record AutoPromoteRequest(String tenantId, String userId, String suggestionId, String messageId,
      String threadId, String direction, String company, String role, String intent, LocalDate applicationDate) {}
}
