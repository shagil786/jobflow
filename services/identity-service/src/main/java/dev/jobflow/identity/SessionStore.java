package dev.jobflow.identity;

import java.util.Optional;

public interface SessionStore {
    StoredSession save(StoredSession session);
    Optional<StoredSession> find(String sessionId);
    default Optional<StoredSession> findForUpdate(String sessionId) { return find(sessionId); }
}
