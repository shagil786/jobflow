package dev.jobflow.ingestion;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class JpaGmailThreadStore implements GmailThreadStore {
    private static final String OWNER_MISMATCH = "gmail thread identity already belongs to a different owner";

    private final GmailThreadRepository repository;
    private final GmailConnectionRepository connections;

    public JpaGmailThreadStore(GmailThreadRepository repository, GmailConnectionRepository connections) {
        this.repository = repository;
        this.connections = connections;
    }

    @Override
    public GmailThreadRecord saveIfAbsent(UUID connectionId, String tenantId, String userId, String threadId) {
        requireOwner(tenantId, "tenantId");
        requireOwner(userId, "userId");
        assertConnectionOwner(connectionId, tenantId, userId);
        GmailThreadEntity.GmailThreadId id = new GmailThreadEntity.GmailThreadId(connectionId, threadId);
        Optional<GmailThreadEntity> existing = repository.findById(id);
        if (existing.isPresent()) {
            if (!existing.get().hasSameOwner(tenantId, userId)) {
                throw new IllegalStateException(OWNER_MISMATCH);
            }
            return existing.get().toRecord();
        }
        return repository.save(new GmailThreadEntity(connectionId, tenantId, userId, threadId)).toRecord();
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
}
