package dev.jobflow.identity;

import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class JpaSessionStore implements SessionStore {
    private final SessionJpaRepository repository;

    public JpaSessionStore(SessionJpaRepository repository) { this.repository = repository; }

    @Override
    public StoredSession save(StoredSession session) {
        SessionEntity entity = repository.findById(session.sessionId()).orElseGet(() -> new SessionEntity(session));
        entity.update(session);
        return repository.save(entity).toModel();
    }

    @Override
    public Optional<StoredSession> find(String sessionId) {
        return repository.findById(sessionId).map(SessionEntity::toModel);
    }

    @Override
    public Optional<StoredSession> findForUpdate(String sessionId) {
        return repository.findBySessionId(sessionId).map(SessionEntity::toModel);
    }
}
