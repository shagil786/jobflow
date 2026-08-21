package dev.jobflow.ingestion;

import java.util.Optional;
import java.util.UUID;

public interface GmailMessageStore {
    boolean saveIfAbsent(GmailMessageMetadata message);
    Optional<GmailMessageMetadata> findByProviderIdentity(String tenantId, String userId, UUID connectionId, String messageId);
    long countForConnection(UUID connectionId);
}
