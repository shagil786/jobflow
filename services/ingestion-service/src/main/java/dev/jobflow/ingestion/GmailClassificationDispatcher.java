package dev.jobflow.ingestion;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
class GmailClassificationDispatcher {
    private static final Logger log = LoggerFactory.getLogger(GmailClassificationDispatcher.class);
    private final GmailClassificationService classifier;
    private final GmailBackfillBatchRepository batches;
    private final ApplicationPromotionOutboxService promotions;

    GmailClassificationDispatcher(GmailClassificationService classifier, GmailBackfillBatchRepository batches, ApplicationPromotionOutboxService promotions) {
        this.classifier = classifier; this.batches = batches; this.promotions = promotions;
    }

    // The SQS backfill worker already runs the scan in the background. Keeping
    // this stage synchronous makes batch completion mean that its counters are
    // final, instead of allowing classification to mutate a completed run.
    public void dispatch(UUID connectionId, String messageId, UUID batchId) {
        try {
            ClassificationSuggestionRecord result = classifier.classify(connectionId, messageId);
            ClassificationSuggestionV1 suggestion = result.suggestion();
            // Unrelated messages are intentionally stored as classification evidence, but
            // they must never enter the application promotion outbox. The outbox payload
            // requires core application fields, while unrelated signals may legitimately
            // have no role or company.
            if (isAutoPromotable(suggestion) || isTerminalRejection(suggestion)) promotions.enqueue(suggestion, batchId);
            int updated = batches.recordClassificationProgress(batchId, 1, classifier.contextIndexEnabled() ? 1 : 0, 1,
                    0, suggestion.requiresReview() ? 1 : 0, java.time.Instant.now());
            if (updated != 1) log.warn("Gmail classification progress batch not found batch={}", batchId);
        } catch (RuntimeException exception) {
            // Import completion must not depend on optional enrichment. The message and
            // candidate are already persisted and remain available for retry/review.
            log.warn("Gmail classification deferred connection={} message={} reason={}",
                    connectionId, messageId, exception.getClass().getSimpleName(), exception);
        }
    }

    private static boolean isAutoPromotable(ClassificationSuggestionV1 suggestion) {
        return !suggestion.requiresReview()
                && suggestion.intent() != MessageIntent.UNRELATED
                && hasValue(suggestion.company())
                && hasValue(suggestion.role());
    }

    private static boolean isTerminalRejection(ClassificationSuggestionV1 suggestion) {
        return suggestion.intent() == MessageIntent.REJECTION
                && !suggestion.requiresReview()
                && suggestion.contradictions().isEmpty()
                && suggestion.confidence() >= 0.85
                && hasValue(suggestion.company())
                && hasValue(suggestion.role());
    }

    private static boolean hasValue(ExtractedFieldCandidateV1<String> candidate) {
        return candidate != null && candidate.value() != null && !candidate.value().isBlank();
    }
}
