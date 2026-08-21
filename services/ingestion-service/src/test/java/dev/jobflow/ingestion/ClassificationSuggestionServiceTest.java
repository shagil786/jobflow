package dev.jobflow.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClassificationSuggestionServiceTest {
    @Test
    void delegatesPersistenceAndLookupToTheStore() {
        FakeClassificationSuggestionStore store = new FakeClassificationSuggestionStore();
        ClassificationSuggestionService service = new ClassificationSuggestionService(store);
        ClassificationSuggestionRecord suggestion = new ClassificationSuggestionRecord(
                UUID.randomUUID(),
                new ClassificationSuggestionV1(
                        "tenant-1",
                        "user-1",
                        UUID.randomUUID().toString(),
                        "message-1",
                        "thread-1",
                        MessageIntent.UNKNOWN,
                        MessageDirection.UNKNOWN,
                        null,
                        null,
                        null,
                        null,
                        0.2,
                        java.util.List.of(),
                        java.util.List.of("company"),
                        java.util.List.of(),
                        true,
                        "rules-2026-08-21-v1",
                        "hash-1"));

        ClassificationSuggestionRecord saved = service.saveIfAbsent(suggestion);
        Optional<ClassificationSuggestionRecord> latest =
                service.findLatestByProviderIdentity("tenant-1", "user-1", suggestion.connectionId(), "message-1");

        assertThat(saved).isEqualTo(suggestion);
        assertThat(store.saved).isEqualTo(suggestion);
        assertThat(latest).contains(suggestion);
    }

    private static final class FakeClassificationSuggestionStore implements ClassificationSuggestionStore {
        private ClassificationSuggestionRecord saved;

        @Override
        public ClassificationSuggestionRecord saveIfAbsent(ClassificationSuggestionRecord suggestion) {
            this.saved = suggestion;
            return suggestion;
        }

        @Override
        public Optional<ClassificationSuggestionRecord> findLatestByProviderIdentity(
                String tenantId, String userId, UUID connectionId, String messageId) {
            return Optional.ofNullable(saved);
        }
    }
}
