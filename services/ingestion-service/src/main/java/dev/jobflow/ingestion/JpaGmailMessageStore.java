package dev.jobflow.ingestion;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class JpaGmailMessageStore implements GmailMessageStore {
    private static final String OWNER_MISMATCH = "gmail message identity already belongs to a different owner";

    private final GmailMessageRepository repository;

    public JpaGmailMessageStore(GmailMessageRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean saveIfAbsent(GmailMessageMetadata message) {
        requireOwner(message.tenantId(), "tenantId");
        requireOwner(message.userId(), "userId");
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
