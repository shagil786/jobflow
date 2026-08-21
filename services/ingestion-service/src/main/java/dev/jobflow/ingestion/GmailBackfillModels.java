package dev.jobflow.ingestion;

import java.time.Instant;
import java.util.UUID;

enum BackfillMode { AUTOMATIC, LABEL_SCOPED }
enum BackfillRunStatus { QUEUED, RUNNING, PAUSING, PAUSED, CANCELLING, CANCELLED, COMPLETED, FAILED
    ; boolean terminal() { return this == CANCELLED || this == COMPLETED || this == FAILED; }
}
enum BackfillBatchStatus { QUEUED, RUNNING, COMPLETED, FAILED, CANCELLED, DEAD_LETTERED }
enum BackfillPriority { HIGH, NORMAL }

record BackfillOwnerContext(String tenantId, String userId) {}
record BackfillRequest(UUID connectionId, Instant from, Instant to, BackfillMode mode) {}
record BackfillWindow(Instant from, Instant to, BackfillPriority priority) {}
record BackfillRunRecord(UUID runId, String tenantId, String userId, UUID connectionId, BackfillMode mode,
        BackfillRunStatus status, Instant from, Instant to, int totalBatches, int completedBatches,
        int failedBatches, int importedMessages) {}
