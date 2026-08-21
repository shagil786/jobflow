package dev.jobflow.ingestion;

import java.util.List;

public record ExtractedFieldCandidateV1<T>(
        T value,
        double confidence,
        List<EvidenceSpanV1> evidence,
        String source,
        boolean requiresReview,
        boolean conflict) {
    public ExtractedFieldCandidateV1 {
        confidence = Math.max(0.0, Math.min(1.0, confidence));
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        if (value != null && evidence.isEmpty()) {
            throw new IllegalArgumentException("non-empty candidates must include evidence");
        }
    }
}
