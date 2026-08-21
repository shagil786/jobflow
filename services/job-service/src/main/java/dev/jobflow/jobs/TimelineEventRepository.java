package dev.jobflow.jobs;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TimelineEventRepository extends JpaRepository<TimelineEventEntity, UUID> {
  List<TimelineEventEntity> findByTenantIdAndUserIdAndApplicationIdOrderByOccurredAtAsc(String tenantId, String userId, UUID applicationId);
  boolean existsByTenantIdAndUserIdAndApplicationIdAndTypeAndSummary(String tenantId, String userId, UUID applicationId, String type, String summary);
}
