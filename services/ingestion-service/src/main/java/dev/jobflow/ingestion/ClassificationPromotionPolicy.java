package dev.jobflow.ingestion;

import java.util.List;

/** Applies the conservative promotion policy after provider output has been verified. */
final class ClassificationPromotionPolicy {
    private static final double AUTO_PROMOTE_THRESHOLD = 0.85;

    private ClassificationPromotionPolicy() {}

    static ClassificationSuggestionV1 apply(ClassificationSuggestionV1 suggestion) {
        boolean offer = suggestion.intent() == MessageIntent.OFFER;
        boolean coreFieldsPresent = hasValue(suggestion.company()) && hasValue(suggestion.role());
        boolean grounded = !suggestion.evidence().isEmpty() && suggestion.contradictions().isEmpty();
        boolean highConfidence = suggestion.confidence() >= AUTO_PROMOTE_THRESHOLD;
        boolean unrelated = suggestion.intent() == MessageIntent.UNRELATED;
        java.util.List<ReviewReason> reasons = new java.util.ArrayList<>();
        if (offer) reasons.add(ReviewReason.OFFER_STATUS);
        if (!coreFieldsPresent && !unrelated) reasons.add(ReviewReason.MISSING_CORE_FIELD);
        if (!suggestion.contradictions().isEmpty()) reasons.add(ReviewReason.CONFLICTING_EVIDENCE);
        if (suggestion.evidence().isEmpty() && !unrelated) reasons.add(ReviewReason.INVALID_CITATION);
        if (!highConfidence && !unrelated) reasons.add(ReviewReason.LOW_CONFIDENCE);
        boolean review = !unrelated && !(coreFieldsPresent && grounded && highConfidence) || offer;
        return withReviewState(suggestion, review, reasons);
    }

    private static boolean hasValue(ExtractedFieldCandidateV1<String> candidate) {
        return candidate != null && candidate.value() != null && !candidate.value().isBlank();
    }

    private static ClassificationSuggestionV1 withReviewState(ClassificationSuggestionV1 value, boolean requiresReview, java.util.List<ReviewReason> reasons) {
        return new ClassificationSuggestionV1(value.tenantId(), value.userId(), value.suggestionId(), value.messageId(), value.threadId(),
            value.intent(), value.direction(), value.company(), value.role(), value.applicationDate(), value.contact(), value.confidence(),
            List.copyOf(value.evidence()), List.copyOf(value.missingFields()), List.copyOf(value.contradictions()), requiresReview,
            value.classifierVersion(), value.contentHash(), List.copyOf(reasons));
    }
}
