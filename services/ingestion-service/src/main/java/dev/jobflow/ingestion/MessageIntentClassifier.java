package dev.jobflow.ingestion;

public interface MessageIntentClassifier {
    ClassificationSuggestionRecord classify(SafeGmailMessage message, GmailEvidenceService.PreparedEvidence evidence);
}
