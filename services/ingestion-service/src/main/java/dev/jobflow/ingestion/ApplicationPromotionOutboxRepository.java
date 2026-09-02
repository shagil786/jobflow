package dev.jobflow.ingestion;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

interface ApplicationPromotionOutboxRepository extends JpaRepository<ApplicationPromotionOutboxEntity, UUID> {
    Optional<ApplicationPromotionOutboxEntity> findBySuggestionId(String suggestionId);
    List<ApplicationPromotionOutboxEntity> findTop100ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(PromotionOutboxStatus status, Instant now);

    @Modifying @Transactional
    @Query("update GmailBackfillBatchEntity b set b.autoPromoted = b.autoPromoted + 1, b.updatedAt = :now where b.batchId = :batchId")
    int recordPromotionSuccess(@Param("batchId") UUID batchId, @Param("now") Instant now);
}
