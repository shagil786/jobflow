package dev.jobflow.jobs;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/recommendations")
public class RecommendationController {
  private final RecommendationService service;
  public RecommendationController(RecommendationService service) { this.service = service; }

  @GetMapping
  public RecommendationService.RecommendationResponse list(@AuthenticationPrincipal Jwt principal) {
    return service.list(tenantId(principal), principal == null ? null : principal.getSubject());
  }

  private static String tenantId(Jwt principal) {
    if (principal == null) throw new JobService.InvalidRequestException("authenticated principal is required");
    String tenant = principal.getClaimAsString("tenant_id");
    if (tenant == null || tenant.isBlank()) tenant = principal.getClaimAsString("https://jobflow.app/tenant_id");
    if (tenant != null && !tenant.isBlank()) return tenant;
    try {
      return "personal:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(principal.getSubject().getBytes(StandardCharsets.UTF_8)));
    } catch (Exception exception) { throw new IllegalStateException("unable to derive personal tenant", exception); }
  }
}
