package dev.jobflow.ingestion;

import java.time.Instant;
import java.util.UUID;

record GmailMessageMetadata(UUID connectionId, String messageId, String threadId, Instant internalDate, String labelIds) {}
