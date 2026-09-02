package dev.jobflow.ingestion;

import java.util.List;

public record ClassificationSuggestionV1(
        String tenantId,
        String userId,
        String suggestionId,
        String messageId,
        String threadId,
        MessageIntent intent,
        MessageDirection direction,
        ExtractedFieldCandidateV1<String> company,
        ExtractedFieldCandidateV1<String> role,
        ExtractedFieldCandidateV1<String> applicationDate,
        ExtractedFieldCandidateV1<String> contact,
        double confidence,
        List<EvidenceSpanV1> evidence,
        List<String> missingFields,
        List<String> contradictions,
        boolean requiresReview,
        String classifierVersion,
        String contentHash,
        List<ReviewReason> reviewReasons) {
    public ClassificationSuggestionV1 {
        confidence = Math.max(0.0, Math.min(1.0, confidence));
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        missingFields = missingFields == null ? List.of() : List.copyOf(missingFields);
        contradictions = contradictions == null ? List.of() : List.copyOf(contradictions);
        reviewReasons = reviewReasons == null ? List.of() : List.copyOf(reviewReasons);
        company = normalizeCandidate(company);
        role = normalizeCandidate(role);
        applicationDate = normalizeCandidate(applicationDate);
        contact = normalizeCandidate(contact);
    }

    public ClassificationSuggestionV1(
            String tenantId, String userId, String suggestionId, String messageId, String threadId,
            MessageIntent intent, MessageDirection direction,
            ExtractedFieldCandidateV1<String> company, ExtractedFieldCandidateV1<String> role,
            ExtractedFieldCandidateV1<String> applicationDate, ExtractedFieldCandidateV1<String> contact,
            double confidence, List<EvidenceSpanV1> evidence, List<String> missingFields,
            List<String> contradictions, boolean requiresReview, String classifierVersion, String contentHash) {
        this(tenantId, userId, suggestionId, messageId, threadId, intent, direction, company, role,
                applicationDate, contact, confidence, evidence, missingFields, contradictions,
                requiresReview, classifierVersion, contentHash, List.of());
    }

    private static <T> ExtractedFieldCandidateV1<T> normalizeCandidate(ExtractedFieldCandidateV1<T> candidate) {
        if (candidate == null || candidate.value() == null) {
            return null;
        }
        return candidate;
    }
}
