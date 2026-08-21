package dev.jobflow.ingestion;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class JpaGmailMessageStore implements GmailMessageStore {
    private static final String OWNER_MISMATCH = "gmail message identity already belongs to a different owner";

    private final GmailMessageRepository repository;
    private final GmailConnectionRepository connections;

    public JpaGmailMessageStore(GmailMessageRepository repository, GmailConnectionRepository connections) {
        this.repository = repository;
        this.connections = connections;
    }

    @Override
    public boolean saveIfAbsent(GmailMessageMetadata message) {
        requireOwner(message.tenantId(), "tenantId");
        requireOwner(message.userId(), "userId");
        assertConnectionOwner(message.connectionId(), message.tenantId(), message.userId());
        Optional<GmailMessageEntity> existing = repository.findByIdConnectionIdAndIdMessageId(
                message.connectionId(), message.messageId());
        if (existing.isPresent()) {
            if (!existing.get().hasSameOwnerAs(message)) {
                throw new IllegalStateException(OWNER_MISMATCH);
            }
            return false;
        }
        repository.save(new GmailMessageEntity(message));
        return true;
    }

    private void assertConnectionOwner(UUID connectionId, String tenantId, String userId) {
        GmailConnectionEntity connection = connections.findById(connectionId)
                .orElseThrow(UnknownGmailConnectionException::new);
        if (!tenantId.equals(connection.getTenantId()) || !userId.equals(connection.getUserId())) {
            throw new IllegalStateException(OWNER_MISMATCH);
        }
    }

    private static void requireOwner(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be null or blank");
        }
    }

    @Override
    public Optional<GmailMessageMetadata> findByProviderIdentity(String tenantId, String userId, UUID connectionId, String messageId) {
        return repository.findByTenantIdAndUserIdAndIdConnectionIdAndIdMessageId(tenantId, userId, connectionId, messageId)
                .map(GmailMessageEntity::toModel);
    }

    @Override
    public long countForConnection(UUID connectionId) {
        return repository.countByIdConnectionId(connectionId);
    }
}
