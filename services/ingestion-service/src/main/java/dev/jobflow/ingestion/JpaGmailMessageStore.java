package dev.jobflow.ingestion;

import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class JpaGmailMessageStore implements GmailMessageStore {
    private final GmailMessageRepository repository;
    public JpaGmailMessageStore(GmailMessageRepository repository) { this.repository = repository; }
    @Override public boolean saveIfAbsent(GmailMessageMetadata message) {
        if (repository.existsById(message.messageId())) return false;
        repository.save(new GmailMessageEntity(message));
        return true;
    }
    @Override public long countForConnection(UUID connectionId) { return repository.countByConnectionId(connectionId); }
}
