package dev.jobflow.ingestion;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ClassificationReviewRepository extends JpaRepository<ClassificationReviewEntity, UUID> {
    Optional<ClassificationReviewEntity> findByTenantIdAndUserIdAndSuggestionId(String tenantId, String userId, String suggestionId);
    List<ClassificationReviewEntity> findAllByTenantIdAndUserId(String tenantId, String userId);
}
