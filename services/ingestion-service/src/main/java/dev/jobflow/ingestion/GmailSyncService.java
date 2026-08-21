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

    public GmailSyncService(GmailConnectionStore connections, GmailTokenCipher cipher, GmailApiClient gmail, GmailMessageStore messages) {
        this.connections = connections; this.cipher = cipher; this.gmail = gmail; this.messages = messages;
    }

    @Transactional
    public GmailSyncResult sync(UUID connectionId) {
        StoredGmailConnection connection = connections.find(connectionId).orElseThrow(() -> new IllegalArgumentException("Gmail connection not found"));
        String accessToken = gmail.refreshAccessToken(cipher.decrypt(connection.refreshTokenCiphertext())).value();
        String trackLabelId = gmail.trackLabelId(accessToken);
        try {
            GmailApiClient.HistoryPage history = gmail.listHistory(accessToken, connection.lastHistoryId(), trackLabelId, null);
            int imported = persist(connectionId, history.addedMessages());
            while (history.nextPageToken() != null) {
                history = gmail.listHistory(accessToken, connection.lastHistoryId(), trackLabelId, history.nextPageToken());
                imported += persist(connectionId, history.addedMessages());
            }
            connections.save(connection.withCursor(new SyncCursor(history.historyId(), null)));
            return new GmailSyncResult(connectionId, imported, false, history.historyId(), messages.countForConnection(connectionId));
        } catch (GmailHistoryExpiredException expired) {
            GmailApiClient.MessagePage page = gmail.listMessages(accessToken, GmailSyncScope.queryForLabel(GmailSyncScope.TRACK_LABEL), null);
            int imported = persist(connectionId, page.messages());
            while (page.nextPageToken() != null) {
                page = gmail.listMessages(accessToken, GmailSyncScope.queryForLabel(GmailSyncScope.TRACK_LABEL), page.nextPageToken());
                imported += persist(connectionId, page.messages());
            }
            String historyId = gmail.currentHistoryId(accessToken);
            connections.save(connection.withCursor(new SyncCursor(historyId, null)));
            return new GmailSyncResult(connectionId, imported, true, historyId, messages.countForConnection(connectionId));
        }
    }

    private int persist(UUID connectionId, java.util.List<GmailApiClient.MessageRef> refs) {
        int imported = 0;
        for (GmailApiClient.MessageRef ref : refs) if (ref.messageId() != null && messages.saveIfAbsent(new GmailMessageMetadata(connectionId, ref.messageId(), ref.threadId(), null, GmailSyncScope.TRACK_LABEL))) imported++;
        return imported;
    }

    public record GmailSyncResult(UUID connectionId, int imported, boolean fullResync, String historyId, long totalTrackedMessages) {}
}
