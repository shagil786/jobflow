package dev.jobflow.ingestion;

import java.util.UUID;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

public interface GmailConnectionRepository extends JpaRepository<GmailConnectionEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<GmailConnectionEntity> findByTenantIdAndUserId(String tenantId, String userId);
    Optional<GmailConnectionEntity> findByTenantIdAndUserIdAndEmailIgnoreCase(String tenantId, String userId, String email);
    List<GmailConnectionEntity> findAllByTenantIdAndUserIdOrderByConnectedAtDesc(String tenantId, String userId);
}
