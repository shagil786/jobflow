package dev.jobflow.identity;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

final class InMemorySessionStore implements SessionStore {
    private final Map<String, StoredSession> sessions = new HashMap<>();
    @Override public StoredSession save(StoredSession session) { sessions.put(session.sessionId(), session); return session; }
    @Override public Optional<StoredSession> find(String sessionId) { return Optional.ofNullable(sessions.get(sessionId)); }
    String rawAccessToken(String sessionId) { return sessions.get(sessionId).accessTokenCiphertext(); }
    String rawRefreshToken(String sessionId) { return sessions.get(sessionId).refreshTokenCiphertext(); }
}
