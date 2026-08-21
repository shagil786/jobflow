package dev.jobflow.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GmailSyncServiceTest {
    @Test
    void incrementallyImportsOnlyNewMessagesAndAdvancesTheCursor() {
        UUID id = UUID.randomUUID();
        InMemoryConnections connections = connected(id, "history-10");
        InMemoryMessages messages = new InMemoryMessages();
        InMemoryThreads threads = new InMemoryThreads();
        FakeGmail gmail = new FakeGmail();
        gmail.historyPages.add(new GmailApiClient.HistoryPage(List.of(new GmailApiClient.MessageRef("m-1", "t-1"), new GmailApiClient.MessageRef("m-1", "t-1")), null, "history-11"));
        gmail.metadataByMessageId.put("m-1", metadata("m-1", "t-1", "Recruiter <recruiter@example.com>", "reply@example.com", List.of("candidate@example.com"), "Interview update", Instant.parse("2026-08-21T09:00:00Z"), List.of("Label_JobFlowTrack")));

        GmailSyncService.GmailSyncResult result = new GmailSyncService(connections, new Cipher(), gmail, messages, threads).sync(id);

        assertThat(result.imported()).isEqualTo(1);
        assertThat(result.fullResync()).isFalse();
        assertThat(connections.find(id).orElseThrow().lastHistoryId()).isEqualTo("history-11");
        assertThat(gmail.lastHistoryId).isEqualTo("history-10");
        assertThat(gmail.lastLabelId).isEqualTo("Label_JobFlowTrack");
        assertThat(threads.savedThreadKeys).containsExactly(id + "::t-1");
        assertThat(messages.findByProviderIdentity("tenant", "user", id, "m-1"))
                .get()
                .extracting(GmailMessageMetadata::sender, GmailMessageMetadata::replyTo, GmailMessageMetadata::recipients, GmailMessageMetadata::subject, GmailMessageMetadata::labelIds, GmailMessageMetadata::normalizedContentHash)
                .containsExactly("Recruiter <recruiter@example.com>", "reply@example.com", "candidate@example.com", "Interview update", "Label_JobFlowTrack", null);
    }

    @Test
    void expiredHistoryPerformsLabelScopedFullRecoveryAndStoresNoBodyContent() {
        UUID id = UUID.randomUUID();
        InMemoryConnections connections = connected(id, "expired-history");
        InMemoryMessages messages = new InMemoryMessages();
        InMemoryThreads threads = new InMemoryThreads();
        FakeGmail gmail = new FakeGmail();
        gmail.expired = true;
        gmail.messagePages.add(new GmailApiClient.MessagePage(List.of(new GmailApiClient.MessageRef("m-2", "t-2")), null, null));
        gmail.currentHistory = "history-20";
        gmail.metadataByMessageId.put("m-2", metadata("m-2", "t-2", "System <no-reply@example.com>", null, List.of("candidate@example.com"), "Application received", Instant.parse("2026-08-21T10:00:00Z"), List.of("Label_JobFlowTrack")));

        GmailSyncService.GmailSyncResult result = new GmailSyncService(connections, new Cipher(), gmail, messages, threads).sync(id);

        assertThat(result.imported()).isEqualTo(1);
        assertThat(result.fullResync()).isTrue();
        assertThat(gmail.query).isEqualTo("label:JobFlow/Track");
        assertThat(connections.find(id).orElseThrow().lastHistoryId()).isEqualTo("history-20");
        assertThat(gmail.bodyFetchCount).isZero();
        assertThat(threads.savedThreadKeys).containsExactly(id + "::t-2");
        assertThat(messages.findByProviderIdentity("tenant", "user", id, "m-2").get().normalizedContentHash()).isNull();
    }

    @Test
    void metadataFailureLeavesThePriorCursorUnchanged() {
        UUID id = UUID.randomUUID();
        InMemoryConnections connections = connected(id, "history-30");
        InMemoryMessages messages = new InMemoryMessages();
        InMemoryThreads threads = new InMemoryThreads();
        FakeGmail gmail = new FakeGmail();
        gmail.historyPages.add(new GmailApiClient.HistoryPage(List.of(new GmailApiClient.MessageRef("m-3", "t-3"), new GmailApiClient.MessageRef("m-4", "t-4")), null, "history-31"));
        gmail.metadataByMessageId.put("m-3", metadata("m-3", "t-3", "Recruiter <recruiter@example.com>", null, List.of("candidate@example.com"), "First message", Instant.parse("2026-08-21T11:00:00Z"), List.of("Label_JobFlowTrack")));
        gmail.metadataFailures.put("m-4", new IllegalStateException("metadata fetch failed"));

        assertThatThrownBy(() -> new GmailSyncService(connections, new Cipher(), gmail, messages, threads).sync(id))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("metadata fetch failed");

        assertThat(connections.find(id).orElseThrow().lastHistoryId()).isEqualTo("history-30");
    }

    private static InMemoryConnections connected(UUID id, String historyId) {
        InMemoryConnections store = new InMemoryConnections();
        store.values.put(id, new StoredGmailConnection(id, "user", "tenant", "person@example.com", "encrypted:refresh", historyId, null, Instant.EPOCH));
        return store;
    }

    private static SafeGmailMessage metadata(
            String messageId,
            String threadId,
            String sender,
            String replyTo,
            List<String> recipients,
            String subject,
            Instant receivedAt,
            List<String> labelIds) {
        return new SafeGmailMessage(messageId, threadId, sender, replyTo, recipients, subject, receivedAt, labelIds, null, null);
    }

    private static final class Cipher implements GmailTokenCipher {
        @Override public String encrypt(String value) { return "encrypted:" + value; }
        @Override public String decrypt(String value) { return value.substring("encrypted:".length()); }
    }

    private static final class InMemoryConnections implements GmailConnectionStore {
        private final Map<UUID, StoredGmailConnection> values = new HashMap<>();
        @Override public StoredGmailConnection save(StoredGmailConnection value) { values.put(value.connectionId(), value); return value; }
        @Override public Optional<StoredGmailConnection> find(UUID id) { return Optional.ofNullable(values.get(id)); }
        @Override public Optional<StoredGmailConnection> findByOwner(String tenantId, String userId) { return values.values().stream().filter(v -> v.tenantId().equals(tenantId) && v.userId().equals(userId)).findFirst(); }
        @Override public Optional<StoredGmailConnection> findByOwnerAndEmail(String tenantId, String userId, String email) { return values.values().stream().filter(v -> v.tenantId().equals(tenantId) && v.userId().equals(userId) && v.email().equalsIgnoreCase(email)).findFirst(); }
        @Override public List<StoredGmailConnection> findAllByOwner(String tenantId, String userId) { return values.values().stream().filter(v -> v.tenantId().equals(tenantId) && v.userId().equals(userId)).toList(); }
        @Override public StoredGmailConnection saveAsActive(StoredGmailConnection value) { values.replaceAll((key, existing) -> existing.tenantId().equals(value.tenantId()) && existing.userId().equals(value.userId()) ? existing.withActive(false) : existing); return save(value.withActive(true)); }
        @Override public Optional<StoredGmailConnection> findActiveByOwner(String tenantId, String userId) { return values.values().stream().filter(v -> v.active() && v.tenantId().equals(tenantId) && v.userId().equals(userId)).findFirst(); }
    }

    private static final class InMemoryMessages implements GmailMessageStore {
        private final Map<String, GmailMessageMetadata> values = new HashMap<>();
        @Override public boolean saveIfAbsent(GmailMessageMetadata message) { return values.putIfAbsent(key(message.connectionId(), message.messageId()), message) == null; }
        @Override public Optional<GmailMessageMetadata> findByProviderIdentity(String tenantId, String userId, UUID connectionId, String messageId) {
            GmailMessageMetadata message = values.get(key(connectionId, messageId));
            if (message == null) return Optional.empty();
            if (!message.tenantId().equals(tenantId) || !message.userId().equals(userId)) return Optional.empty();
            return Optional.of(message);
        }
        @Override public long countForConnection(UUID connectionId) { return values.values().stream().filter(m -> m.connectionId().equals(connectionId)).count(); }
        private String key(UUID connectionId, String messageId) { return connectionId + "::" + messageId; }
    }

    private static final class InMemoryThreads implements GmailThreadStore {
        private final List<String> savedThreadKeys = new ArrayList<>();
        @Override
        public GmailThreadRecord saveIfAbsent(UUID connectionId, String tenantId, String userId, String threadId) {
            String key = connectionId + "::" + threadId;
            if (!savedThreadKeys.contains(key)) {
                savedThreadKeys.add(key);
            }
            return new GmailThreadRecord(connectionId, tenantId, userId, threadId, Instant.EPOCH, Instant.EPOCH);
        }
    }

    private static final class FakeGmail implements GmailApiClient {
        private final List<HistoryPage> historyPages = new ArrayList<>();
        private final List<MessagePage> messagePages = new ArrayList<>();
        private final Map<String, SafeGmailMessage> metadataByMessageId = new HashMap<>();
        private final Map<String, RuntimeException> metadataFailures = new HashMap<>();
        private boolean expired;
        private String currentHistory;
        private String lastHistoryId;
        private String lastLabelId;
        private String query;
        private int bodyFetchCount;
        private int historyIndex;
        private int messageIndex;
        @Override public AccessToken refreshAccessToken(String refreshToken) { return new AccessToken("access", Instant.now().plusSeconds(300)); }
        @Override public String trackLabelId(String accessToken) { return "Label_JobFlowTrack"; }
        @Override public String currentHistoryId(String accessToken) { return currentHistory; }
        @Override public HistoryPage listHistory(String accessToken, String startHistoryId, String labelId, String pageToken) { lastHistoryId = startHistoryId; lastLabelId = labelId; if (expired) throw new GmailHistoryExpiredException(); return historyPages.get(historyIndex++); }
        @Override public MessagePage listMessages(String accessToken, String query, String pageToken) { this.query = query; return messagePages.get(messageIndex++); }
        @Override public SafeGmailMessage fetchMessageMetadata(String accessToken, String messageId) {
            RuntimeException failure = metadataFailures.get(messageId);
            if (failure != null) {
                throw failure;
            }
            return metadataByMessageId.get(messageId);
        }
        @Override public SafeGmailMessage fetchMessageBodyForProcessing(String accessToken, String messageId) {
            bodyFetchCount++;
            return metadataByMessageId.get(messageId);
        }
    }
}
