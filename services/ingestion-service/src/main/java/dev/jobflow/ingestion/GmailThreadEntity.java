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
@Table(name = "gmail_threads")
public class GmailThreadEntity {
    @EmbeddedId
    private GmailThreadId id;

    @Column(nullable = false, length = 120)
    private String tenantId;

    @Column(nullable = false, length = 120)
    private String userId;

    @Column(nullable = false)
    private Instant firstSeenAt;

    @Column(nullable = false)
    private Instant lastSeenAt;

    protected GmailThreadEntity() {}

    GmailThreadEntity(UUID connectionId, String tenantId, String userId, String threadId) {
        Instant now = Instant.now();
        id = new GmailThreadId(connectionId, threadId);
        this.tenantId = tenantId;
        this.userId = userId;
        firstSeenAt = now;
        lastSeenAt = now;
    }

    boolean hasSameOwner(String tenantId, String userId) {
        return Objects.equals(this.tenantId, tenantId) && Objects.equals(this.userId, userId);
    }

    GmailThreadRecord toRecord() {
        return new GmailThreadRecord(id.connectionId(), tenantId, userId, id.threadId(), firstSeenAt, lastSeenAt);
    }

    @Embeddable
    public static class GmailThreadId implements Serializable {
        @Column(name = "connection_id", nullable = false)
        private UUID connectionId;

        @Column(name = "thread_id", nullable = false, length = 255)
        private String threadId;

        protected GmailThreadId() {}

        GmailThreadId(UUID connectionId, String threadId) {
            this.connectionId = connectionId;
            this.threadId = threadId;
        }

        UUID connectionId() {
            return connectionId;
        }

        String threadId() {
            return threadId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof GmailThreadId that)) {
                return false;
            }
            return Objects.equals(connectionId, that.connectionId) && Objects.equals(threadId, that.threadId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(connectionId, threadId);
        }
    }
}
