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

class GmailSyncServiceTest {
    @Test
    void incrementallyImportsOnlyNewMessagesAndAdvancesTheCursor() {
        UUID id = UUID.randomUUID();
        InMemoryConnections connections = connected(id, "history-10");
        InMemoryMessages messages = new InMemoryMessages();
        FakeGmail gmail = new FakeGmail();
        gmail.historyPages.add(new GmailApiClient.HistoryPage(List.of(new GmailApiClient.MessageRef("m-1", "t-1"), new GmailApiClient.MessageRef("m-1", "t-1")), null, "history-11"));

        GmailSyncService.GmailSyncResult result = new GmailSyncService(connections, new Cipher(), gmail, messages).sync(id);

        assertThat(result.imported()).isEqualTo(1);
        assertThat(result.fullResync()).isFalse();
        assertThat(connections.find(id).orElseThrow().lastHistoryId()).isEqualTo("history-11");
        assertThat(gmail.lastHistoryId).isEqualTo("history-10");
        assertThat(gmail.lastLabelId).isEqualTo("Label_JobFlowTrack");
    }

    @Test
    void expiredHistoryPerformsLabelScopedFullRecoveryAndStoresNoBodyContent() {
        UUID id = UUID.randomUUID();
        InMemoryConnections connections = connected(id, "expired-history");
        InMemoryMessages messages = new InMemoryMessages();
        FakeGmail gmail = new FakeGmail();
        gmail.expired = true;
        gmail.messagePages.add(new GmailApiClient.MessagePage(List.of(new GmailApiClient.MessageRef("m-2", "t-2")), null, null));
        gmail.currentHistory = "history-20";

        GmailSyncService.GmailSyncResult result = new GmailSyncService(connections, new Cipher(), gmail, messages).sync(id);

        assertThat(result.imported()).isEqualTo(1);
        assertThat(result.fullResync()).isTrue();
        assertThat(gmail.query).isEqualTo("label:JobFlow/Track");
        assertThat(connections.find(id).orElseThrow().lastHistoryId()).isEqualTo("history-20");
    }

    private static InMemoryConnections connected(UUID id, String historyId) {
        InMemoryConnections store = new InMemoryConnections();
        store.values.put(id, new StoredGmailConnection(id, "user", "tenant", "person@example.com", "encrypted:refresh", historyId, null, Instant.EPOCH));
        return store;
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

    private static final class FakeGmail implements GmailApiClient {
        private final List<HistoryPage> historyPages = new ArrayList<>();
        private final List<MessagePage> messagePages = new ArrayList<>();
        private boolean expired;
        private String currentHistory;
        private String lastHistoryId;
        private String lastLabelId;
        private String query;
        private int historyIndex;
        private int messageIndex;
        @Override public AccessToken refreshAccessToken(String refreshToken) { return new AccessToken("access", Instant.now().plusSeconds(300)); }
        @Override public String trackLabelId(String accessToken) { return "Label_JobFlowTrack"; }
        @Override public String currentHistoryId(String accessToken) { return currentHistory; }
        @Override public HistoryPage listHistory(String accessToken, String startHistoryId, String labelId, String pageToken) { lastHistoryId = startHistoryId; lastLabelId = labelId; if (expired) throw new GmailHistoryExpiredException(); return historyPages.get(historyIndex++); }
        @Override public MessagePage listMessages(String accessToken, String query, String pageToken) { this.query = query; return messagePages.get(messageIndex++); }
    }
}
