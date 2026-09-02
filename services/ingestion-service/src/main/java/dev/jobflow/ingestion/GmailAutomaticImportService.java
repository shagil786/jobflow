package dev.jobflow.ingestion;

import java.time.Instant;
import java.util.UUID;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
class GmailAutomaticImportService {
    private static final Logger log = LoggerFactory.getLogger(GmailAutomaticImportService.class);
    private final BatchContextRepository batches;
    private final GmailConnectionStore connections;
    private final GmailTokenCipher cipher;
    private final GmailApiClient gmail;
    private final GmailMessageStore messages;
    private final GmailThreadStore threads;
    private final GmailMessageCandidateRepository candidates;
    private final GmailCandidateFilter filter;
    private final java.util.Optional<GmailClassificationService> classification;
    private final java.util.Optional<GmailClassificationDispatcher> classificationDispatcher;

    @Autowired
    GmailAutomaticImportService(
            GmailBackfillBatchRepository batches,
            GmailConnectionStore connections,
            GmailTokenCipher cipher,
            GmailApiClient gmail,
            GmailMessageStore messages,
            GmailThreadStore threads,
            GmailMessageCandidateRepository candidates,
            GmailCandidateFilter filter,
            GmailClassificationService classification,
            GmailClassificationDispatcher classificationDispatcher) {
        this(new JpaBatchContextRepository(batches), connections, cipher, gmail, messages, threads, candidates, filter,
                java.util.Optional.of(classification), java.util.Optional.of(classificationDispatcher));
    }

    GmailAutomaticImportService(
            GmailBackfillBatchRepository batches,
            GmailConnectionStore connections,
            GmailTokenCipher cipher,
            GmailApiClient gmail,
            GmailMessageStore messages,
            GmailThreadStore threads,
            GmailMessageCandidateRepository candidates,
            GmailCandidateFilter filter) {
        this(new JpaBatchContextRepository(batches), connections, cipher, gmail, messages, threads, candidates, filter,
                java.util.Optional.empty(), java.util.Optional.empty());
    }

    GmailAutomaticImportService(
            BatchContextRepository batches,
            GmailConnectionStore connections,
            GmailTokenCipher cipher,
            GmailApiClient gmail,
            GmailMessageStore messages,
            GmailThreadStore threads,
            GmailMessageCandidateRepository candidates,
            GmailCandidateFilter filter) {
        this(batches, connections, cipher, gmail, messages, threads, candidates, filter,
                java.util.Optional.empty(), java.util.Optional.empty());
    }

    GmailAutomaticImportService(
            BatchContextRepository batches,
            GmailConnectionStore connections,
            GmailTokenCipher cipher,
            GmailApiClient gmail,
            GmailMessageStore messages,
            GmailThreadStore threads,
            GmailMessageCandidateRepository candidates,
            GmailCandidateFilter filter,
            java.util.Optional<GmailClassificationService> classification) {
        this(batches, connections, cipher, gmail, messages, threads, candidates, filter, classification, java.util.Optional.empty());
    }

    GmailAutomaticImportService(
            BatchContextRepository batches,
            GmailConnectionStore connections,
            GmailTokenCipher cipher,
            GmailApiClient gmail,
            GmailMessageStore messages,
            GmailThreadStore threads,
            GmailMessageCandidateRepository candidates,
            GmailCandidateFilter filter,
            java.util.Optional<GmailClassificationService> classification,
            java.util.Optional<GmailClassificationDispatcher> classificationDispatcher) {
        this.batches = batches;
        this.connections = connections;
        this.cipher = cipher;
        this.gmail = gmail;
        this.messages = messages;
        this.threads = threads;
        this.candidates = candidates;
        this.filter = filter;
        this.classification = classification;
        this.classificationDispatcher = classificationDispatcher;
    }

    ImportBatchResult importBatch(UUID batchId) {
        BatchContext context = batches.findContext(batchId)
                .orElseThrow(() -> new IllegalArgumentException("GMAIL_BACKFILL_BATCH_NOT_FOUND"));
        StoredGmailConnection connection = connections.find(context.connectionId())
                .orElseThrow(UnknownGmailConnectionException::new);
        requireOwner(context, connection);
        String accessToken = gmail.refreshAccessToken(cipher.decrypt(connection.refreshTokenCiphertext())).value();
        String query = GmailApiClient.dateWindowQuery(context.windowFrom(), context.windowTo());
        int imported = 0;
        int candidateCount = 0;
        int candidateMessagesSeen = 0;
        int processed = 0;
        Set<String> classifiedThreads = new HashSet<>();
        String pageToken = null;
        do {
            GmailApiClient.MessagePage page = gmail.listMessages(accessToken, query, pageToken);
            for (GmailApiClient.MessageRef ref : page.messages()) {
                if (ref == null || blank(ref.messageId())) continue;
                SafeGmailMessage safe = gmail.fetchMessageMetadata(accessToken, ref.messageId());
                if (safe == null || blank(safe.threadId())) {
                    throw new IllegalArgumentException("GMAIL_MESSAGE_THREAD_ID_MISSING");
                }
                GmailMessageMetadata metadata = safe.toMetadata(connection.connectionId(), connection.tenantId(), connection.userId());
                processed++;
                boolean fullScan = context.mode() == BackfillMode.FULL;
                boolean broadScan = context.mode() == BackfillMode.BROAD;
                GmailCandidateFilter.CandidateDecision decision = fullScan
                        ? filter.reviewAll()
                        : broadScan ? filter.evaluateBroad(metadata) : filter.evaluate(metadata);
                if (!decision.candidate()) {
                    batches.recordStageCounts(batchId, processed, imported, processed - candidateMessagesSeen, candidateCount, Instant.now());
                    continue;
                }
                candidateMessagesSeen++;
                threads.saveIfAbsent(connection.connectionId(), connection.tenantId(), connection.userId(), safe.threadId());
                if (messages.saveIfAbsent(metadata)) imported++;
                if (decision.candidate()) {
                    var existing = candidates.findByConnectionIdAndThreadId(connection.connectionId(), safe.threadId())
                            .or(() -> candidates.findByConnectionIdAndProviderMessageId(connection.connectionId(), safe.messageId()));
                    if (existing.isPresent()) {
                        existing.get().refresh(decision, context.runId(), batchId, Instant.now());
                    } else {
                        candidates.save(GmailMessageCandidateEntity.pending(metadata, context.runId(), batchId, decision, Instant.now()));
                        candidateCount++;
                    }
                    try {
                        if (!classifiedThreads.add(safe.threadId())) continue;
                        if (classificationDispatcher.isPresent()) {
                            classificationDispatcher.get().dispatch(connection.connectionId(), safe.messageId(), batchId);
                        } else {
                            classification.ifPresent(service -> service.classify(connection.connectionId(), safe.messageId()));
                        }
                    } catch (RuntimeException exception) {
                        // Classification is enrichment. A slow or unavailable classifier
                        // must not roll back the genuine Gmail metadata already persisted.
                        log.warn("Gmail classification skipped for connection={} reason={}",
                                connection.connectionId(), exception.getClass().getSimpleName());
                    }
                }
                if (processed % 10 == 0) {
                    batches.recordStageCounts(batchId, processed, imported, processed - candidateMessagesSeen, candidateCount, Instant.now());
                }
            }
            pageToken = page.nextPageToken();
        } while (pageToken != null && !pageToken.isBlank());
        batches.recordStageCounts(batchId, processed, imported, processed - candidateMessagesSeen, candidateCount, Instant.now());
        return new ImportBatchResult(batchId, imported, candidateCount, query);
    }

    private static void requireOwner(BatchContext context, StoredGmailConnection connection) {
        if (!context.tenantId().equals(connection.tenantId()) || !context.userId().equals(connection.userId())) {
            throw new IllegalStateException("GMAIL_BACKFILL_OWNER_MISMATCH");
        }
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }

    record ImportBatchResult(UUID batchId, int importedMessages, int candidateMessages, String query) {}

    record BatchContext(UUID batchId, UUID runId, UUID connectionId, String tenantId, String userId, Instant windowFrom, Instant windowTo, BackfillMode mode) {
        BatchContext(UUID batchId, UUID runId, UUID connectionId, String tenantId, String userId, Instant windowFrom, Instant windowTo) {
            this(batchId, runId, connectionId, tenantId, userId, windowFrom, windowTo, BackfillMode.FOCUSED);
        }
    }

        interface BatchContextRepository {
        java.util.Optional<BatchContext> findContext(UUID batchId);
        void recordCounts(UUID batchId, int imported, int candidates, Instant now);
        default void recordStageCounts(UUID batchId, int seen, int imported, int filtered, int candidates, Instant now) {
            recordCounts(batchId, imported, candidates, now);
        }
    }

    private record JpaBatchContextRepository(GmailBackfillBatchRepository delegate) implements BatchContextRepository {
        @Override public java.util.Optional<BatchContext> findContext(UUID batchId) {
            return delegate.findImportContext(batchId).map(value -> new BatchContext(value.getBatchId(), value.getRunId(), value.getConnectionId(), value.getTenantId(), value.getUserId(), value.getWindowFrom(), value.getWindowTo(), value.getMode()));
        }
        @Override public void recordCounts(UUID batchId, int imported, int candidates, Instant now) {
            if (delegate.recordImportCounts(batchId, imported, candidates, now) != 1) throw new IllegalArgumentException("GMAIL_BACKFILL_BATCH_NOT_FOUND");
        }
        @Override public void recordStageCounts(UUID batchId, int seen, int imported, int filtered, int candidates, Instant now) {
            if (delegate.recordStageCounts(batchId, seen, imported, filtered, candidates, now) != 1) throw new IllegalArgumentException("GMAIL_BACKFILL_BATCH_NOT_FOUND");
        }
    }
}
