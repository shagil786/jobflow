package dev.jobflow.ingestion;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GmailMessageRepository extends JpaRepository<GmailMessageEntity, String> {
    long countByConnectionId(UUID connectionId);
}
