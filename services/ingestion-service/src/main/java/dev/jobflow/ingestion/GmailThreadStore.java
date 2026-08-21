package dev.jobflow.ingestion;

import java.time.Instant;
import java.util.UUID;

public interface GmailThreadStore {
    GmailThreadRecord saveIfAbsent(UUID connectionId, String tenantId, String userId, String threadId);
}

record GmailThreadRecord(
        UUID connectionId,
        String tenantId,
        String userId,
        String threadId,
        Instant firstSeenAt,
        Instant lastSeenAt) {}
