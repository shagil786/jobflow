package dev.jobflow.ingestion;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
@ConditionalOnExpression("'${JOBFLOW_BACKFILL_WORKER_ENABLED:false}' == 'true' and '${jobflow.sqs.enabled:false}' == 'true'")
class SqsBackfillWorkerConfiguration {
    @Bean GmailBackfillBatchProcessor gmailBackfillBatchProcessor(GmailAutomaticImportService importer) {
        return new GmailBackfillBatchProcessor(importer);
    }

    @Bean SqsBackfillWorker backfillWorker(BackfillQueue queue, GmailBackfillBatchProcessor processor,
            GmailBackfillBatchRepository batches, Clock clock) {
        return new SqsBackfillWorker(queue, processor, new JpaBatchLifecycle(batches, clock), 3, Duration.ofSeconds(20));
    }

    @Bean BackfillWorkerRunner backfillWorkerRunner(SqsBackfillWorker worker) { return new BackfillWorkerRunner(worker); }

    static final class BackfillWorkerRunner {
        private final SqsBackfillWorker worker;
        BackfillWorkerRunner(SqsBackfillWorker worker) { this.worker = worker; }
        @Scheduled(fixedDelayString = "${JOBFLOW_BACKFILL_POLL_DELAY_MS:5000}")
        public void poll() { worker.pollOnce(); }
    }

    private static final class JpaBatchLifecycle implements SqsBackfillWorker.BatchLifecycle {
        private final GmailBackfillBatchRepository batches;
        private final Clock clock;
        JpaBatchLifecycle(GmailBackfillBatchRepository batches, Clock clock) { this.batches = batches; this.clock = clock; }
        private GmailBackfillBatchEntity find(BackfillQueue.BatchPayload p) { return batches.findById(p.batchId()).orElseThrow(() -> new IllegalArgumentException("GMAIL_BACKFILL_BATCH_NOT_FOUND")); }
        public void claim(BackfillQueue.BatchPayload p) { var b = find(p); b.markRunning(Instant.now(clock)); batches.save(b); }
        public void succeeded(BackfillQueue.BatchPayload p) { var b = find(p); b.markCompleted(Instant.now(clock)); batches.save(b); }
        public void retryableFailure(BackfillQueue.BatchPayload p, String code) { var b = find(p); b.markFailed(code, Instant.now(clock)); batches.save(b); }
        public void failed(BackfillQueue.BatchPayload p, String code) { var b = find(p); b.markFailed(code, Instant.now(clock)); batches.save(b); }
        public void deadLettered(BackfillQueue.BatchPayload p, String code) { var b = find(p); b.markDeadLettered(code, Instant.now(clock)); batches.save(b); }
    }
}
