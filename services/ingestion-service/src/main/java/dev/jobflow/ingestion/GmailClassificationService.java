package dev.jobflow.ingestion;

import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GmailClassificationService {
    private final GmailEvidenceService evidenceService;
    private final MessageIntentClassifier classifier;
    private final ClassificationSuggestionService suggestions;

    public GmailClassificationService(
            GmailEvidenceService evidenceService,
            MessageIntentClassifier classifier,
            ClassificationSuggestionService suggestions) {
        this.evidenceService = evidenceService;
        this.classifier = classifier;
        this.suggestions = suggestions;
    }

    @Transactional
    public ClassificationSuggestionRecord classify(UUID connectionId, String messageId) {
        Objects.requireNonNull(connectionId, "connectionId");
        requireValue(messageId, "messageId");

        GmailEvidenceService.PreparedMessage prepared = evidenceService.prepareMessage(connectionId, messageId);
        ClassificationSuggestionRecord suggestion = classifier.classify(prepared.message(), prepared.evidence());
        return suggestions.saveIfAbsent(suggestion);
    }

    private static void requireValue(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be null or blank");
        }
    }
}
