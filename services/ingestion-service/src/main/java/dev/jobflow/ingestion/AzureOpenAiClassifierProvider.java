package dev.jobflow.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.util.UriComponentsBuilder;

/** Azure OpenAI structured extraction provider. All model output remains review-only. */
@Component
@ConditionalOnProperty(name = "JOBFLOW_CLASSIFIER_PROVIDER", havingValue = "azure-openai")
class AzureOpenAiClassifierProvider implements ClassifierProvider {
    static final String NAME = "azure-openai";
    static final String CLASSIFIER_VERSION = "azure-openai-structured-v1";
    private static final int MAX_INPUT_CHARS = 12_000;
    private static final int MAX_EVIDENCE_SPANS = 12;

    private static final String SYSTEM_PROMPT = """
            You classify one Gmail message for a private job-search tracker.
            Return only the requested JSON schema. Never infer or invent a value.
            A field is null unless the exact value is supported by a quote in the message.
            Treat quoted replies and signatures as evidence only when the quote clearly supports the field.
            jobRelated is false for newsletters, marketing, receipts, and unrelated mail.
            """;

    private final RestClient client;
    private final ObjectMapper mapper;
    private final AzureOpenAiSecretResolver secrets;
    private final String endpoint;
    private final String deployment;
    private final String apiVersion;

    @Autowired
    AzureOpenAiClassifierProvider(
            AzureOpenAiSecretResolver secrets,
            ObjectMapper mapper,
            @Value("${AZURE_OPENAI_ENDPOINT:}") String endpoint,
            @Value("${AZURE_OPENAI_DEPLOYMENT:}") String deployment,
            @Value("${AZURE_OPENAI_API_VERSION:}") String apiVersion) {
        this(timeoutConfiguredClient(), secrets, mapper, endpoint, deployment, apiVersion);
    }

    AzureOpenAiClassifierProvider(
            RestClient client,
            AzureOpenAiSecretResolver secrets,
            ObjectMapper mapper,
            String endpoint,
            String deployment,
            String apiVersion) {
        this.client = client;
        this.secrets = secrets;
        this.mapper = mapper;
        this.endpoint = normalizeEndpoint(endpoint);
        this.deployment = requireConfig(deployment, "AZURE_OPENAI_DEPLOYMENT");
        this.apiVersion = apiVersion == null ? "" : apiVersion.trim();
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public ClassificationSuggestionV1 classify(ClassificationInput input) {
        String messageText = promptText(input.message());
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", deployment);
        request.put("messages", List.of(
                Map.of("role", "system", "content", SYSTEM_PROMPT),
                Map.of("role", "user", "content", messageText)));
        request.put("response_format", Map.of(
                "type", "json_schema",
                "json_schema", Map.of(
                        "name", "jobflow_email_classification",
                        "strict", true,
                        "schema", schema())));

        String responseBody;
        try {
            RestClient.RequestBodySpec requestSpec = client.post()
                    .uri(requestUri())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("api-key", secrets.resolve())
                    .body(request);
            responseBody = requestSpec.retrieve().body(String.class);
        } catch (RestClientResponseException exception) {
            throw new ClassifierProviderException(
                    "Azure OpenAI classification request failed with status " + exception.getStatusCode().value(), exception);
        } catch (RestClientException exception) {
            throw new ClassifierProviderException("Azure OpenAI classification request failed", exception);
        }

        return toSuggestion(input, parseResponse(responseBody));
    }

    private java.net.URI requestUri() {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(endpoint + "/chat/completions");
        if (!apiVersion.isBlank()) {
            builder.queryParam("api-version", apiVersion);
        }
        return builder.build().encode().toUri();
    }

    private ClassificationSuggestionV1 toSuggestion(ClassificationInput input, JsonNode result) {
        SafeGmailMessage message = input.message();
        GmailEvidenceService.PreparedEvidence evidence = input.evidence();
        List<EvidenceSpanV1> spans = new ArrayList<>();
        List<String> contradictions = new ArrayList<>();
        Map<String, ExtractedFieldCandidateV1<String>> fields = new LinkedHashMap<>();

        for (String field : List.of("company", "role", "applicationDate", "contact")) {
            JsonNode fieldNode = result.path(field);
            String value = textOrNull(fieldNode.path("value"));
            String quote = textOrNull(fieldNode.path("quote"));
            if (value == null) {
                fields.put(field, null);
                continue;
            }
            EvidenceSpanV1 span = verifiedSpan(message, evidence, field, quote);
            if (span == null) {
                contradictions.add(field + " value has no verifiable evidence");
                fields.put(field, null);
                continue;
            }
            spans.add(span);
            fields.put(field, new ExtractedFieldCandidateV1<>(value, modelConfidence(result), List.of(span), span.source(), true, false));
        }

        List<JsonNode> modelEvidence = result.path("evidence").isArray()
                ? iterable(result.path("evidence"))
                : List.of();
        for (JsonNode node : modelEvidence) {
            if (spans.size() >= MAX_EVIDENCE_SPANS) {
                break;
            }
            String quote = textOrNull(node.path("quote"));
            String field = textOrNull(node.path("field"));
            EvidenceSpanV1 span = verifiedSpan(message, evidence, field == null ? "message" : field, quote);
            if (span != null && spans.stream().noneMatch(existing -> existing.evidenceId().equals(span.evidenceId()))) {
                spans.add(span);
            }
        }

        double confidence = modelConfidence(result);
        if (spans.isEmpty()) {
            confidence = Math.min(confidence, 0.25);
            contradictions.add("model returned no verifiable evidence");
        } else {
            confidence = Math.min(1.0, confidence * (0.6 + (0.4 * Math.min(1.0, spans.size() / 3.0))));
        }

        MessageIntent intent = parseIntent(result.path("intent"), result.path("jobRelated"));
        String contentHash = firstNonBlank(evidence.contentHash(), message.normalizedContentHash(), EmailNormalizer.sha256(""));
        List<String> missing = new ArrayList<>();
        if (fields.get("company") == null) missing.add("company");
        if (fields.get("role") == null) missing.add("role");
        if (fields.get("applicationDate") == null) missing.add("applicationDate");
        if (fields.get("contact") == null) missing.add("contact");

        ClassificationSuggestionV1 suggestion = new ClassificationSuggestionV1(
                evidence.tenantId(),
                evidence.userId(),
                UUID.nameUUIDFromBytes((message.messageId() + CLASSIFIER_VERSION + contentHash).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString(),
                message.messageId(),
                message.threadId(),
                intent,
                directionFor(message),
                fields.get("company"),
                fields.get("role"),
                fields.get("applicationDate"),
                fields.get("contact"),
                confidence,
                List.copyOf(spans),
                List.copyOf(missing),
                List.copyOf(contradictions),
                true,
                CLASSIFIER_VERSION,
                contentHash);
        return suggestion;
    }

    private EvidenceSpanV1 verifiedSpan(
            SafeGmailMessage message,
            GmailEvidenceService.PreparedEvidence evidence,
            String field,
            String quote) {
        if (quote == null || quote.isBlank()) {
            return null;
        }
        String normalizedQuote = normalizeForSearch(quote);
        String subject = normalizeForSearch(message.subject());
        String body = normalizeForSearch(message.normalizedContent());
        String source;
        if (subject.contains(normalizedQuote)) {
            source = "subject";
        } else if (body.contains(normalizedQuote)) {
            source = "body";
        } else {
            return null;
        }
        String compactQuote = quote.trim().replaceAll("\\s+", " ");
        String evidenceId = message.messageId() + ":azure:" + field + ":" + EmailNormalizer.sha256(compactQuote).substring(0, 12);
        return new EvidenceSpanV1(
                evidence.tenantId(), evidence.userId(), evidenceId, message.messageId(), message.threadId(), source,
                compactQuote.length() > 160 ? compactQuote.substring(0, 157) + "..." : compactQuote,
                firstNonBlank(evidence.contentHash(), message.normalizedContentHash(), ""), true);
    }

    private JsonNode parseResponse(String responseBody) {
        try {
            JsonNode root = mapper.readTree(responseBody == null ? "" : responseBody);
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            if (content.isBlank()) {
                throw new ClassifierProviderException("Azure OpenAI returned no structured classification");
            }
            JsonNode result = mapper.readTree(content);
            if (!result.isObject()) {
                throw new ClassifierProviderException("Azure OpenAI returned an invalid classification shape");
            }
            return result;
        } catch (ClassifierProviderException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ClassifierProviderException("Azure OpenAI returned invalid classification JSON", exception);
        }
    }

    private static Map<String, Object> schema() {
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("type", List.of("object", "null"));
        field.put("additionalProperties", false);
        field.put("properties", Map.of("value", Map.of("type", List.of("string", "null")), "quote", Map.of("type", List.of("string", "null"))));
        field.put("required", List.of("value", "quote"));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.put("properties", Map.of(
                "jobRelated", Map.of("type", "boolean"),
                "intent", Map.of("type", "string", "enum", List.of("APPLICATION_CONFIRMATION", "RECRUITER_OUTREACH", "RECRUITER_REPLY", "INTERVIEW_INVITATION", "INTERVIEW_RESCHEDULE", "INTERVIEW_FEEDBACK", "ASSESSMENT_INVITATION", "REJECTION", "OFFER", "WITHDRAWAL", "FOLLOW_UP_REQUEST", "EMPLOYER_UPDATE", "UNRELATED", "UNKNOWN")),
                "confidence", Map.of("type", "number", "minimum", 0, "maximum", 1),
                "company", field, "role", field, "applicationDate", field, "contact", field,
                "evidence", Map.of("type", "array", "maxItems", MAX_EVIDENCE_SPANS, "items", Map.of("type", "object", "additionalProperties", false, "properties", Map.of("field", Map.of("type", "string"), "quote", Map.of("type", "string")), "required", List.of("field", "quote"))),
                "contradictions", Map.of("type", "array", "maxItems", 8, "items", Map.of("type", "string"))));
        schema.put("required", List.of("jobRelated", "intent", "confidence", "company", "role", "applicationDate", "contact", "evidence", "contradictions"));
        return schema;
    }

    private static String promptText(SafeGmailMessage message) {
        String text = "Subject: " + safe(message.subject()) + "\n"
                + "From: " + safe(message.sender()) + "\n"
                + "Reply-To: " + safe(message.replyTo()) + "\n"
                + "Received: " + (message.receivedAt() == null ? "" : message.receivedAt()) + "\n\n"
                + "Message:\n" + safe(message.normalizedContent());
        return text.length() <= MAX_INPUT_CHARS ? text : text.substring(0, MAX_INPUT_CHARS);
    }

    private static RestClient timeoutConfiguredClient() {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build());
        factory.setReadTimeout(Duration.ofSeconds(45));
        return RestClient.builder().requestFactory(factory).build();
    }

    private static String normalizeEndpoint(String value) {
        String endpoint = requireConfig(value, "AZURE_OPENAI_ENDPOINT");
        return endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
    }

    private static String requireConfig(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new ClassifierProviderException(name + " is required for Azure OpenAI classification");
        }
        return value.trim();
    }

    private static double modelConfidence(JsonNode result) {
        double value = result.path("confidence").asDouble(0.0);
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static MessageIntent parseIntent(JsonNode intentNode, JsonNode jobRelatedNode) {
        String raw = intentNode.asText("UNKNOWN").trim().toUpperCase(Locale.ROOT);
        try {
            MessageIntent intent = MessageIntent.valueOf(raw);
            return !jobRelatedNode.asBoolean(true) && intent == MessageIntent.UNKNOWN ? MessageIntent.UNRELATED : intent;
        } catch (IllegalArgumentException exception) {
            return MessageIntent.UNKNOWN;
        }
    }

    private static MessageDirection directionFor(SafeGmailMessage message) {
        if (message.labelIds().stream().map(value -> value.toUpperCase(Locale.ROOT)).anyMatch("SENT"::equals)) {
            return MessageDirection.OUTBOUND;
        }
        if (message.sender() == null || message.sender().isBlank()) {
            return MessageDirection.UNKNOWN;
        }
        return MessageDirection.INBOUND;
    }

    private static List<JsonNode> iterable(JsonNode node) {
        List<JsonNode> result = new ArrayList<>();
        node.forEach(result::add);
        return result;
    }

    private static String normalizeForSearch(String value) {
        return safe(value).replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    private static String textOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull() || !node.isTextual() || node.asText().isBlank()) {
            return null;
        }
        return node.asText().trim();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String firstNonBlank(String first, String second, String fallback) {
        if (first != null && !first.isBlank()) return first;
        if (second != null && !second.isBlank()) return second;
        return fallback;
    }
}
