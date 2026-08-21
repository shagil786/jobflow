package dev.jobflow.ingestion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "classification_suggestions")
public class ClassificationSuggestionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long rowId;

    @Column(nullable = false, length = 36, unique = true)
    private String suggestionId;

    @Column(nullable = false)
    private UUID connectionId;

    @Column(nullable = false, length = 120)
    private String tenantId;

    @Column(nullable = false, length = 120)
    private String userId;

    @Column(nullable = false, length = 255)
    private String messageId;

    @Column(nullable = false, length = 255)
    private String threadId;

    @Column(nullable = false, length = 64)
    private String intent;

    @Column(nullable = false, length = 32)
    private String direction;

    @Column(nullable = false)
    private double confidence;

    @Column(nullable = false)
    private boolean requiresReview;

    @Column(nullable = false, length = 128)
    private String classifierVersion;

    @Column(nullable = false, length = 128)
    private String contentHash;

    @Column
    private String companyJson;

    @Column
    private String roleJson;

    @Column
    private String applicationDateJson;

    @Column
    private String contactJson;

    @Column(nullable = false)
    private String evidenceJson;

    @Column(nullable = false)
    private String missingFieldsJson;

    @Column(nullable = false)
    private String contradictionsJson;

    @Column(nullable = false)
    private Instant createdAt;

    protected ClassificationSuggestionEntity() {}

    ClassificationSuggestionEntity(UUID connectionId, ClassificationSuggestionV1 suggestion) {
        update(connectionId, suggestion);
        createdAt = Instant.now();
    }

    void update(UUID connectionId, ClassificationSuggestionV1 suggestion) {
        suggestionId = scopedSuggestionId(connectionId, suggestion);
        this.connectionId = connectionId;
        tenantId = suggestion.tenantId();
        userId = suggestion.userId();
        messageId = suggestion.messageId();
        threadId = suggestion.threadId();
        intent = suggestion.intent().name();
        direction = suggestion.direction().name();
        confidence = suggestion.confidence();
        requiresReview = suggestion.requiresReview();
        classifierVersion = suggestion.classifierVersion();
        contentHash = suggestion.contentHash();
        companyJson = toJsonOrNull(suggestion.company());
        roleJson = toJsonOrNull(suggestion.role());
        applicationDateJson = toJsonOrNull(suggestion.applicationDate());
        contactJson = toJsonOrNull(suggestion.contact());
        evidenceJson = ClassificationSuggestionJsonCodec.write(suggestion.evidence());
        missingFieldsJson = ClassificationSuggestionJsonCodec.write(suggestion.missingFields());
        contradictionsJson = ClassificationSuggestionJsonCodec.write(suggestion.contradictions());
    }

    boolean hasSameOwner(UUID connectionId, String tenantId, String userId) {
        return Objects.equals(this.connectionId, connectionId)
                && Objects.equals(this.tenantId, tenantId)
                && Objects.equals(this.userId, userId);
    }

    ClassificationSuggestionRecord toRecord() {
        ClassificationSuggestionV1 suggestion = new ClassificationSuggestionV1(
                tenantId,
                userId,
                suggestionId,
                messageId,
                threadId,
                MessageIntent.valueOf(intent),
                MessageDirection.valueOf(direction),
                ClassificationSuggestionJsonCodec.readStringCandidate(companyJson),
                ClassificationSuggestionJsonCodec.readStringCandidate(roleJson),
                ClassificationSuggestionJsonCodec.readStringCandidate(applicationDateJson),
                ClassificationSuggestionJsonCodec.readStringCandidate(contactJson),
                confidence,
                ClassificationSuggestionJsonCodec.readEvidenceList(evidenceJson),
                ClassificationSuggestionJsonCodec.readStringList(missingFieldsJson),
                ClassificationSuggestionJsonCodec.readStringList(contradictionsJson),
                requiresReview,
                classifierVersion,
                contentHash);
        return new ClassificationSuggestionRecord(connectionId, suggestion);
    }

    private static String toJsonOrNull(Object value) {
        return value == null ? null : ClassificationSuggestionJsonCodec.write(value);
    }

    private static String scopedSuggestionId(UUID connectionId, ClassificationSuggestionV1 suggestion) {
        String scope = String.join("\u001f",
                suggestion.tenantId(),
                suggestion.userId(),
                connectionId.toString(),
                suggestion.messageId(),
                suggestion.classifierVersion(),
                suggestion.contentHash());
        return UUID.nameUUIDFromBytes(scope.getBytes(StandardCharsets.UTF_8)).toString();
    }

    Long rowId() {
        return rowId;
    }

    Instant createdAt() {
        return createdAt;
    }

    String tenantId() {
        return tenantId;
    }

    String userId() {
        return userId;
    }

    UUID connectionId() {
        return connectionId;
    }

    String messageId() {
        return messageId;
    }

    String classifierVersion() {
        return classifierVersion;
    }

    String suggestionId() {
        return suggestionId;
    }

    String contentHash() {
        return contentHash;
    }

    static ClassificationSuggestionV1 sanitize(ClassificationSuggestionV1 suggestion) {
        return new ClassificationSuggestionV1(
                suggestion.tenantId(),
                suggestion.userId(),
                suggestion.suggestionId(),
                suggestion.messageId(),
                suggestion.threadId(),
                suggestion.intent(),
                suggestion.direction(),
                sanitizeCandidate(suggestion.company()),
                sanitizeCandidate(suggestion.role()),
                sanitizeCandidate(suggestion.applicationDate()),
                sanitizeCandidate(suggestion.contact()),
                suggestion.confidence(),
                sanitizeEvidence(suggestion.evidence()),
                suggestion.missingFields(),
                suggestion.contradictions(),
                suggestion.requiresReview(),
                suggestion.classifierVersion(),
                suggestion.contentHash());
    }

    private static ExtractedFieldCandidateV1<String> sanitizeCandidate(ExtractedFieldCandidateV1<String> candidate) {
        if (candidate == null) {
            return null;
        }
        return new ExtractedFieldCandidateV1<>(
                candidate.value(),
                candidate.confidence(),
                sanitizeEvidence(candidate.evidence()),
                candidate.source(),
                candidate.requiresReview(),
                candidate.conflict());
    }

    private static List<EvidenceSpanV1> sanitizeEvidence(List<EvidenceSpanV1> evidence) {
        if (evidence == null || evidence.isEmpty()) {
            return List.of();
        }
        return evidence.stream()
                .map(span -> new EvidenceSpanV1(
                        span.tenantId(),
                        span.userId(),
                        span.evidenceId(),
                        span.messageId(),
                        span.threadId(),
                        span.source(),
                        null,
                        span.normalizedTextHash(),
                        span.sourceAvailable()))
                .toList();
    }
}
