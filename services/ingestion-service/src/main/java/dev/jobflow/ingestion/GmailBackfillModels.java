package dev.jobflow.ingestion;

import java.time.Instant;
import java.util.UUID;

enum BackfillMode { FOCUSED, BROAD, FULL, AUTOMATIC, LABEL_SCOPED }
enum ReviewReason { MISSING_CORE_FIELD, CONFLICTING_EVIDENCE, UNSUPPORTED_CLAIM, INVALID_CITATION, LOW_CONFIDENCE, OFFER_STATUS, PROVIDER_FAILURE, RETRIEVAL_FAILURE }
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
        int failedBatches, int importedMessages, int metadataSeen, int filteredOut, int candidates,
        int bodiesFetched, int indexedThreads, int classifiedThreads, int autoPromoted, int needsReview) {
    BackfillRunRecord(UUID runId, String tenantId, String userId, UUID connectionId, BackfillMode mode,
            BackfillRunStatus status, Instant from, Instant to, int totalBatches, int completedBatches,
            int failedBatches, int importedMessages) {
        this(runId, tenantId, userId, connectionId, mode, status, from, to, totalBatches, completedBatches,
                failedBatches, importedMessages, 0, 0, 0, 0, 0, 0, 0, 0);
    }
}
