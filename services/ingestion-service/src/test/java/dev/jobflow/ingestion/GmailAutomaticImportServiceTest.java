package dev.jobflow.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class GmailAutomaticImportServiceTest {
    @Test
    void focusedImportPersistsOnlyHighRecallCandidates() {
        UUID connectionId = UUID.randomUUID();
        UUID batchId = UUID.randomUUID();
        InMemoryConnections connections = new InMemoryConnections(connectionId);
        InMemoryMessages messages = new InMemoryMessages();
        InMemoryThreads threads = new InMemoryThreads();
        InMemoryCandidates candidates = new InMemoryCandidates();
        InMemoryBatchRepository batches = new InMemoryBatchRepository(batchId, connectionId);
        FakeGmail gmail = new FakeGmail();
        GmailClassificationService classification = mock(GmailClassificationService.class);
        gmail.pages.add(new GmailApiClient.MessagePage(
                List.of(new GmailApiClient.MessageRef("job-1", "thread-1"), new GmailApiClient.MessageRef("promo-1", "thread-2")),
                null, null));
        gmail.metadata.put("job-1", safe("job-1", "thread-1", "Recruiter <talent@acme.example>", "Interview invitation - Software Engineer"));
        gmail.metadata.put("promo-1", safe("promo-1", "thread-2", "Newsletter <news@retail.example>", "Weekly jobs newsletter - unsubscribe"));

        GmailAutomaticImportService service = new GmailAutomaticImportService(
                batches, connections, new Cipher(), gmail, messages, threads, candidates, new GmailCandidateFilter(), Optional.of(classification));

        GmailAutomaticImportService.ImportBatchResult result = service.importBatch(batchId);

        assertThat(result.importedMessages()).isEqualTo(1);
        assertThat(result.candidateMessages()).isEqualTo(1);
        assertThat(result.query()).contains("after:", "before:");
        assertThat(messages.values).hasSize(1);
        assertThat(threads.threadIds).containsExactly("thread-1");
        assertThat(candidates.values).hasSize(1);
        assertThat(batches.imported).isEqualTo(1);
        assertThat(batches.candidateCount).isEqualTo(1);
        verify(classification).classify(connectionId, "job-1");
    }

    @Test
    void paginatesAndDeduplicatesARepeatedMessageWithoutFetchingBodies() {
        UUID connectionId = UUID.randomUUID();
        UUID batchId = UUID.randomUUID();
        InMemoryConnections connections = new InMemoryConnections(connectionId);
        InMemoryMessages messages = new InMemoryMessages();
        InMemoryThreads threads = new InMemoryThreads();
        InMemoryCandidates candidates = new InMemoryCandidates();
        InMemoryBatchRepository batches = new InMemoryBatchRepository(batchId, connectionId);
        FakeGmail gmail = new FakeGmail();
        gmail.pages.add(new GmailApiClient.MessagePage(List.of(new GmailApiClient.MessageRef("job-1", "thread-1")), "page-2", null));
        gmail.pages.add(new GmailApiClient.MessagePage(List.of(new GmailApiClient.MessageRef("job-1", "thread-1")), null, null));
        gmail.metadata.put("job-1", safe("job-1", "thread-1", "Recruiter <talent@acme.example>", "Application received"));

        GmailAutomaticImportService service = new GmailAutomaticImportService(
                batches, connections, new Cipher(), gmail, messages, threads, candidates, new GmailCandidateFilter());

        GmailAutomaticImportService.ImportBatchResult result = service.importBatch(batchId);

        assertThat(result.importedMessages()).isEqualTo(1);
        assertThat(result.candidateMessages()).isEqualTo(1);
        assertThat(gmail.bodyFetches).isZero();
        assertThat(gmail.queries).hasSize(2).allMatch(query -> query.contains("after:") && query.contains("before:"));
    }

    private static SafeGmailMessage safe(String id, String thread, String sender, String subject) {
        return new SafeGmailMessage(id, thread, sender, null, List.of("candidate@example.com"), subject,
                Instant.parse("2026-08-21T10:00:00Z"), List.of(), null, null);
    }

    private static final class Cipher implements GmailTokenCipher {
        @Override public String encrypt(String value) { return "encrypted:" + value; }
        @Override public String decrypt(String value) { return value.substring("encrypted:".length()); }
    }

    private static final class InMemoryConnections implements GmailConnectionStore {
        private final Map<UUID, StoredGmailConnection> values = new HashMap<>();
        private InMemoryConnections(UUID id) { values.put(id, new StoredGmailConnection(id, "user", "tenant", "person@example.com", "encrypted:refresh", "history", null, Instant.EPOCH)); }
        @Override public StoredGmailConnection save(StoredGmailConnection value) { values.put(value.connectionId(), value); return value; }
        @Override public Optional<StoredGmailConnection> find(UUID id) { return Optional.ofNullable(values.get(id)); }
        @Override public Optional<StoredGmailConnection> findByOwner(String tenantId, String userId) { return Optional.empty(); }
        @Override public Optional<StoredGmailConnection> findByOwnerAndEmail(String tenantId, String userId, String email) { return Optional.empty(); }
        @Override public List<StoredGmailConnection> findAllByOwner(String tenantId, String userId) { return List.of(); }
        @Override public StoredGmailConnection saveAsActive(StoredGmailConnection connection) { return save(connection.withActive(true)); }
        @Override public Optional<StoredGmailConnection> findActiveByOwner(String tenantId, String userId) { return Optional.empty(); }
    }

    private static final class InMemoryMessages implements GmailMessageStore {
        private final Map<String, GmailMessageMetadata> values = new HashMap<>();
        @Override public boolean saveIfAbsent(GmailMessageMetadata message) { return values.putIfAbsent(message.messageId(), message) == null; }
        @Override public Optional<GmailMessageMetadata> findByProviderIdentity(String tenantId, String userId, UUID connectionId, String messageId) { return Optional.ofNullable(values.get(messageId)); }
        @Override public long countForConnection(UUID connectionId) { return values.size(); }
    }

    private static final class InMemoryThreads implements GmailThreadStore {
        private final List<String> threadIds = new ArrayList<>();
        @Override public GmailThreadRecord saveIfAbsent(UUID connectionId, String tenantId, String userId, String threadId) { if (!threadIds.contains(threadId)) threadIds.add(threadId); return new GmailThreadRecord(connectionId, tenantId, userId, threadId, Instant.EPOCH, Instant.EPOCH); }
    }

    private static final class InMemoryCandidates implements GmailMessageCandidateRepository {
        private final Map<String, GmailMessageCandidateEntity> values = new HashMap<>();
        @Override public Optional<GmailMessageCandidateEntity> findByConnectionIdAndProviderMessageId(UUID connectionId, String providerMessageId) { return Optional.ofNullable(values.get(providerMessageId)); }
        @Override public <S extends GmailMessageCandidateEntity> S save(S entity) { values.put(entity.getProviderMessageId(), entity); return entity; }
    }

    private static final class InMemoryBatchRepository implements GmailAutomaticImportService.BatchContextRepository {
        private final UUID batchId;
        private final UUID connectionId;
        private final Instant from = Instant.parse("2026-08-20T00:00:00Z");
        private final Instant to = Instant.parse("2026-08-21T00:00:00Z");
        private int imported;
        private int candidateCount;
        private InMemoryBatchRepository(UUID batchId, UUID connectionId) { this.batchId = batchId; this.connectionId = connectionId; }
        @Override public Optional<GmailAutomaticImportService.BatchContext> findContext(UUID id) { return id.equals(batchId) ? Optional.of(new GmailAutomaticImportService.BatchContext(batchId, UUID.randomUUID(), connectionId, "tenant", "user", from, to)) : Optional.empty(); }
        @Override public void recordCounts(UUID id, int imported, int candidates, Instant now) { this.imported = imported; this.candidateCount = candidates; }
    }

    private static final class FakeGmail implements GmailApiClient {
        private final List<GmailApiClient.MessagePage> pages = new ArrayList<>();
        private final Map<String, SafeGmailMessage> metadata = new HashMap<>();
        private final List<String> queries = new ArrayList<>();
        private int page;
        private int bodyFetches;
        @Override public AccessToken refreshAccessToken(String refreshToken) { return new AccessToken("access", Instant.now().plusSeconds(300)); }
        @Override public String trackLabelId(String accessToken) { throw new AssertionError("automatic import must not resolve a label"); }
        @Override public String currentHistoryId(String accessToken) { return "history"; }
        @Override public HistoryPage listHistory(String accessToken, String startHistoryId, String labelId, String pageToken) { throw new UnsupportedOperationException(); }
        @Override public MessagePage listMessages(String accessToken, String query, String pageToken) { queries.add(query); return pages.get(page++); }
        @Override public SafeGmailMessage fetchMessageMetadata(String accessToken, String messageId) { return metadata.get(messageId); }
        @Override public SafeGmailMessage fetchMessageBodyForProcessing(String accessToken, String messageId) { bodyFetches++; return metadata.get(messageId); }
    }
}
