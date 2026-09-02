package dev.jobflow.jobs;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationRepository extends JpaRepository<ApplicationEntity, UUID> {
  List<ApplicationEntity> findByTenantIdAndUserIdOrderByUpdatedAtDesc(String tenantId, String userId);
  List<ApplicationEntity> findByTenantIdAndUserId(String tenantId, String userId);
  Optional<ApplicationEntity> findByTenantIdAndUserIdAndIdempotencyKey(String tenantId, String userId, String idempotencyKey);
  Optional<ApplicationEntity> findByTenantIdAndUserIdAndId(String tenantId, String userId, UUID id);
}
