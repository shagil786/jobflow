package dev.jobflow.ingestion;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JpaClassificationSuggestionStore implements ClassificationSuggestionStore {
    private static final String OWNER_MISMATCH =
            "classification suggestion owner does not match the Gmail connection owner";

    private final ClassificationSuggestionRepository repository;
    private final GmailConnectionRepository connections;

    public JpaClassificationSuggestionStore(
            ClassificationSuggestionRepository repository, GmailConnectionRepository connections) {
        this.repository = repository;
        this.connections = connections;
    }

    @Override
    @Transactional
    public ClassificationSuggestionRecord saveIfAbsent(ClassificationSuggestionRecord record) {
        Objects.requireNonNull(record, "record");
        Objects.requireNonNull(record.connectionId(), "record.connectionId");
        Objects.requireNonNull(record.suggestion(), "record.suggestion");

        ClassificationSuggestionV1 suggestion = ClassificationSuggestionEntity.sanitize(record.suggestion());
        requireOwner(suggestion.tenantId(), "tenantId");
        requireOwner(suggestion.userId(), "userId");
        requireValue(suggestion.messageId(), "messageId");
        requireValue(suggestion.threadId(), "threadId");
        requireValue(suggestion.classifierVersion(), "classifierVersion");
        requireValue(suggestion.contentHash(), "contentHash");
        assertConnectionOwner(record.connectionId(), suggestion.tenantId(), suggestion.userId());

        Optional<ClassificationSuggestionEntity> existing = repository.findByConnectionIdAndMessageIdAndClassifierVersionAndContentHash(
                record.connectionId(), suggestion.messageId(), suggestion.classifierVersion(), suggestion.contentHash());
        if (existing.isPresent()) {
            if (!existing.get().hasSameOwner(record.connectionId(), suggestion.tenantId(), suggestion.userId())) {
                throw new IllegalStateException(OWNER_MISMATCH);
            }
            return existing.get().toRecord();
        }

        return repository.save(new ClassificationSuggestionEntity(record.connectionId(), suggestion)).toRecord();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ClassificationSuggestionRecord> findLatestByProviderIdentity(
            String tenantId, String userId, UUID connectionId, String messageId) {
        requireOwner(tenantId, "tenantId");
        requireOwner(userId, "userId");
        requireValue(messageId, "messageId");
        Objects.requireNonNull(connectionId, "connectionId");
        return repository
                .findTopByTenantIdAndUserIdAndConnectionIdAndMessageIdOrderByRowIdDesc(
                        tenantId, userId, connectionId, messageId)
                .map(ClassificationSuggestionEntity::toRecord);
    }

    private void assertConnectionOwner(UUID connectionId, String tenantId, String userId) {
        GmailConnectionEntity connection = connections.findById(connectionId)
                .orElseThrow(UnknownGmailConnectionException::new);
        if (!tenantId.equals(connection.getTenantId()) || !userId.equals(connection.getUserId())) {
            throw new IllegalStateException(OWNER_MISMATCH);
        }
    }

    private static void requireOwner(String value, String field) {
        requireValue(value, field);
    }

    private static void requireValue(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be null or blank");
        }
    }
}
