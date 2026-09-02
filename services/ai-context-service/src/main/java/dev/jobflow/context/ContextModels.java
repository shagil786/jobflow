package dev.jobflow.context;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ContextModels {
    private ContextModels() {}

    public enum Purpose { CLASSIFICATION, RANKING, DRAFT, FOLLOW_UP }
    public enum ReviewState { SUPPORTED, NEEDS_REVIEW, UNKNOWN }

    public record EvidenceDocumentRequest(
            String tenantId, String userId, String correlationId, String sourceType, String sourceId,
            String threadId, String applicationId, String resumeVersionId, Instant occurredAt,
            String text, String retentionPolicy) {
        public EvidenceDocumentRequest {
            require(tenantId, "tenantId"); require(userId, "userId"); require(correlationId, "correlationId");
            require(sourceType, "sourceType"); require(sourceId, "sourceId"); require(text, "text");
            retentionPolicy = retentionPolicy == null || retentionPolicy.isBlank() ? "DERIVED_DEFAULT" : retentionPolicy;
        }
    }

    public record EvidenceDocumentResponse(UUID documentId, int chunks, String contentHash, String indexVersion) {}

    public record ContextQuery(
            String tenantId, String userId, String correlationId, Purpose purpose, String query,
            List<String> sourceTypes, String threadId, String applicationId, String resumeVersionId,
            int limit, int maxTokens) {
        public ContextQuery {
            require(tenantId, "tenantId"); require(userId, "userId"); require(correlationId, "correlationId"); require(query, "query");
            purpose = purpose == null ? Purpose.CLASSIFICATION : purpose;
            sourceTypes = sourceTypes == null ? List.of() : List.copyOf(sourceTypes);
            limit = Math.max(1, Math.min(limit <= 0 ? 12 : limit, 50));
            maxTokens = Math.max(256, Math.min(maxTokens <= 0 ? 6000 : maxTokens, 16000));
        }
    }

    public record RetrievedEvidence(UUID chunkId, String sourceType, String sourceId, String threadId,
                                    String section, String text, double score, String citation) {}

    public record ContextBundle(ContextQuery query, List<RetrievedEvidence> evidence,
                                int estimatedTokens, ReviewState state, List<String> warnings) {}

    public record VerificationResult(ReviewState state, List<String> unsupportedClaims,
                                     List<String> invalidEvidence, Map<String, String> metadata) {}

    static void require(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
    }
}
