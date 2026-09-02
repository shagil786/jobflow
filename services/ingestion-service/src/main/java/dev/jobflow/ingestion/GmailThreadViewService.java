package dev.jobflow.ingestion;

import java.util.List;
import org.springframework.stereotype.Service;

/** Fetches a user-authorized thread on demand without persisting raw message bodies. */
@Service
class GmailThreadViewService {
    private final GmailConnectionStore connections;
    private final GmailTokenCipher cipher;
    private final GmailApiClient gmail;

    GmailThreadViewService(GmailConnectionStore connections, GmailTokenCipher cipher, GmailApiClient gmail) {
        this.connections = connections;
        this.cipher = cipher;
        this.gmail = gmail;
    }

    ThreadView view(String tenantId, String userId, String threadId) {
        if (threadId == null || threadId.isBlank()) throw new IllegalArgumentException("threadId must not be blank");
        StoredGmailConnection connection = connections.findActiveByOwner(tenantId, userId)
                .or(() -> connections.findByOwner(tenantId, userId))
                .orElseThrow(UnknownGmailConnectionException::new);
        String accessToken = gmail.refreshAccessToken(cipher.decrypt(connection.refreshTokenCiphertext())).value();
        List<ThreadMessage> messages = gmail.fetchThreadForViewing(accessToken, threadId).stream()
                .map(message -> toThreadMessage(message, connection.email()))
                .toList();
        return new ThreadView(threadId, messages);
    }

    ThreadView viewFromMessage(String tenantId, String userId, String messageId) {
        if (messageId == null || messageId.isBlank()) throw new IllegalArgumentException("messageId must not be blank");
        StoredGmailConnection connection = connections.findActiveByOwner(tenantId, userId)
                .or(() -> connections.findByOwner(tenantId, userId))
                .orElseThrow(UnknownGmailConnectionException::new);
        String accessToken = gmail.refreshAccessToken(cipher.decrypt(connection.refreshTokenCiphertext())).value();
        SafeGmailMessage message = gmail.fetchMessageBodyForProcessing(accessToken, messageId);
        if (message.threadId() == null || message.threadId().isBlank()) throw new IllegalArgumentException("Gmail message has no thread");
        List<SafeGmailMessage> fetchedThread;
        try {
            fetchedThread = gmail.fetchThreadForViewing(accessToken, message.threadId());
        } catch (RuntimeException ignored) {
            // Gmail can reject thread expansion for an older or partially indexed message.
            // The verified message is still safe and useful to show; do not invent siblings.
            fetchedThread = List.of(message);
        }
        List<ThreadMessage> messages = fetchedThread.stream()
                .map(value -> toThreadMessage(value, connection.email())).toList();
        return new ThreadView(message.threadId(), messages);
    }

    private static ThreadMessage toThreadMessage(SafeGmailMessage message, String mailbox) {
        boolean sent = message.sender() != null && mailbox != null && message.sender().equalsIgnoreCase(mailbox);
        return new ThreadMessage(message.messageId(), sent ? "OUTBOUND" : "INBOUND", message.sender(), message.recipients(),
                message.subject(), message.receivedAt(), message.normalizedContent());
    }

    record ThreadView(String threadId, List<ThreadMessage> messages) {}
    record ThreadMessage(String messageId, String direction, String sender, List<String> recipients,
            String subject, java.time.Instant receivedAt, String body) {}
}
