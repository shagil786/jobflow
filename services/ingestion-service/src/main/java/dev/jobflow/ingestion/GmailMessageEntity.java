package dev.jobflow.ingestion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "gmail_messages")
public class GmailMessageEntity {
    @Id @Column(length = 255) private String messageId;
    @Column(nullable = false) private UUID connectionId;
    @Column(nullable = false, length = 255) private String threadId;
    private Instant internalDate;
    @Column(length = 2048) private String labelIds;
    @Column(nullable = false) private Instant capturedAt;
    protected GmailMessageEntity() {}
    GmailMessageEntity(GmailMessageMetadata value) { update(value); }
    void update(GmailMessageMetadata value) { messageId=value.messageId(); connectionId=value.connectionId(); threadId=value.threadId(); internalDate=value.internalDate(); labelIds=value.labelIds(); if(capturedAt==null) capturedAt=Instant.now(); }
}
