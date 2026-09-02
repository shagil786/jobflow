package dev.jobflow.ingestion;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.http.client.JdkClientHttpRequestFactory;

@Component
@ConditionalOnProperty(name = "JOBFLOW_CONTEXT_INDEX_ENABLED", havingValue = "true")
class ContextIndexClient {
    private final RestClient client;
    private final String baseUrl;
    private final String internalKey;

    ContextIndexClient(@Value("${AI_CONTEXT_SERVICE_URL:http://localhost:8084}") String baseUrl,
                       @Value("${JOBFLOW_INTERNAL_SERVICE_KEY:}") String internalKey) {
        this.client = RestClient.builder().requestFactory(new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build())).build();
        this.baseUrl = baseUrl.replaceAll("/$", ""); this.internalKey = internalKey;
    }

    void index(GmailEvidenceService.PreparedMessage prepared, UUID connectionId) {
        SafeGmailMessage message = prepared.message();
        GmailEvidenceService.PreparedEvidence evidence = prepared.evidence();
        String text = "Subject: " + value(message.subject()) + "\nSender: " + value(message.sender()) +
                "\n" + value(message.normalizedContent());
        Map<String, Object> request = Map.of(
                "tenantId", evidence.tenantId(), "userId", evidence.userId(), "correlationId", "gmail:" + connectionId + ":" + message.messageId(),
                "sourceType", "gmail_message", "sourceId", message.messageId(), "threadId", message.threadId(),
                "occurredAt", message.receivedAt() == null ? Instant.now().toString() : message.receivedAt().toString(),
                "text", text, "retentionPolicy", "DERIVED_EMAIL_EVIDENCE");
        try {
            client.post().uri(URI.create(baseUrl + "/internal/v1/evidence")).contentType(MediaType.APPLICATION_JSON)
                    .header("X-Internal-Service-Key", internalKey).body(request).retrieve().toBodilessEntity();
        } catch (RestClientException exception) {
            throw new ClassifierProviderException("context indexing failed", exception);
        }
    }

    private static String value(String value) { return value == null ? "" : value; }
}
