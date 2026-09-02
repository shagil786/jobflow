package dev.jobflow.ingestion;

import java.time.Instant;
import java.util.List;

public interface GmailApiClient {
    AccessToken refreshAccessToken(String refreshToken);
    String trackLabelId(String accessToken);
    String currentHistoryId(String accessToken);
    MessagePage listMessages(String accessToken, String query, String pageToken);
    default MessagePage listMessagesForWindow(String accessToken, Instant from, Instant to, String pageToken) {
        return listMessages(accessToken, dateWindowQuery(from, to), pageToken);
    }
    HistoryPage listHistory(String accessToken, String startHistoryId, String labelId, String pageToken);
    SafeGmailMessage fetchMessageMetadata(String accessToken, String messageId);
    SafeGmailMessage fetchMessageBodyForProcessing(String accessToken, String messageId);
    default String createDraft(String accessToken, String recipient, String subject, String body) { throw new UnsupportedOperationException("Gmail draft creation is not implemented by this client"); }
    default List<SafeGmailMessage> fetchThreadForViewing(String accessToken, String threadId) {
        throw new UnsupportedOperationException("thread viewing is not implemented by this client");
    }

    record AccessToken(String value, Instant expiresAt) {}
    record MessageRef(String messageId, String threadId) {}
    record MessagePage(List<MessageRef> messages, String nextPageToken, String historyId) {}
    record HistoryPage(List<MessageRef> addedMessages, String nextPageToken, String historyId) {}

    static String dateWindowQuery(Instant from, Instant to) {
        if (from == null || to == null || !from.isBefore(to)) {
            throw new IllegalArgumentException("Gmail date window must have from before to");
        }
        return "after:" + from.getEpochSecond() + " before:" + to.getEpochSecond();
    }
}
