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
@Table(name = "gmail_backfill_runs")
class GmailBackfillRunEntity {
    @Id private UUID runId;
    @Column(nullable = false, length = 120) private String tenantId;
    @Column(nullable = false, length = 120) private String userId;
    @Column(nullable = false) private UUID connectionId;
    @Column(nullable = false, length = 255) private String idempotencyKey;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private BackfillMode mode;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private BackfillRunStatus status;
    @Column(nullable = false) private Instant requestedFrom;
    @Column(nullable = false) private Instant requestedTo;
    @Column(nullable = false) private int batchSizeDays;
    @Column(nullable = false) private int totalBatches;
    @Column(nullable = false) private int completedBatches;
    @Column(nullable = false) private int failedBatches;
    @Column(nullable = false) private int importedMessages;
    @Column(nullable = false) private int metadataSeen;
    @Column(nullable = false) private int filteredOut;
    @Column(nullable = false) private int candidates;
    @Column(nullable = false) private int bodiesFetched;
    @Column(nullable = false) private int indexedThreads;
    @Column(nullable = false) private int classifiedThreads;
    @Column(nullable = false) private int autoPromoted;
    @Column(nullable = false) private int needsReview;
    @Column(nullable = false, length = 255) private String correlationId;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
    @Column(nullable = false, length = 120) private String activeOwnerKey;

    protected GmailBackfillRunEntity() {}

    static GmailBackfillRunEntity queued(UUID runId, String tenantId, String userId, UUID connectionId,
            String idempotencyKey, BackfillMode mode, Instant from, Instant to, int batchSizeDays,
            String correlationId, Instant now) {
        GmailBackfillRunEntity entity = new GmailBackfillRunEntity();
        entity.runId = runId; entity.tenantId = tenantId; entity.userId = userId; entity.connectionId = connectionId;
        entity.idempotencyKey = idempotencyKey; entity.mode = mode; entity.status = BackfillRunStatus.QUEUED;
        entity.requestedFrom = from; entity.requestedTo = to; entity.batchSizeDays = batchSizeDays;
        entity.correlationId = correlationId; entity.createdAt = now; entity.updatedAt = now;
        entity.activeOwnerKey = "ACTIVE";
        return entity;
    }

    void setTotalBatches(int value) { totalBatches = value; }
    void transition(BackfillRunStatus next, Instant now) {
        if (status.terminal()) throw new IllegalStateException("GMAIL_BACKFILL_TERMINAL");
        status = next; activeOwnerKey = next.terminal() ? runId.toString() : "ACTIVE"; updatedAt = now;
    }
    void setCounters(int completed, int failed, int imported, int metadata, int filtered, int candidateCount,
            int bodies, int indexed, int classified, int promoted, int review, Instant now) {
        completedBatches = completed; failedBatches = failed; importedMessages = imported; metadataSeen = metadata;
        filteredOut = filtered; candidates = candidateCount; bodiesFetched = bodies; indexedThreads = indexed;
        classifiedThreads = classified; autoPromoted = promoted; needsReview = review; updatedAt = now;
    }
    BackfillRunRecord toRecord() { return new BackfillRunRecord(runId, tenantId, userId, connectionId, mode, status, requestedFrom, requestedTo, totalBatches, completedBatches, failedBatches, importedMessages, metadataSeen, filteredOut, candidates, bodiesFetched, indexedThreads, classifiedThreads, autoPromoted, needsReview); }
    UUID getRunId() { return runId; } String getTenantId() { return tenantId; } String getUserId() { return userId; }
    UUID getConnectionId() { return connectionId; } String getIdempotencyKey() { return idempotencyKey; }
    BackfillRunStatus getStatus() { return status; } BackfillMode getMode() { return mode; }
    Instant getRequestedFrom() { return requestedFrom; } Instant getRequestedTo() { return requestedTo; }
    int getBatchSizeDays() { return batchSizeDays; } int getTotalBatches() { return totalBatches; }
    int getCompletedBatches() { return completedBatches; } int getFailedBatches() { return failedBatches; }
    int getImportedMessages() { return importedMessages; } String getCorrelationId() { return correlationId; }
    int getMetadataSeen() { return metadataSeen; } int getFilteredOut() { return filteredOut; } int getCandidates() { return candidates; }
    int getBodiesFetched() { return bodiesFetched; } int getIndexedThreads() { return indexedThreads; }
    int getClassifiedThreads() { return classifiedThreads; } int getAutoPromoted() { return autoPromoted; } int getNeedsReview() { return needsReview; }
}
