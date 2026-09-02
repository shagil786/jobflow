package dev.jobflow.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.http.client.JdkClientHttpRequestFactory;

/** Classification adapter that uses the shared context service instead of calling Azure directly. */
@Component
@ConditionalOnProperty(name = "JOBFLOW_CLASSIFIER_PROVIDER", havingValue = "context-azure")
class ContextClassifierProvider implements ClassifierProvider {
    private static final String VERSION = "context-azure-grounded-v1";
    private final RestClient client; private final ObjectMapper mapper; private final String url; private final String key;

    ContextClassifierProvider(ObjectMapper mapper,
            @Value("${AI_CONTEXT_SERVICE_URL:http://localhost:8084}") String url,
            @Value("${JOBFLOW_INTERNAL_SERVICE_KEY:}") String key) {
        this.client = RestClient.builder().requestFactory(new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build())).build();
        this.mapper = mapper; this.url = url.replaceAll("/$", ""); this.key = key;
    }

    @Override public String name() { return "context-azure"; }

    @Override public ClassificationSuggestionV1 classify(ClassificationInput input) {
        SafeGmailMessage message = input.message(); GmailEvidenceService.PreparedEvidence evidence = input.evidence();
        Map<String,Object> query = new LinkedHashMap<>();
        query.put("tenantId", evidence.tenantId()); query.put("userId", evidence.userId()); query.put("correlationId", "classification:" + message.messageId());
        query.put("purpose", "CLASSIFICATION"); query.put("query", promptText(message)); query.put("sourceTypes", List.of("gmail_message"));
        query.put("threadId", message.threadId()); query.put("limit", 12); query.put("maxTokens", 5000);
        JsonNode context = post("/internal/v1/context/query", query);
        Map<String,Object> generation = new LinkedHashMap<>(); generation.put("tenantId", evidence.tenantId()); generation.put("userId", evidence.userId());
        generation.put("correlationId", "classification:" + message.messageId()); generation.put("purpose", "CLASSIFICATION");
        generation.put("instruction", "Return JSON with jobRelated boolean, intent enum, confidence 0..1, and company, role, applicationDate, contact objects with value and quote or null. Include only values directly supported by the evidence citations.");
        generation.put("context", context); generation.put("schema", Map.of());
        JsonNode response = post("/internal/v1/generation", generation);
        try { return toSuggestion(message, evidence, context, mapper.readTree(response.path("content").asText(""))); }
        catch (Exception exception) { throw new ClassifierProviderException("shared context classifier returned invalid JSON", exception); }
    }

    private ClassificationSuggestionV1 toSuggestion(SafeGmailMessage message, GmailEvidenceService.PreparedEvidence evidence,
                                                     JsonNode context, JsonNode result) {
        List<EvidenceSpanV1> spans = new ArrayList<>(); Map<String, ExtractedFieldCandidateV1<String>> fields = new LinkedHashMap<>(); List<String> contradictions = new ArrayList<>();
        for (String field : List.of("company", "role", "applicationDate", "contact")) {
            JsonNode node = result.path(field); String value = text(node.path("value")); String quote = text(node.path("quote"));
            EvidenceSpanV1 span = value == null ? null : verifiedSpan(evidence, message, context, quote, field);
            if (value != null && span == null) { contradictions.add(field + " has no supporting context citation"); fields.put(field, null); }
            else if (span != null) { spans.add(span); fields.put(field, new ExtractedFieldCandidateV1<>(value, result.path("confidence").asDouble(0.5), List.of(span), "ai", true, false)); }
            else fields.put(field, null);
        }
        List<String> missing = fields.entrySet().stream().filter(entry -> entry.getValue() == null).map(Map.Entry::getKey).toList();
        double confidence = result.path("confidence").asDouble(0.0);
        if (spans.isEmpty()) { confidence = Math.min(confidence, 0.25); contradictions.add("no verifiable evidence returned"); }
        MessageIntent intent = parseIntent(result.path("intent").asText("UNKNOWN"), result.path("jobRelated").asBoolean(false));
        return new ClassificationSuggestionV1(evidence.tenantId(), evidence.userId(), UUID.nameUUIDFromBytes((message.messageId()+VERSION+evidence.contentHash()).getBytes()).toString(),
                message.messageId(), message.threadId(), intent, direction(message), fields.get("company"), fields.get("role"), fields.get("applicationDate"), fields.get("contact"),
                confidence, spans, missing, contradictions, true, VERSION, evidence.contentHash());
    }

    private EvidenceSpanV1 verifiedSpan(GmailEvidenceService.PreparedEvidence evidence, SafeGmailMessage message, JsonNode context, String quote, String field) {
        if (quote == null || quote.isBlank()) return null; String normalized = quote.trim().replaceAll("\\s+", " ").toLowerCase();
        for (JsonNode item : context.path("evidence")) if (item.path("text").asText("").replaceAll("\\s+", " ").toLowerCase().contains(normalized))
            return new EvidenceSpanV1(evidence.tenantId(), evidence.userId(), message.messageId()+":context:"+field+":"+EmailNormalizer.sha256(quote).substring(0,12), message.messageId(), message.threadId(), "body", quote, evidence.contentHash(), true);
        return null;
    }

    private JsonNode post(String path, Object body) {
        try { String value = client.post().uri(url+path).contentType(MediaType.APPLICATION_JSON).header("X-Internal-Service-Key", key).body(body).retrieve().body(String.class); return mapper.readTree(value); }
        catch (RestClientException | java.io.IOException exception) { throw new ClassifierProviderException("shared context service unavailable", exception); }
    }
    private static String promptText(SafeGmailMessage message) { return (message.subject()==null?"":message.subject())+"\n"+(message.normalizedContent()==null?"":message.normalizedContent()); }
    private static String text(JsonNode node) { return node == null || node.isNull() || node.asText("").isBlank() ? null : node.asText().trim(); }
    private static MessageDirection direction(SafeGmailMessage message) { return message.sender()!=null && message.sender().contains("@") ? MessageDirection.INBOUND : MessageDirection.UNKNOWN; }
    private static MessageIntent parseIntent(String value, boolean jobRelated) { try { return jobRelated ? MessageIntent.valueOf(value.toUpperCase()) : MessageIntent.UNRELATED; } catch (IllegalArgumentException exception) { return MessageIntent.UNKNOWN; } }
}
