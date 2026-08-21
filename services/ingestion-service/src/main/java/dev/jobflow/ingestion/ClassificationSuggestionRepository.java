package dev.jobflow.ingestion;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClassificationSuggestionRepository extends JpaRepository<ClassificationSuggestionEntity, Long> {
    List<ClassificationSuggestionEntity> findAllByTenantIdAndUserIdAndRequiresReviewTrueOrderByRowIdDesc(String tenantId, String userId);
    Optional<ClassificationSuggestionEntity> findByTenantIdAndUserIdAndSuggestionId(String tenantId, String userId, String suggestionId);
    Optional<ClassificationSuggestionEntity> findByTenantIdAndUserIdAndConnectionIdAndMessageIdAndClassifierVersionAndContentHash(
            String tenantId, String userId, UUID connectionId, String messageId, String classifierVersion, String contentHash);

    Optional<ClassificationSuggestionEntity> findTopByTenantIdAndUserIdAndConnectionIdAndMessageIdOrderByRowIdDesc(
            String tenantId, String userId, UUID connectionId, String messageId);
}
