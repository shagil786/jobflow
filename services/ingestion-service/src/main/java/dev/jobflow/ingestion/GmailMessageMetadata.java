package dev.jobflow.ingestion;

import java.time.Instant;
import java.util.UUID;

record GmailMessageMetadata(
        UUID connectionId,
        String tenantId,
        String userId,
        String messageId,
        String threadId,
        String sender,
        String replyTo,
        String recipients,
        String subject,
        Instant receivedAt,
        String labelIds,
        String normalizedContentHash) {}
