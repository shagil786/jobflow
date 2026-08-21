package dev.jobflow.jobs;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class SecurityConfigTest {
  @Test
  void acceptsOnlyTheConfiguredAudience() {
    Jwt valid = jwt(List.of("https://api.jobflow.local"));
    Jwt wrong = jwt(List.of("https://another-api.example"));

    assertThat(SecurityConfig.audienceValidator("https://api.jobflow.local").validate(valid).hasErrors()).isFalse();
    assertThat(SecurityConfig.audienceValidator("https://api.jobflow.local").validate(wrong).hasErrors()).isTrue();
  }

  private static Jwt jwt(List<String> audience) {
    return Jwt.withTokenValue("token")
      .header("alg", "RS256")
      .subject("user")
      .issuer("https://issuer.example")
      .audience(audience)
      .issuedAt(Instant.now())
      .expiresAt(Instant.now().plusSeconds(300))
      .build();
  }
}
