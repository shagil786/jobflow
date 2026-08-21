package dev.jobflow.ingestion;

import java.util.Optional;
import java.util.UUID;

interface GmailMessageCandidateRepository {
    Optional<GmailMessageCandidateEntity> findByConnectionIdAndProviderMessageId(UUID connectionId, String providerMessageId);
    <S extends GmailMessageCandidateEntity> S save(S entity);
}
