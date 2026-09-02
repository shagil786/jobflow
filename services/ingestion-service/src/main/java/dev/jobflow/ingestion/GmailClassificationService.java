package dev.jobflow.ingestion;

import java.util.Objects;
import java.util.UUID;
import java.util.Optional;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GmailClassificationService {
    private static final Pattern HIGH_PRECISION_REJECTION = Pattern.compile(
            "\\b(?:we will not be moving forward|will not be moving forward|not moving forward with your application|moving forward with another applicant|moving forward with other candidates|decided not to proceed with your application|no longer under consideration|not selected for this position|regret to inform you)\\b",
            Pattern.CASE_INSENSITIVE);
    private final GmailEvidenceService evidenceService;
    private final MessageIntentClassifier classifier;
    private final ClassificationSuggestionService suggestions;
    private final Optional<ContextIndexClient> contextIndex;

    public GmailClassificationService(
            GmailEvidenceService evidenceService,
            MessageIntentClassifier classifier,
            ClassificationSuggestionService suggestions,
            Optional<ContextIndexClient> contextIndex) {
        this.evidenceService = evidenceService;
        this.classifier = classifier;
        this.suggestions = suggestions;
        this.contextIndex = contextIndex;
    }

    @Transactional
    public ClassificationSuggestionRecord classify(UUID connectionId, String messageId) {
        Objects.requireNonNull(connectionId, "connectionId");
        requireValue(messageId, "messageId");

        GmailEvidenceService.PreparedMessage prepared = evidenceService.prepareMessage(connectionId, messageId);
        contextIndex.ifPresent(client -> client.index(prepared, connectionId));
        ClassificationSuggestionRecord suggestion = classifier.classify(prepared.message(), prepared.evidence());
        ClassificationSuggestionV1 canonical = canonicalizeRejection(suggestion.suggestion(), prepared.message());
        ClassificationSuggestionV1 promoted = ClassificationPromotionPolicy.apply(canonical);
        return suggestions.saveIfAbsent(new ClassificationSuggestionRecord(suggestion.connectionId(), promoted));
    }

    private static ClassificationSuggestionV1 canonicalizeRejection(ClassificationSuggestionV1 suggestion, SafeGmailMessage message) {
        boolean inbound = suggestion.direction() == MessageDirection.INBOUND;
        boolean clearLanguage = message.normalizedContent() != null && HIGH_PRECISION_REJECTION.matcher(message.normalizedContent().toLowerCase(Locale.ROOT)).find();
        boolean groundedCore = suggestion.company() != null && suggestion.company().value() != null
                && suggestion.role() != null && suggestion.role().value() != null && !suggestion.evidence().isEmpty();
        if (!inbound || !clearLanguage || !groundedCore || suggestion.intent() == MessageIntent.REJECTION) return suggestion;
        return new ClassificationSuggestionV1(suggestion.tenantId(), suggestion.userId(), suggestion.suggestionId(), suggestion.messageId(), suggestion.threadId(),
                MessageIntent.REJECTION, suggestion.direction(), suggestion.company(), suggestion.role(), suggestion.applicationDate(), suggestion.contact(),
                Math.max(0.90, suggestion.confidence()), suggestion.evidence(), suggestion.missingFields(), suggestion.contradictions(), suggestion.requiresReview(),
                suggestion.classifierVersion() + ":rejection-gate", suggestion.contentHash(), suggestion.reviewReasons());
    }

    boolean contextIndexEnabled() {
        return contextIndex.isPresent();
    }

    private static void requireValue(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be null or blank");
        }
    }
}
