package dev.jobflow.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ClassificationPromotionPolicyTest {
    private static final EvidenceSpanV1 EVIDENCE = new EvidenceSpanV1(
            "tenant", "user", "evidence", "message", "thread", "body", "supported text", "hash", true);

    @Test
    void promotesGroundedHighConfidenceCoreFields() {
        ClassificationSuggestionV1 result = ClassificationPromotionPolicy.apply(suggestion(
                MessageIntent.APPLICATION_CONFIRMATION,
                new ExtractedFieldCandidateV1<>("Acme", .98, List.of(EVIDENCE), "body", false, false),
                new ExtractedFieldCandidateV1<>("Frontend Engineer", .98, List.of(EVIDENCE), "body", false, false),
                .94, List.of(EVIDENCE), List.of()));

        assertThat(result.requiresReview()).isFalse();
        assertThat(result.reviewReasons()).isEmpty();
    }

    @Test
    void routesOfferToReviewEvenWhenGrounded() {
        ClassificationSuggestionV1 result = ClassificationPromotionPolicy.apply(suggestion(
                MessageIntent.OFFER,
                new ExtractedFieldCandidateV1<>("Acme", .99, List.of(EVIDENCE), "body", false, false),
                new ExtractedFieldCandidateV1<>("Frontend Engineer", .99, List.of(EVIDENCE), "body", false, false),
                .99, List.of(EVIDENCE), List.of()));

        assertThat(result.requiresReview()).isTrue();
        assertThat(result.reviewReasons()).containsExactly(ReviewReason.OFFER_STATUS);
    }

    @Test
    void recordsReasonsForMissingAndContradictoryEvidence() {
        ClassificationSuggestionV1 result = ClassificationPromotionPolicy.apply(suggestion(
                MessageIntent.RECRUITER_REPLY, null, null, .42, List.of(), List.of("role")));

        assertThat(result.requiresReview()).isTrue();
        assertThat(result.reviewReasons()).containsExactlyInAnyOrder(
                ReviewReason.MISSING_CORE_FIELD, ReviewReason.CONFLICTING_EVIDENCE,
                ReviewReason.INVALID_CITATION, ReviewReason.LOW_CONFIDENCE);
    }

    private static ClassificationSuggestionV1 suggestion(MessageIntent intent,
            ExtractedFieldCandidateV1<String> company, ExtractedFieldCandidateV1<String> role,
            double confidence, List<EvidenceSpanV1> evidence, List<String> contradictions) {
        return new ClassificationSuggestionV1("tenant", "user", "suggestion", "message", "thread", intent,
                MessageDirection.INBOUND, company, role, null, null, confidence, evidence, List.of(),
                contradictions, true, "test", "content");
    }
}
