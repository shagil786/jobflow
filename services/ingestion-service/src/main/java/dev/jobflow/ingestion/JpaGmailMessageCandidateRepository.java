package dev.jobflow.ingestion;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
interface JpaGmailMessageCandidateRepository extends JpaRepository<GmailMessageCandidateEntity, UUID>, GmailMessageCandidateRepository {
    @Override
    Optional<GmailMessageCandidateEntity> findByConnectionIdAndProviderMessageId(UUID connectionId, String providerMessageId);

    @Override
    Optional<GmailMessageCandidateEntity> findByConnectionIdAndThreadId(UUID connectionId, String threadId);
}
