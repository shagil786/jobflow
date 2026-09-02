package dev.jobflow.ingestion;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "gmail_messages")
public class GmailMessageEntity {
    @EmbeddedId
    private GmailMessageId id;

    @Column(nullable = false, length = 120)
    private String tenantId;

    @Column(nullable = false, length = 120)
    private String userId;

    @Column(nullable = false, length = 255)
    private String threadId;

    @Column(length = 320)
    private String sender;

    @Column(length = 320)
    private String replyTo;

    @Column(length = 16384)
    private String recipients;

    @Column(length = 1024)
    private String subject;

    private Instant receivedAt;

    @Column(length = 2048)
    private String labelIds;

    @Column(length = 128)
    private String normalizedContentHash;

    @Column(nullable = false)
    private Instant capturedAt;

    protected GmailMessageEntity() {}

    GmailMessageEntity(GmailMessageMetadata value) {
        update(value);
    }

    void update(GmailMessageMetadata value) {
        id = new GmailMessageId(value.connectionId(), value.messageId());
        tenantId = value.tenantId();
        userId = value.userId();
        threadId = value.threadId();
        sender = value.sender();
        replyTo = value.replyTo();
        recipients = value.recipients();
        subject = value.subject();
        receivedAt = value.receivedAt();
        labelIds = value.labelIds();
        normalizedContentHash = value.normalizedContentHash();
        if (capturedAt == null) {
            capturedAt = Instant.now();
        }
    }

    boolean hasSameOwnerAs(GmailMessageMetadata value) {
        return Objects.equals(id.connectionId(), value.connectionId())
                && Objects.equals(tenantId, value.tenantId())
                && Objects.equals(userId, value.userId());
    }

    GmailMessageMetadata toModel() {
        return new GmailMessageMetadata(
                id.connectionId(),
                tenantId,
                userId,
                id.messageId(),
                threadId,
                sender,
                replyTo,
                recipients,
                subject,
                receivedAt,
                labelIds,
                normalizedContentHash);
    }

    @Embeddable
    public static class GmailMessageId implements Serializable {
        @Column(name = "connection_id", nullable = false)
        private UUID connectionId;

        @Column(name = "message_id", nullable = false, length = 255)
        private String messageId;

        protected GmailMessageId() {}

        GmailMessageId(UUID connectionId, String messageId) {
            this.connectionId = connectionId;
            this.messageId = messageId;
        }

        UUID connectionId() {
            return connectionId;
        }

        String messageId() {
            return messageId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof GmailMessageId that)) {
                return false;
            }
            return Objects.equals(connectionId, that.connectionId) && Objects.equals(messageId, that.messageId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(connectionId, messageId);
        }
    }
}
