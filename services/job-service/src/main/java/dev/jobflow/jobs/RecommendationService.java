package dev.jobflow.jobs;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class RecommendationService {
  private final List<OfficialJobFeedAdapter> feeds;

  public RecommendationService(List<OfficialJobFeedAdapter> feeds) { this.feeds = feeds; }

  public RecommendationResponse list(String tenantId, String userId) {
    if (tenantId == null || tenantId.isBlank() || userId == null || userId.isBlank()) {
      throw new JobService.InvalidRequestException("authenticated tenant and user are required");
    }
    List<OfficialJobFeedAdapter> enabled = feeds.stream().filter(OfficialJobFeedAdapter::enabled).toList();
    if (enabled.isEmpty()) return new RecommendationResponse("NO_FEED_CONFIGURED", List.of());
    List<OfficialJobFeedAdapter.OfficialJob> jobs = enabled.stream()
        .flatMap(feed -> feed.fetch(tenantId, userId).stream()).toList();
    if (jobs.isEmpty()) return new RecommendationResponse("NO_RECOMMENDATIONS", List.of());
    // A source-backed job may be displayed, but it is not scored positively
    // until resume/profile evidence is indexed by the shared context service.
    return new RecommendationResponse("FEED_AVAILABLE_NEEDS_PROFILE", jobs.stream().map(job ->
        new Recommendation(job.sourceId(), job.sourceId(), job.title(), job.company(), job.jobUrl(), 0.0,
            List.of(), List.of("Resume/profile evidence is not indexed"), List.of(), job.sourceFreshness(), "IGNORE", job.citations())).toList());
  }

  public record RecommendationResponse(String status, List<Recommendation> items) {}
  public record Recommendation(String recommendationId, String sourceId, String title, String company,
                               String jobUrl, double fitScore, List<String> matchingEvidence,
                               List<String> missingRequirements, List<String> conflicts,
                               String sourceFreshness, String recommendedAction, List<String> citations) {}
}
