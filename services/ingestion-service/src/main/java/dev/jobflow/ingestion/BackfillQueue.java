package dev.jobflow.ingestion;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Queue port for identifier-only Gmail backfill work. */
public interface BackfillQueue {
    void publish(BatchPayload payload);

    List<QueuedBatch> receive();

    void acknowledge(Receipt receipt);

    void retry(Receipt receipt, java.time.Duration visibilityTimeout);

    void deadLetter(QueuedBatch batch);

    record BatchPayload(
            UUID runId,
            UUID batchId,
            UUID connectionId,
            String tenantId,
            String userId,
            int sequenceNo,
            Instant windowFrom,
            Instant windowTo,
            String correlationId,
            String attemptId) {}

    record Receipt(String messageId, String receiptHandle) {}

    record QueuedBatch(BatchPayload payload, Receipt receipt, int receiveAttempt) {}
}
