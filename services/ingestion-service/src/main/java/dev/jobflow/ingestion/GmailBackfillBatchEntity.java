package dev.jobflow.ingestion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "gmail_backfill_batches")
class GmailBackfillBatchEntity {
    @Id private UUID batchId;
    @Column(nullable = false) private UUID runId;
    @Column(nullable = false, length = 120) private String tenantId;
    @Column(nullable = false, length = 120) private String userId;
    @Column(nullable = false) private UUID connectionId;
    @Column(nullable = false) private int sequenceNo;
    @Column(nullable = false) private Instant windowFrom;
    @Column(nullable = false) private Instant windowTo;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private BackfillPriority priority;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private BackfillBatchStatus status;
    @Column(nullable = false) private int attemptCount;
    @Column(nullable = false) private int importedMessages;
    @Column(nullable = false) private int candidateMessages;
    @Column(length = 64) private String lastErrorCode;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;

    protected GmailBackfillBatchEntity() {}
    static GmailBackfillBatchEntity queued(UUID runId, String tenantId, String userId, UUID connectionId, int sequenceNo, BackfillWindow window, Instant now) {
        GmailBackfillBatchEntity entity = new GmailBackfillBatchEntity();
        entity.batchId = UUID.randomUUID(); entity.runId = runId; entity.tenantId = tenantId; entity.userId = userId;
        entity.connectionId = connectionId; entity.sequenceNo = sequenceNo; entity.windowFrom = window.from(); entity.windowTo = window.to();
        entity.priority = window.priority(); entity.status = BackfillBatchStatus.QUEUED; entity.createdAt = now; entity.updatedAt = now;
        return entity;
    }
    UUID getBatchId() { return batchId; } UUID getRunId() { return runId; } UUID getConnectionId() { return connectionId; }
    java.time.Instant getWindowFrom() { return windowFrom; } java.time.Instant getWindowTo() { return windowTo; }
    void markRunning(Instant now) { status = BackfillBatchStatus.RUNNING; attemptCount++; updatedAt = now; }
    void markCompleted(Instant now) { status = BackfillBatchStatus.COMPLETED; updatedAt = now; }
    void markFailed(String code, Instant now) { status = BackfillBatchStatus.FAILED; lastErrorCode = code; updatedAt = now; }
    void markDeadLettered(String code, Instant now) { status = BackfillBatchStatus.DEAD_LETTERED; lastErrorCode = code; updatedAt = now; }
    int getSequenceNo() { return sequenceNo; }
    BackfillBatchStatus getStatus() { return status; } String getTenantId() { return tenantId; } String getUserId() { return userId; }
}
