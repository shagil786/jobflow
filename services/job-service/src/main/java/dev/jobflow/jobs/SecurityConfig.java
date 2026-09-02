package dev.jobflow.jobs;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
  @Bean
  JwtDecoder jwtDecoder(@Value("${JOBFLOW_OIDC_ISSUER_URI}") String issuerUri, @Value("${JOBFLOW_OIDC_AUDIENCE}") String audience) {
    if (audience == null || audience.isBlank()) throw new IllegalArgumentException("JOBFLOW_OIDC_AUDIENCE is required");
    NimbusJwtDecoder decoder = (NimbusJwtDecoder) JwtDecoders.fromIssuerLocation(issuerUri);
    decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuerUri), audienceValidator(audience)));
    return decoder;
  }

  static OAuth2TokenValidator<Jwt> audienceValidator(String audience) {
    return jwt -> jwt.getAudience().contains(audience)
      ? OAuth2TokenValidatorResult.success()
      : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "JWT audience is not allowed", null));
  }

  @Bean
  SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
    return http
      .csrf(csrf -> csrf.disable())
      .sessionManagement(sessions -> sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
      .authorizeHttpRequests(auth -> auth
        .requestMatchers("/actuator/health").permitAll()
        .requestMatchers("/internal/service-info").permitAll()
        .requestMatchers("/internal/v1/**").permitAll()
        .requestMatchers("/api/**").authenticated()
        .anyRequest().denyAll())
      .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> {}))
      .build();
  }
}
