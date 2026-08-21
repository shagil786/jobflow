package dev.jobflow.ingestion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@SpringBootApplication
public class IngestionApplication {
  public static void main(String[] args) { SpringApplication.run(IngestionApplication.class, args); }

  @RestController
  static class ServiceInfoController {
    private final String key;
    ServiceInfoController(@Value("${JOBFLOW_INTERNAL_SERVICE_KEY}") String key) {
      if (key == null || key.isBlank()) throw new IllegalArgumentException("JOBFLOW_INTERNAL_SERVICE_KEY is required");
      this.key = key;
    }
    @GetMapping("/internal/service-info")
    ServiceInfo info(@RequestHeader(value = "X-Internal-Service-Key", required = false) String provided) {
      if (provided == null || !MessageDigest.isEqual(key.getBytes(StandardCharsets.UTF_8), provided.getBytes(StandardCharsets.UTF_8))) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "internal authorization failed");
      return new ServiceInfo("ingestion-service", "scoped Gmail and browser captures", "ready");
    }
  }
  record ServiceInfo(String service, String responsibility, String status) {}
}
