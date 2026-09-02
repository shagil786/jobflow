package dev.jobflow.ai;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
interface ResumeVersionRepository extends JpaRepository<ResumeVersionEntity,UUID> { List<ResumeVersionEntity> findByTenantIdAndUserIdOrderByCreatedAtDesc(String tenantId,String userId); Optional<ResumeVersionEntity> findByIdAndTenantIdAndUserId(UUID id,String tenantId,String userId); }
