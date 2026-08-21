package dev.jobflow.ingestion;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GmailBackfillBatchProcessorTest {
    private final GmailAutomaticImportService importer = org.mockito.Mockito.mock(GmailAutomaticImportService.class);
    private final GmailBackfillBatchProcessor processor = new GmailBackfillBatchProcessor(importer);

    @Test
    void importsTheBatchByItsDurableIdentifier() {
        BackfillQueue.BatchPayload payload = payload();
        when(importer.importBatch(payload.batchId())).thenReturn(
                new GmailAutomaticImportService.ImportBatchResult(payload.batchId(), 2, 1, "after:1"));

        processor.process(payload);

        verify(importer).importBatch(payload.batchId());
    }

    @Test
    void convertsGmailFetchFailuresToRetryableWorkerFailures() {
        BackfillQueue.BatchPayload payload = payload();
        when(importer.importBatch(payload.batchId())).thenThrow(new GmailFetchException(new RuntimeException("timeout")));

        assertThatThrownBy(() -> processor.process(payload))
                .isInstanceOf(SqsBackfillWorker.RetryableBatchException.class)
                .hasMessage("GMAIL_FETCH_FAILED");
    }

    @Test
    void convertsUnexpectedFailuresToNonRetryableWorkerFailures() {
        BackfillQueue.BatchPayload payload = payload();
        when(importer.importBatch(payload.batchId())).thenThrow(new IllegalArgumentException("bad batch"));

        assertThatThrownBy(() -> processor.process(payload))
                .isInstanceOf(SqsBackfillWorker.NonRetryableBatchException.class)
                .hasMessage("GMAIL_BACKFILL_BATCH_FAILED");
    }

    private static BackfillQueue.BatchPayload payload() {
        return new BackfillQueue.BatchPayload(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "tenant-1", "user-1", 0, Instant.parse("2026-08-20T00:00:00Z"),
                Instant.parse("2026-08-21T00:00:00Z"), "correlation-1", "attempt-1");
    }
}
