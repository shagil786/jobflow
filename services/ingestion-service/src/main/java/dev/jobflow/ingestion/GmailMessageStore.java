package dev.jobflow.ingestion;

import java.util.UUID;

public interface GmailMessageStore {
    boolean saveIfAbsent(GmailMessageMetadata message);
    long countForConnection(UUID connectionId);
}
