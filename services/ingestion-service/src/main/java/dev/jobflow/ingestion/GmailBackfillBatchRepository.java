package dev.jobflow.ingestion;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface GmailBackfillBatchRepository extends JpaRepository<GmailBackfillBatchEntity, UUID> {
    List<GmailBackfillBatchEntity> findByRunIdOrderBySequenceNoAsc(UUID runId);
    List<GmailBackfillBatchEntity> findByRunIdAndTenantIdAndUserIdOrderBySequenceNoAsc(UUID runId, String tenantId, String userId);

    @Query("select b.batchId as batchId, b.runId as runId, b.connectionId as connectionId, b.tenantId as tenantId, b.userId as userId, b.windowFrom as windowFrom, b.windowTo as windowTo from GmailBackfillBatchEntity b where b.batchId = :batchId")
    java.util.Optional<BatchImportContext> findImportContext(@Param("batchId") UUID batchId);

    @Modifying
    @Query("update GmailBackfillBatchEntity b set b.importedMessages = :imported, b.candidateMessages = :candidates, b.updatedAt = :updatedAt where b.batchId = :batchId")
    int recordImportCounts(@Param("batchId") UUID batchId, @Param("imported") int imported,
            @Param("candidates") int candidates, @Param("updatedAt") java.time.Instant updatedAt);
}

interface BatchImportContext {
    UUID getBatchId();
    UUID getRunId();
    UUID getConnectionId();
    String getTenantId();
    String getUserId();
    java.time.Instant getWindowFrom();
    java.time.Instant getWindowTo();
}
