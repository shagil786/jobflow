package dev.jobflow.ingestion;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class GmailBackfillService {
    private final GmailBackfillRepository runs;
    private final GmailBackfillBatchRepository batches;
    private final GmailConnectionStore connections;
    private final GmailBackfillWindowPlanner planner;
    private final Optional<BackfillQueue> queue;
    private final Clock clock;

    @Autowired
    GmailBackfillService(GmailBackfillRepository runs, GmailBackfillBatchRepository batches, GmailConnectionStore connections,
            GmailBackfillWindowPlanner planner, Optional<BackfillQueue> queue, Clock clock) {
        this.runs = runs; this.batches = batches; this.connections = connections; this.planner = planner; this.queue = queue; this.clock = clock;
    }

    GmailBackfillService(GmailBackfillRepository runs, GmailBackfillBatchRepository batches, GmailConnectionStore connections,
            GmailBackfillWindowPlanner planner, Clock clock) {
        this(runs, batches, connections, planner, Optional.empty(), clock);
    }

    @Transactional
    BackfillRunRecord start(BackfillRequest request, BackfillOwnerContext owner, String idempotencyKey) {
        requireOwner(owner); requireKey(idempotencyKey);
        var replay = runs.findByTenantIdAndUserIdAndIdempotencyKey(owner.tenantId(), owner.userId(), idempotencyKey);
        if (replay.isPresent()) return replay.get().toRecord();
        var connection = connections.find(request.connectionId()).orElseThrow(() -> new IllegalArgumentException("GMAIL_CONNECTION_NOT_FOUND"));
        if (!owner.tenantId().equals(connection.tenantId()) || !owner.userId().equals(connection.userId())) throw new IllegalArgumentException("GMAIL_CONNECTION_NOT_OWNED");
        if (runs.findActiveByTenantIdAndUserId(owner.tenantId(), owner.userId()).isPresent()) throw new IllegalStateException("GMAIL_BACKFILL_ALREADY_ACTIVE");
        Instant to = request.to() == null ? Instant.now(clock) : request.to();
        Instant from = request.from() == null ? to.minus(60, ChronoUnit.DAYS) : request.from();
        BackfillMode mode = request.mode() == null ? BackfillMode.AUTOMATIC : request.mode();
        List<BackfillWindow> windows = planner.plan(from, to);
        Instant now = Instant.now(clock);
        GmailBackfillRunEntity run = GmailBackfillRunEntity.queued(UUID.randomUUID(), owner.tenantId(), owner.userId(), request.connectionId(), idempotencyKey, mode, from, to, GmailBackfillWindowPlanner.DEFAULT_BATCH_SIZE_DAYS, UUID.randomUUID().toString(), now);
        run.setTotalBatches(windows.size()); runs.save(run);
        List<GmailBackfillBatchEntity> persisted = java.util.stream.IntStream.range(0, windows.size()).mapToObj(index -> GmailBackfillBatchEntity.queued(run.getRunId(), owner.tenantId(), owner.userId(), request.connectionId(), index, windows.get(index), now)).toList();
        batches.saveAll(persisted);
        queue.ifPresent(backfillQueue -> persisted.forEach(batch -> backfillQueue.publish(new BackfillQueue.BatchPayload(
                batch.getRunId(), batch.getBatchId(), batch.getConnectionId(), batch.getTenantId(), batch.getUserId(),
                batch.getSequenceNo(), batch.getWindowFrom(), batch.getWindowTo(), run.getCorrelationId(), UUID.randomUUID().toString()))));
        return run.toRecord();
    }

    @Transactional(readOnly = true)
    BackfillRunRecord status(UUID runId, BackfillOwnerContext owner) {
        requireOwner(owner);
        return runs.findByRunIdAndTenantIdAndUserId(runId, owner.tenantId(), owner.userId()).orElseThrow(() -> new IllegalArgumentException("GMAIL_BACKFILL_NOT_FOUND")).toRecord();
    }

    @Transactional
    BackfillRunRecord pause(UUID runId, BackfillOwnerContext owner) { return transition(runId, owner, BackfillRunStatus.PAUSING); }
    @Transactional
    BackfillRunRecord resume(UUID runId, BackfillOwnerContext owner) { return transition(runId, owner, BackfillRunStatus.RUNNING); }
    @Transactional
    BackfillRunRecord cancel(UUID runId, BackfillOwnerContext owner) { return transition(runId, owner, BackfillRunStatus.CANCELLING); }

    private BackfillRunRecord transition(UUID runId, BackfillOwnerContext owner, BackfillRunStatus next) {
        requireOwner(owner);
        GmailBackfillRunEntity run = runs.findByRunIdAndTenantIdAndUserId(runId, owner.tenantId(), owner.userId()).orElseThrow(() -> new IllegalArgumentException("GMAIL_BACKFILL_NOT_FOUND"));
        run.transition(next, Instant.now(clock));
        return runs.save(run).toRecord();
    }
    private static void requireOwner(BackfillOwnerContext owner) { if (owner == null || blank(owner.tenantId()) || blank(owner.userId())) throw new IllegalArgumentException("BACKFILL_OWNER_REQUIRED"); }
    private static void requireKey(String value) { if (blank(value)) throw new IllegalArgumentException("IDEMPOTENCY_KEY_REQUIRED"); }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
