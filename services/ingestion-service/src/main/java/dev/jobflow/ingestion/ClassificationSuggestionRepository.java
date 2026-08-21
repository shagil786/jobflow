package dev.jobflow.ingestion;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClassificationSuggestionRepository extends JpaRepository<ClassificationSuggestionEntity, Long> {
    Optional<ClassificationSuggestionEntity> findByConnectionIdAndMessageIdAndClassifierVersionAndContentHash(
            UUID connectionId, String messageId, String classifierVersion, String contentHash);

    Optional<ClassificationSuggestionEntity> findTopByTenantIdAndUserIdAndConnectionIdAndMessageIdOrderByCreatedAtDescRowIdDesc(
            String tenantId, String userId, UUID connectionId, String messageId);
}
