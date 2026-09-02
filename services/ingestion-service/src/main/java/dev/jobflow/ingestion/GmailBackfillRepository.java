package dev.jobflow.ingestion;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface GmailBackfillRepository extends JpaRepository<GmailBackfillRunEntity, UUID> {
    Optional<GmailBackfillRunEntity> findByTenantIdAndUserIdAndIdempotencyKey(String tenantId, String userId, String idempotencyKey);
    Optional<GmailBackfillRunEntity> findByRunIdAndTenantIdAndUserId(UUID runId, String tenantId, String userId);
    @Query("select run from GmailBackfillRunEntity run where run.tenantId = :tenantId and run.userId = :userId and run.status in (dev.jobflow.ingestion.BackfillRunStatus.QUEUED, dev.jobflow.ingestion.BackfillRunStatus.RUNNING, dev.jobflow.ingestion.BackfillRunStatus.PAUSING, dev.jobflow.ingestion.BackfillRunStatus.PAUSED, dev.jobflow.ingestion.BackfillRunStatus.CANCELLING)")
    Optional<GmailBackfillRunEntity> findActiveByTenantIdAndUserId(@Param("tenantId") String tenantId, @Param("userId") String userId);
}
