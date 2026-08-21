package dev.jobflow.ingestion;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClassificationSuggestionService {
    private final ClassificationSuggestionStore store;

    public ClassificationSuggestionService(ClassificationSuggestionStore store) {
        this.store = store;
    }

    public ClassificationSuggestionRecord saveIfAbsent(ClassificationSuggestionRecord suggestion) {
        return store.saveIfAbsent(suggestion);
    }

    @Transactional(readOnly = true)
    public Optional<ClassificationSuggestionRecord> findLatestByProviderIdentity(
            String tenantId, String userId, UUID connectionId, String messageId) {
        return store.findLatestByProviderIdentity(tenantId, userId, connectionId, messageId);
    }
}
