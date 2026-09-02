package dev.jobflow.jobs;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class RecommendationServiceTest {
  @Test
  void returnsHonestNoFeedStateWithoutPlaceholderJobs() {
    RecommendationService service = new RecommendationService(List.of());

    var response = service.list("tenant-a", "user-a");

    assertThat(response.status()).isEqualTo("NO_FEED_CONFIGURED");
    assertThat(response.items()).isEmpty();
  }

  @Test
  void doesNotRankUntilAnEnabledOfficialFeedExists() {
    OfficialJobFeedAdapter disabled = new OfficialJobFeedAdapter() {
      public String sourceName() { return "disabled-test-feed"; }
      public boolean enabled() { return false; }
      public List<OfficialJob> fetch(String tenantId, String userId) { return List.of(); }
    };

    var response = new RecommendationService(List.of(disabled)).list("tenant-a", "user-a");

    assertThat(response.status()).isEqualTo("NO_FEED_CONFIGURED");
    assertThat(response.items()).isEmpty();
  }

  @Test
  void exposesSourcedJobsWithoutInventingResumeFit() {
    OfficialJobFeedAdapter feed = new OfficialJobFeedAdapter() {
      public String sourceName() { return "official-test-feed"; }
      public boolean enabled() { return true; }
      public List<OfficialJob> fetch(String tenantId, String userId) {
        return List.of(new OfficialJob("job-1", "Backend Engineer", "Example Co", "https://jobs.example/job-1", "Java", "2026-08-23", List.of("https://jobs.example/feed")));
      }
    };

    var response = new RecommendationService(List.of(feed)).list("tenant-a", "user-a");

    assertThat(response.status()).isEqualTo("FEED_AVAILABLE_NEEDS_PROFILE");
    assertThat(response.items()).singleElement().satisfies(item -> {
      assertThat(item.fitScore()).isZero();
      assertThat(item.citations()).containsExactly("https://jobs.example/feed");
      assertThat(item.missingRequirements()).contains("Resume/profile evidence is not indexed");
    });
  }
}
