package dev.jobflow.jobs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Reads operator-configured official JSON feeds. The feed URL is an explicit
 * integration boundary; this adapter does not crawl job boards or LinkedIn.
 */
@Component
public class ConfiguredOfficialJobFeedAdapter implements OfficialJobFeedAdapter {
  private final List<String> urls;
  private final RestClient client = RestClient.builder().build();
  private final ObjectMapper mapper = new ObjectMapper();

  public ConfiguredOfficialJobFeedAdapter(@Value("${JOBFLOW_OFFICIAL_JOB_FEED_URLS:}") String configuredUrls) {
    this.urls = configuredUrls == null ? List.of() : List.of(configuredUrls.split(",")).stream()
        .map(String::trim).filter(value -> !value.isBlank()).toList();
  }

  @Override public String sourceName() { return "configured-official-json"; }
  @Override public boolean enabled() { return !urls.isEmpty(); }

  @Override public List<OfficialJob> fetch(String tenantId, String userId) {
    List<OfficialJob> jobs = new ArrayList<>();
    for (String configuredUrl : urls) {
      URI uri;
      try { uri = URI.create(configuredUrl); }
      catch (IllegalArgumentException exception) { continue; }
      if (!"https".equalsIgnoreCase(uri.getScheme())) continue;
      try {
        String body = client.get().uri(uri).retrieve().body(String.class);
        JsonNode root = mapper.readTree(body == null ? "[]" : body);
        JsonNode entries = root.isArray() ? root : root.path("jobs");
        if (!entries.isArray()) continue;
        for (JsonNode item : entries) {
          String jobUrl = text(item, "url", "jobUrl", "applyUrl");
          String title = text(item, "title", "name");
          if (jobUrl.isBlank() || title.isBlank() || !isHttpUrl(jobUrl)) continue;
          String sourceId = text(item, "id", "sourceId");
          if (sourceId.isBlank()) sourceId = jobUrl;
          jobs.add(new OfficialJob(sourceId, title, text(item, "company", "companyName", "employer"), jobUrl,
              text(item, "description", "content", "summary"), text(item, "updatedAt", "publishedAt"), List.of(configuredUrl, jobUrl)));
        }
      } catch (Exception ignored) {
        // A malformed or unavailable feed contributes no records. Raw provider
        // responses are intentionally not logged or persisted.
      }
    }
    return jobs;
  }

  private static String text(JsonNode item, String... fields) {
    for (String field : fields) if (item.hasNonNull(field) && item.get(field).isValueNode()) return item.get(field).asText("").trim();
    return "";
  }
  private static boolean isHttpUrl(String value) {
    try { String scheme = URI.create(value).getScheme(); return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme); }
    catch (IllegalArgumentException exception) { return false; }
  }
}
