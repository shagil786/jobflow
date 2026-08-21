package dev.jobflow.ingestion;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SafeGmailMessage(
        String messageId,
        String threadId,
        String sender,
        String replyTo,
        List<String> recipients,
        String subject,
        Instant receivedAt,
        List<String> labelIds,
        String normalizedContent,
        String normalizedContentHash) {
    public SafeGmailMessage {
        recipients = recipients == null ? List.of() : List.copyOf(recipients.stream().filter(SafeGmailMessage::hasText).toList());
        labelIds = labelIds == null ? List.of() : List.copyOf(labelIds.stream().filter(SafeGmailMessage::hasText).toList());
        normalizedContent = blankToNull(normalizedContent);
        normalizedContentHash = blankToNull(normalizedContentHash);
    }

    @Override
    public String toString() {
        return "SafeGmailMessage[messageId=" + messageId
                + ", threadId=" + threadId
                + ", recipientCount=" + recipients.size()
                + ", labelCount=" + labelIds.size()
                + ", hasSender=" + hasText(sender)
                + ", hasSubject=" + hasText(subject)
                + ", receivedAt=" + receivedAt
                + ", normalizedContentHash=" + normalizedContentHash
                + "]";
    }

    GmailMessageMetadata toMetadata(UUID connectionId, String tenantId, String userId) {
        return new GmailMessageMetadata(
                connectionId,
                tenantId,
                userId,
                messageId,
                threadId,
                sender,
                replyTo,
                recipients.isEmpty() ? null : String.join(",", recipients),
                subject,
                receivedAt,
                labelIds.isEmpty() ? null : String.join(",", labelIds),
                normalizedContentHash);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String blankToNull(String value) {
        return hasText(value) ? value : null;
    }
}
