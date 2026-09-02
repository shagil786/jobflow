package dev.jobflow.contacts;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnrichmentRunRepository extends JpaRepository<EnrichmentRunEntity, UUID> {
  Optional<EnrichmentRunEntity> findByTenantIdAndUserIdAndApplicationIdAndIdempotencyKey(String tenantId, String userId, UUID applicationId, String idempotencyKey);
  Optional<EnrichmentRunEntity> findFirstByTenantIdAndUserIdAndApplicationIdOrderByCreatedAtDesc(String tenantId, String userId, UUID applicationId);
}
