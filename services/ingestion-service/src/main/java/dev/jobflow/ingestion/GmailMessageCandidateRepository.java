package dev.jobflow.ingestion;

import java.util.Optional;
import java.util.UUID;

interface GmailMessageCandidateRepository {
    Optional<GmailMessageCandidateEntity> findByConnectionIdAndProviderMessageId(UUID connectionId, String providerMessageId);
    default Optional<GmailMessageCandidateEntity> findByConnectionIdAndThreadId(UUID connectionId, String threadId) { return Optional.empty(); }
    <S extends GmailMessageCandidateEntity> S save(S entity);
}
