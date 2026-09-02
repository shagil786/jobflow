package dev.jobflow.ingestion;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class GmailBackfillBatchProcessor implements SqsBackfillWorker.BatchProcessor {
    private static final Logger log = LoggerFactory.getLogger(GmailBackfillBatchProcessor.class);
    private final GmailAutomaticImportService importer;

    GmailBackfillBatchProcessor(GmailAutomaticImportService importer) { this.importer = importer; }

    @Override
    public void process(BackfillQueue.BatchPayload payload) {
        try {
            importer.importBatch(payload.batchId());
        } catch (GmailFetchException exception) {
            throw new SqsBackfillWorker.RetryableBatchException("GMAIL_FETCH_FAILED");
        } catch (RuntimeException exception) {
            log.error("Gmail backfill batch failed batchId={} runId={}", payload.batchId(), payload.runId(), exception);
            throw new SqsBackfillWorker.NonRetryableBatchException("GMAIL_BACKFILL_BATCH_FAILED", exception);
        }
    }
}
