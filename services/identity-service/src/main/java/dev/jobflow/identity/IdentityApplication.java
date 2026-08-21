package dev.jobflow.identity;

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
public class IdentityApplication {
  public static void main(String[] args) { SpringApplication.run(IdentityApplication.class, args); }

  @RestController
  static class ServiceInfoController {
    private final String internalServiceKey;

    ServiceInfoController(@Value("${JOBFLOW_INTERNAL_SERVICE_KEY}") String internalServiceKey) {
      if (internalServiceKey == null || internalServiceKey.isBlank()) throw new IllegalArgumentException("JOBFLOW_INTERNAL_SERVICE_KEY is required");
      this.internalServiceKey = internalServiceKey;
    }

    @GetMapping("/internal/service-info")
    ServiceInfo info(@RequestHeader(value = "X-Internal-Service-Key", required = false) String providedKey) {
      if (providedKey == null || !MessageDigest.isEqual(internalServiceKey.getBytes(StandardCharsets.UTF_8), providedKey.getBytes(StandardCharsets.UTF_8))) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "internal authorization failed");
      }
      return new ServiceInfo("identity-service", "tenant and OAuth ownership", "ready");
    }
  }
  record ServiceInfo(String service, String responsibility, String status) {}
}
