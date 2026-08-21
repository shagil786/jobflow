package dev.jobflow.ingestion;

import java.util.UUID;

final class GmailBackfillBatchProcessor implements SqsBackfillWorker.BatchProcessor {
    private final GmailAutomaticImportService importer;

    GmailBackfillBatchProcessor(GmailAutomaticImportService importer) { this.importer = importer; }

    @Override
    public void process(BackfillQueue.BatchPayload payload) {
        try {
            importer.importBatch(payload.batchId());
        } catch (GmailFetchException exception) {
            throw new SqsBackfillWorker.RetryableBatchException("GMAIL_FETCH_FAILED");
        } catch (RuntimeException exception) {
            throw new SqsBackfillWorker.NonRetryableBatchException("GMAIL_BACKFILL_BATCH_FAILED");
        }
    }
}
