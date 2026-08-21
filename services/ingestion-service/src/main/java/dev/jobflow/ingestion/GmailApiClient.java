package dev.jobflow.ingestion;

import java.time.Instant;
import java.util.List;

public interface GmailApiClient {
    AccessToken refreshAccessToken(String refreshToken);
    String trackLabelId(String accessToken);
    String currentHistoryId(String accessToken);
    MessagePage listMessages(String accessToken, String query, String pageToken);
    HistoryPage listHistory(String accessToken, String startHistoryId, String labelId, String pageToken);
    SafeGmailMessage fetchMessageMetadata(String accessToken, String messageId);
    SafeGmailMessage fetchMessageBodyForProcessing(String accessToken, String messageId);

    record AccessToken(String value, Instant expiresAt) {}
    record MessageRef(String messageId, String threadId) {}
    record MessagePage(List<MessageRef> messages, String nextPageToken, String historyId) {}
    record HistoryPage(List<MessageRef> addedMessages, String nextPageToken, String historyId) {}
}
