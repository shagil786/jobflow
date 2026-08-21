package dev.jobflow.ingestion;

import java.util.Optional;
import java.util.UUID;

public interface ClassificationSuggestionStore {
    ClassificationSuggestionRecord saveIfAbsent(ClassificationSuggestionRecord suggestion);

    Optional<ClassificationSuggestionRecord> findLatestByProviderIdentity(
            String tenantId, String userId, UUID connectionId, String messageId);
}
