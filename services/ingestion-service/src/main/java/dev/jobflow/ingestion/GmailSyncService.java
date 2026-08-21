package dev.jobflow.ingestion;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GmailSyncService {
    private final GmailConnectionStore connections;
    private final GmailTokenCipher cipher;
    private final GmailApiClient gmail;
    private final GmailMessageStore messages;
    private final GmailThreadStore threads;

    public GmailSyncService(
            GmailConnectionStore connections,
            GmailTokenCipher cipher,
            GmailApiClient gmail,
            GmailMessageStore messages,
            GmailThreadStore threads) {
        this.connections = connections;
        this.cipher = cipher;
        this.gmail = gmail;
        this.messages = messages;
        this.threads = threads;
    }

    @Transactional
    public GmailSyncResult sync(UUID connectionId) {
        StoredGmailConnection connection = connections.find(connectionId).orElseThrow(UnknownGmailConnectionException::new);
        String accessToken = gmail.refreshAccessToken(cipher.decrypt(connection.refreshTokenCiphertext())).value();
        String trackLabelId = gmail.trackLabelId(accessToken);
        try {
            GmailApiClient.HistoryPage history = gmail.listHistory(accessToken, connection.lastHistoryId(), trackLabelId, null);
            int imported = persist(connection, accessToken, history.addedMessages());
            while (history.nextPageToken() != null) {
                history = gmail.listHistory(accessToken, connection.lastHistoryId(), trackLabelId, history.nextPageToken());
                imported += persist(connection, accessToken, history.addedMessages());
            }
            connections.save(connection.withCursor(new SyncCursor(history.historyId(), null)));
            return new GmailSyncResult(connectionId, imported, false, history.historyId(), messages.countForConnection(connectionId));
        } catch (GmailHistoryExpiredException expired) {
            GmailApiClient.MessagePage page = gmail.listMessages(accessToken, GmailSyncScope.queryForLabel(GmailSyncScope.TRACK_LABEL), null);
            int imported = persist(connection, accessToken, page.messages());
            while (page.nextPageToken() != null) {
                page = gmail.listMessages(accessToken, GmailSyncScope.queryForLabel(GmailSyncScope.TRACK_LABEL), page.nextPageToken());
                imported += persist(connection, accessToken, page.messages());
            }
            String historyId = gmail.currentHistoryId(accessToken);
            connections.save(connection.withCursor(new SyncCursor(historyId, null)));
            return new GmailSyncResult(connectionId, imported, true, historyId, messages.countForConnection(connectionId));
        }
    }

    private int persist(StoredGmailConnection connection, String accessToken, java.util.List<GmailApiClient.MessageRef> refs) {
        int imported = 0;
        for (GmailApiClient.MessageRef ref : refs) {
            if (ref.messageId() == null || ref.messageId().isBlank()) {
                continue;
            }
            SafeGmailMessage message = gmail.fetchMessageMetadata(accessToken, ref.messageId());
            if (message == null || message.threadId() == null || message.threadId().isBlank()) {
                throw new IllegalStateException("Gmail metadata did not include a thread id");
            }
            threads.saveIfAbsent(connection.connectionId(), connection.tenantId(), connection.userId(), message.threadId());
            if (messages.saveIfAbsent(message.toMetadata(connection.connectionId(), connection.tenantId(), connection.userId()))) {
                imported++;
            }
        }
        return imported;
    }

    public record GmailSyncResult(UUID connectionId, int imported, boolean fullResync, String historyId, long totalTrackedMessages) {}
}
