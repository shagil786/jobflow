package dev.jobflow.ingestion;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.http.client.JdkClientHttpRequestFactory;

@Component
class JobPromotionClient {
  private final RestClient client;
  private final String baseUrl;
  private final String key;

  JobPromotionClient(@Value("${JOB_SERVICE_URL:http://localhost:8091}") String baseUrl,
      @Value("${JOBFLOW_INTERNAL_SERVICE_KEY:}") String key) {
    this.client = RestClient.builder().requestFactory(new JdkClientHttpRequestFactory(
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build())).build();
    this.baseUrl = baseUrl.replaceAll("/$", ""); this.key = key;
  }

  void promote(ClassificationSuggestionV1 suggestion) {
    promote(suggestion.tenantId(), suggestion.userId(), suggestion.suggestionId(), suggestion.messageId(),
        suggestion.threadId(), suggestion.direction() == null ? null : suggestion.direction().name(),
        value(suggestion.company()), value(suggestion.role()), suggestion.intent().name(),
        suggestion.applicationDate() == null ? null : suggestion.applicationDate().value());
  }

  void promote(String tenantId, String userId, String suggestionId, String messageId, String threadId, String direction, String company,
      String role, String intent, String applicationDate) {
    Map<String, Object> body = new HashMap<>();
    body.put("tenantId", tenantId); body.put("userId", userId);
    body.put("suggestionId", suggestionId); body.put("messageId", messageId);
    body.put("threadId", threadId); body.put("direction", direction);
    body.put("company", company); body.put("role", role); body.put("intent", intent);
    LocalDate date = date(applicationDate); if (date != null) body.put("applicationDate", date);
    try {
      client.post().uri(URI.create(baseUrl + "/internal/v1/applications/auto-promote"))
          .contentType(MediaType.APPLICATION_JSON).header("X-Internal-Service-Key", key)
          .body(body).retrieve().toBodilessEntity();
    } catch (RestClientException exception) {
      throw new ClassifierProviderException("application auto-promotion failed", exception);
    }
  }

  private static String value(ExtractedFieldCandidateV1<String> candidate) { return candidate == null ? "" : candidate.value(); }
  private static LocalDate date(ExtractedFieldCandidateV1<String> candidate) {
    if (candidate == null || candidate.value() == null) return null;
    try { return LocalDate.parse(candidate.value()); } catch (RuntimeException ignored) { return null; }
  }
  private static LocalDate date(String value) {
    if (value == null || value.isBlank()) return null;
    try { return LocalDate.parse(value); } catch (RuntimeException ignored) { return null; }
  }
}
