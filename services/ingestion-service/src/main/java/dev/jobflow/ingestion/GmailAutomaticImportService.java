package dev.jobflow.ingestion;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

@Service
class GmailAutomaticImportService {
    private final BatchContextRepository batches;
    private final GmailConnectionStore connections;
    private final GmailTokenCipher cipher;
    private final GmailApiClient gmail;
    private final GmailMessageStore messages;
    private final GmailThreadStore threads;
    private final GmailMessageCandidateRepository candidates;
    private final GmailCandidateFilter filter;

    @Autowired
    GmailAutomaticImportService(
            GmailBackfillBatchRepository batches,
            GmailConnectionStore connections,
            GmailTokenCipher cipher,
            GmailApiClient gmail,
            GmailMessageStore messages,
            GmailThreadStore threads,
            GmailMessageCandidateRepository candidates,
            GmailCandidateFilter filter) {
        this(new JpaBatchContextRepository(batches), connections, cipher, gmail, messages, threads, candidates, filter);
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
        this.batches = batches;
        this.connections = connections;
        this.cipher = cipher;
        this.gmail = gmail;
        this.messages = messages;
        this.threads = threads;
        this.candidates = candidates;
        this.filter = filter;
    }

    @Transactional
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
                threads.saveIfAbsent(connection.connectionId(), connection.tenantId(), connection.userId(), safe.threadId());
                if (messages.saveIfAbsent(metadata)) imported++;
                GmailCandidateFilter.CandidateDecision decision = filter.evaluate(metadata);
                if (decision.candidate()) {
                    var existing = candidates.findByConnectionIdAndProviderMessageId(connection.connectionId(), safe.messageId());
                    if (existing.isPresent()) {
                        existing.get().refresh(decision, context.runId(), batchId, Instant.now());
                    } else {
                        candidates.save(GmailMessageCandidateEntity.pending(metadata, context.runId(), batchId, decision, Instant.now()));
                        candidateCount++;
                    }
                }
            }
            pageToken = page.nextPageToken();
        } while (pageToken != null && !pageToken.isBlank());
        batches.recordCounts(batchId, imported, candidateCount, Instant.now());
        return new ImportBatchResult(batchId, imported, candidateCount, query);
    }

    private static void requireOwner(BatchContext context, StoredGmailConnection connection) {
        if (!context.tenantId().equals(connection.tenantId()) || !context.userId().equals(connection.userId())) {
            throw new IllegalStateException("GMAIL_BACKFILL_OWNER_MISMATCH");
        }
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }

    record ImportBatchResult(UUID batchId, int importedMessages, int candidateMessages, String query) {}

    record BatchContext(UUID batchId, UUID runId, UUID connectionId, String tenantId, String userId, Instant windowFrom, Instant windowTo) {}

    interface BatchContextRepository {
        java.util.Optional<BatchContext> findContext(UUID batchId);
        void recordCounts(UUID batchId, int imported, int candidates, Instant now);
    }

    private record JpaBatchContextRepository(GmailBackfillBatchRepository delegate) implements BatchContextRepository {
        @Override public java.util.Optional<BatchContext> findContext(UUID batchId) {
            return delegate.findImportContext(batchId).map(value -> new BatchContext(value.getBatchId(), value.getRunId(), value.getConnectionId(), value.getTenantId(), value.getUserId(), value.getWindowFrom(), value.getWindowTo()));
        }
        @Override public void recordCounts(UUID batchId, int imported, int candidates, Instant now) {
            if (delegate.recordImportCounts(batchId, imported, candidates, now) != 1) throw new IllegalArgumentException("GMAIL_BACKFILL_BATCH_NOT_FOUND");
        }
    }
}
