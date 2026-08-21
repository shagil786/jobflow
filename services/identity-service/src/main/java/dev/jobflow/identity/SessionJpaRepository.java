package dev.jobflow.identity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface SessionJpaRepository extends JpaRepository<SessionEntity, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<SessionEntity> findBySessionId(String sessionId);
}
