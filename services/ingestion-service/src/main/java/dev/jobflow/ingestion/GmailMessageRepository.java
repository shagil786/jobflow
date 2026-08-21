package dev.jobflow.ingestion;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GmailMessageRepository extends JpaRepository<GmailMessageEntity, GmailMessageEntity.GmailMessageId> {
    Optional<GmailMessageEntity> findByIdConnectionIdAndIdMessageId(UUID connectionId, String messageId);
    Optional<GmailMessageEntity> findByTenantIdAndUserIdAndIdConnectionIdAndIdMessageId(
            String tenantId, String userId, UUID connectionId, String messageId);
    long countByIdConnectionId(UUID connectionId);
}
