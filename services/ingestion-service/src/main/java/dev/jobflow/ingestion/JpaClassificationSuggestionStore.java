package dev.jobflow.ingestion;

import jakarta.persistence.EntityManager;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class JpaClassificationSuggestionStore implements ClassificationSuggestionStore {
    private static final String OWNER_MISMATCH =
            "classification suggestion owner does not match the Gmail connection owner";

    private final ClassificationSuggestionRepository repository;
    private final GmailConnectionRepository connections;
    private final EntityManager entityManager;
    private final TransactionTemplate writeTransaction;

    public JpaClassificationSuggestionStore(
            ClassificationSuggestionRepository repository,
            GmailConnectionRepository connections,
            EntityManager entityManager,
            PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.connections = connections;
        this.entityManager = entityManager;
        this.writeTransaction = new TransactionTemplate(transactionManager);
        this.writeTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
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

        Optional<ClassificationSuggestionEntity> existing = findByScopedIdentity(record.connectionId(), suggestion);
        if (existing.isPresent()) {
            if (!existing.get().hasSameOwner(record.connectionId(), suggestion.tenantId(), suggestion.userId())) {
                throw new IllegalStateException(OWNER_MISMATCH);
            }
            return existing.get().toRecord();
        }

        try {
            return writeTransaction.execute(status -> repository
                    .saveAndFlush(new ClassificationSuggestionEntity(record.connectionId(), suggestion))
                    .toRecord());
        } catch (DataIntegrityViolationException error) {
            entityManager.clear();
            if (!isSuggestionIdentityViolation(error)) {
                throw error;
            }
            return findByScopedIdentity(record.connectionId(), suggestion)
                    .map(ClassificationSuggestionEntity::toRecord)
                    .orElseThrow(() -> error);
        }
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

    private Optional<ClassificationSuggestionEntity> findByScopedIdentity(
            UUID connectionId, ClassificationSuggestionV1 suggestion) {
        return repository.findByTenantIdAndUserIdAndConnectionIdAndMessageIdAndClassifierVersionAndContentHash(
                suggestion.tenantId(),
                suggestion.userId(),
                connectionId,
                suggestion.messageId(),
                suggestion.classifierVersion(),
                suggestion.contentHash());
    }

    private static boolean isSuggestionIdentityViolation(DataIntegrityViolationException error) {
        Throwable cause = error;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException constraint) {
                String name = constraint.getConstraintName();
                if (name == null) {
                    return false;
                }
                String normalized = name.toLowerCase(Locale.ROOT);
                return normalized.contains("ux_classification_suggestions_idempotency")
                        || normalized.contains("ux_classification_suggestions_suggestion_id");
            }
            cause = cause.getCause();
        }
        return false;
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
