package dev.jobflow.ai;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
interface DraftRepository extends JpaRepository<DraftEntity,UUID> { Optional<DraftEntity> findByIdAndTenantIdAndUserId(UUID id,String tenantId,String userId); Optional<DraftEntity> findByTenantIdAndUserIdAndApplicationIdAndContactIdAndResumeVersionId(String tenantId,String userId,String applicationId,String contactId,String resumeVersionId); }
