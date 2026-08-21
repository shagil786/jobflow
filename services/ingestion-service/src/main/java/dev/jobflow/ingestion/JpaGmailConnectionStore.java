package dev.jobflow.ingestion;

import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class JpaGmailConnectionStore implements GmailConnectionStore {
    private final GmailConnectionRepository repository;
    public JpaGmailConnectionStore(GmailConnectionRepository repository) { this.repository=repository; }
    @Override public StoredGmailConnection save(StoredGmailConnection value) { GmailConnectionEntity entity=repository.findById(value.connectionId()).orElseGet(() -> new GmailConnectionEntity(value)); entity.update(value); return repository.save(entity).toModel(); }
    @Override public Optional<StoredGmailConnection> find(UUID id) { return repository.findById(id).map(GmailConnectionEntity::toModel); }
    @Override public Optional<StoredGmailConnection> findByOwner(String tenantId, String userId) { return repository.findByTenantIdAndUserId(tenantId, userId).map(GmailConnectionEntity::toModel); }
    @Override public Optional<StoredGmailConnection> findByOwnerAndEmail(String tenantId, String userId, String email) { return repository.findByTenantIdAndUserIdAndEmailIgnoreCase(tenantId, userId, email).map(GmailConnectionEntity::toModel); }
    @Override public List<StoredGmailConnection> findAllByOwner(String tenantId, String userId) { return repository.findAllByTenantIdAndUserIdOrderByConnectedAtDesc(tenantId, userId).stream().map(GmailConnectionEntity::toModel).toList(); }
}
