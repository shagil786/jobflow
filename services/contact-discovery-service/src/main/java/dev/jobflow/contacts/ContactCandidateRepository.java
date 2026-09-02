package dev.jobflow.contacts;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContactCandidateRepository extends JpaRepository<ContactCandidateEntity, UUID> {
  List<ContactCandidateEntity> findByTenantIdAndUserIdAndApplicationIdOrderByConfidenceDescCreatedAtAsc(String tenantId, String userId, UUID applicationId);
  Optional<ContactCandidateEntity> findByTenantIdAndUserIdAndApplicationIdAndContentHash(String tenantId, String userId, UUID applicationId, String contentHash);
}
