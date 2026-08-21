package dev.jobflow.ingestion;

import java.util.Objects;

/** Provider-neutral contract for bounded, structured classification. */
public interface ClassifierProvider {
    String name();

    ClassificationSuggestionV1 classify(ClassificationInput input);

    record ClassificationInput(
            SafeGmailMessage message,
            GmailEvidenceService.PreparedEvidence evidence,
            String candidateId) {
        public ClassificationInput {
            Objects.requireNonNull(message, "message");
            Objects.requireNonNull(evidence, "evidence");
            if (candidateId != null && candidateId.isBlank()) {
                throw new IllegalArgumentException("candidateId must not be blank");
            }
        }
    }
}
