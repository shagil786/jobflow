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
@ConditionalOnExpression("'${JOBFLOW_BACKFILL_WORKER_ENABLED:false}' == 'true' and '${JOBFLOW_SQS_ENABLED:false}' == 'true'")
class SqsBackfillWorkerConfiguration {
    @Bean GmailBackfillBatchProcessor gmailBackfillBatchProcessor(GmailAutomaticImportService importer) {
        return new GmailBackfillBatchProcessor(importer);
    }

    @Bean SqsBackfillWorker backfillWorker(BackfillQueue queue, GmailBackfillBatchProcessor processor,
            GmailBackfillBatchRepository batches, GmailBackfillRepository runs, Clock clock) {
        return new SqsBackfillWorker(queue, processor, new JpaBatchLifecycle(batches, runs, clock), 3, Duration.ofSeconds(20));
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
        private final GmailBackfillRepository runs;
        private final Clock clock;
        JpaBatchLifecycle(GmailBackfillBatchRepository batches, GmailBackfillRepository runs, Clock clock) { this.batches = batches; this.runs = runs; this.clock = clock; }
        private GmailBackfillBatchEntity find(BackfillQueue.BatchPayload p) { return batches.findById(p.batchId()).orElseThrow(() -> new IllegalArgumentException("GMAIL_BACKFILL_BATCH_NOT_FOUND")); }
        public boolean claim(BackfillQueue.BatchPayload p) {
            var b = find(p);
            if (b.getStatus() == BackfillBatchStatus.COMPLETED
                    || b.getStatus() == BackfillBatchStatus.FAILED
                    || b.getStatus() == BackfillBatchStatus.DEAD_LETTERED
                    || b.getStatus() == BackfillBatchStatus.CANCELLED) {
                return false;
            }
            var run = runs.findById(p.runId()).orElseThrow(() -> new IllegalArgumentException("GMAIL_BACKFILL_NOT_FOUND"));
            if (run.getStatus().terminal()) return false;
            b.markRunning(Instant.now(clock));
            batches.save(b);
            updateRun(p.runId());
            return true;
        }
        public void succeeded(BackfillQueue.BatchPayload p) { var b = find(p); b.markCompleted(Instant.now(clock)); batches.save(b); updateRun(p.runId()); }
        public void retryableFailure(BackfillQueue.BatchPayload p, String code) { var b = find(p); b.markRetryable(code, Instant.now(clock)); batches.save(b); updateRun(p.runId()); }
        public void failed(BackfillQueue.BatchPayload p, String code) { var b = find(p); b.markFailed(code, Instant.now(clock)); batches.save(b); updateRun(p.runId()); }
        public void deadLettered(BackfillQueue.BatchPayload p, String code) { var b = find(p); b.markDeadLettered(code, Instant.now(clock)); batches.save(b); updateRun(p.runId()); }

        private void updateRun(java.util.UUID runId) {
            var run = runs.findById(runId).orElseThrow(() -> new IllegalArgumentException("GMAIL_BACKFILL_NOT_FOUND"));
            if (run.getStatus().terminal()) return;
            var all = batches.findByRunIdOrderBySequenceNoAsc(runId);
            int completed = (int) all.stream().filter(b -> b.getStatus() == BackfillBatchStatus.COMPLETED).count();
            int failed = (int) all.stream().filter(b -> b.getStatus() == BackfillBatchStatus.FAILED || b.getStatus() == BackfillBatchStatus.DEAD_LETTERED).count();
            int imported = all.stream().mapToInt(GmailBackfillBatchEntity::getImportedMessages).sum();
            int seen = all.stream().mapToInt(GmailBackfillBatchEntity::getMetadataSeen).sum();
            int filtered = all.stream().mapToInt(GmailBackfillBatchEntity::getFilteredMessages).sum();
            int candidates = all.stream().mapToInt(GmailBackfillBatchEntity::getCandidateMessages).sum();
            int bodies = all.stream().mapToInt(GmailBackfillBatchEntity::getBodiesFetched).sum();
            int indexed = all.stream().mapToInt(GmailBackfillBatchEntity::getIndexedThreads).sum();
            int classified = all.stream().mapToInt(GmailBackfillBatchEntity::getClassifiedThreads).sum();
            int promoted = all.stream().mapToInt(GmailBackfillBatchEntity::getAutoPromoted).sum();
            int review = all.stream().mapToInt(GmailBackfillBatchEntity::getNeedsReview).sum();
            Instant now = Instant.now(clock);
            run.setCounters(completed, failed, imported, seen, filtered, candidates, bodies, indexed, classified, promoted, review, now);
            int finished = completed + failed;
            if (finished >= run.getTotalBatches()) {
                run.transition(failed == 0 ? BackfillRunStatus.COMPLETED : BackfillRunStatus.FAILED, now);
            } else if (run.getStatus() == BackfillRunStatus.QUEUED) {
                run.transition(BackfillRunStatus.RUNNING, now);
            }
            runs.save(run);
        }
    }
}
