package dev.jobflow.ingestion;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SqsBackfillWorkerTest {
    private final BackfillQueue queue = org.mockito.Mockito.mock(BackfillQueue.class);
    private final SqsBackfillWorker.BatchProcessor processor = org.mockito.Mockito.mock(SqsBackfillWorker.BatchProcessor.class);
    private final SqsBackfillWorker.BatchLifecycle lifecycle = org.mockito.Mockito.mock(SqsBackfillWorker.BatchLifecycle.class);
    private final SqsBackfillWorker worker = new SqsBackfillWorker(queue, processor, lifecycle, 3, Duration.ofSeconds(20));

    @Test
    void acknowledgesOnlyAfterSuccessfulProcessing() {
        BackfillQueue.QueuedBatch queued = queued(1);
        when(lifecycle.claim(queued.payload())).thenReturn(true);

        worker.process(queued);

        verify(lifecycle).claim(queued.payload());
        verify(processor).process(queued.payload());
        verify(lifecycle).succeeded(queued.payload());
        verify(queue).acknowledge(queued.receipt());
    }

    @Test
    void retriesRetryableFailuresWithoutAcknowledging() {
        BackfillQueue.QueuedBatch queued = queued(1);
        when(lifecycle.claim(queued.payload())).thenReturn(true);
        doThrow(new SqsBackfillWorker.RetryableBatchException("GMAIL_RATE_LIMITED"))
                .when(processor).process(queued.payload());

        worker.process(queued);

        verify(lifecycle).retryableFailure(queued.payload(), "GMAIL_RATE_LIMITED");
        verify(queue).retry(queued.receipt(), Duration.ofSeconds(20));
        org.mockito.Mockito.verify(queue, org.mockito.Mockito.never()).acknowledge(queued.receipt());
    }

    @Test
    void deadLettersAfterConfiguredAttemptLimit() {
        BackfillQueue.QueuedBatch queued = queued(3);
        when(lifecycle.claim(queued.payload())).thenReturn(true);
        doThrow(new SqsBackfillWorker.RetryableBatchException("GMAIL_UNAVAILABLE"))
                .when(processor).process(queued.payload());

        worker.process(queued);

        verify(lifecycle).deadLettered(queued.payload(), "GMAIL_UNAVAILABLE");
        verify(queue).deadLetter(queued);
        org.mockito.Mockito.verify(queue, org.mockito.Mockito.never()).retry(queued.receipt(), Duration.ofSeconds(20));
    }

    @Test
    void doesNotRetryNonRetryableFailures() {
        BackfillQueue.QueuedBatch queued = queued(1);
        when(lifecycle.claim(queued.payload())).thenReturn(true);
        doThrow(new SqsBackfillWorker.NonRetryableBatchException("INVALID_BATCH"))
                .when(processor).process(queued.payload());

        assertThatThrownBy(() -> worker.process(queued))
                .isInstanceOf(SqsBackfillWorker.NonRetryableBatchException.class);
        verify(lifecycle).failed(queued.payload(), "INVALID_BATCH");
        verify(queue).acknowledge(queued.receipt());
        org.mockito.Mockito.verify(queue, org.mockito.Mockito.never()).retry(queued.receipt(), Duration.ofSeconds(20));
    }

    @Test
    void acknowledgesDuplicateDeliveryWithoutReprocessingClaimedOrCompletedBatch() {
        BackfillQueue.QueuedBatch queued = queued(2);
        when(lifecycle.claim(queued.payload())).thenReturn(false);

        worker.process(queued);

        org.mockito.Mockito.verifyNoInteractions(processor);
        verify(queue).acknowledge(queued.receipt());
    }

    private static BackfillQueue.QueuedBatch queued(int attempt) {
        return new BackfillQueue.QueuedBatch(
                new BackfillQueue.BatchPayload(
                        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "tenant-1", "user-1", 1,
                        Instant.parse("2026-08-20T00:00:00Z"), Instant.parse("2026-08-21T00:00:00Z"),
                        "correlation-1", "attempt-" + attempt),
                new BackfillQueue.Receipt("message-1", "receipt-1"),
                attempt);
    }
}
