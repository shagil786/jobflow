package dev.jobflow.ingestion;

import java.util.Optional;
import java.util.UUID;

public interface GmailConnectionStore {
    StoredGmailConnection save(StoredGmailConnection connection);
    Optional<StoredGmailConnection> find(UUID connectionId);
    Optional<StoredGmailConnection> findByOwner(String tenantId, String userId);
    Optional<StoredGmailConnection> findByOwnerAndEmail(String tenantId, String userId, String email);
}
