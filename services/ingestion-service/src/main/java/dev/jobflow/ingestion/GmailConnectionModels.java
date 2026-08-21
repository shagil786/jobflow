package dev.jobflow.ingestion;

import java.time.Instant;
import java.util.UUID;

record GmailConnectionCommand(String userId, String tenantId, String email, String refreshToken, String historyId) {}
record GmailConnectionRecord(UUID connectionId, String email, String lastHistoryId, String pageToken) {}
record GmailConnectionStatus(UUID connectionId, boolean connected, String email, Instant connectedAt) {}
record StoredGmailConnection(UUID connectionId, String userId, String tenantId, String email,
        String refreshTokenCiphertext, String lastHistoryId, String pageToken, Instant connectedAt) {
    StoredGmailConnection withCursor(SyncCursor cursor) {
        return new StoredGmailConnection(connectionId, userId, tenantId, email, refreshTokenCiphertext,
                cursor.historyId(), cursor.pageToken(), connectedAt);
    }
}
