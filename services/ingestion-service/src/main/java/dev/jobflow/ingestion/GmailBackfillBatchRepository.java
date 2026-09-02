package dev.jobflow.ingestion;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

interface GmailBackfillBatchRepository extends JpaRepository<GmailBackfillBatchEntity, UUID> {
    List<GmailBackfillBatchEntity> findByRunIdOrderBySequenceNoAsc(UUID runId);
    List<GmailBackfillBatchEntity> findByRunIdAndTenantIdAndUserIdOrderBySequenceNoAsc(UUID runId, String tenantId, String userId);

    @Query("select b.batchId as batchId, b.runId as runId, b.connectionId as connectionId, b.tenantId as tenantId, b.userId as userId, b.windowFrom as windowFrom, b.windowTo as windowTo, r.mode as mode from GmailBackfillBatchEntity b join GmailBackfillRunEntity r on r.runId = b.runId where b.batchId = :batchId")
    java.util.Optional<BatchImportContext> findImportContext(@Param("batchId") UUID batchId);

    @Modifying
    @Transactional
    @Query("update GmailBackfillBatchEntity b set b.importedMessages = :imported, b.candidateMessages = :candidates, b.updatedAt = :updatedAt where b.batchId = :batchId")
    int recordImportCounts(@Param("batchId") UUID batchId, @Param("imported") int imported,
            @Param("candidates") int candidates, @Param("updatedAt") java.time.Instant updatedAt);

    @Modifying
    @Transactional
    @Query("update GmailBackfillBatchEntity b set b.metadataSeen = :seen, b.importedMessages = :imported, b.filteredMessages = :filtered, b.candidateMessages = :candidates, b.updatedAt = :updatedAt where b.batchId = :batchId")
    int recordStageCounts(@Param("batchId") UUID batchId, @Param("seen") int seen, @Param("imported") int imported,
            @Param("filtered") int filtered, @Param("candidates") int candidates, @Param("updatedAt") java.time.Instant updatedAt);

    @Modifying
    @Transactional
    @Query("update GmailBackfillBatchEntity b set b.bodiesFetched = b.bodiesFetched + :bodies, b.indexedThreads = b.indexedThreads + :indexed, b.classifiedThreads = b.classifiedThreads + :classified, b.autoPromoted = b.autoPromoted + :promoted, b.needsReview = b.needsReview + :review, b.updatedAt = :updatedAt where b.batchId = :batchId")
    int recordClassificationProgress(@Param("batchId") UUID batchId, @Param("bodies") int bodies,
            @Param("indexed") int indexed, @Param("classified") int classified, @Param("promoted") int promoted,
            @Param("review") int review, @Param("updatedAt") java.time.Instant updatedAt);
}

interface BatchImportContext {
    UUID getBatchId();
    UUID getRunId();
    UUID getConnectionId();
    String getTenantId();
    String getUserId();
    java.time.Instant getWindowFrom();
    java.time.Instant getWindowTo();
    BackfillMode getMode();
}
