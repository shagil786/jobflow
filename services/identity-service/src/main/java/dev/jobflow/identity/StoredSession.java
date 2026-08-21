package dev.jobflow.identity;

import java.time.Instant;

public record StoredSession(String sessionId, String userId, String tenantId, String provider,
        String accessTokenCiphertext, String refreshTokenCiphertext, Instant accessTokenExpiresAt, Instant revokedAt) {
    public StoredSession revoke(Instant at) {
        return new StoredSession(sessionId, userId, tenantId, provider, accessTokenCiphertext,
                refreshTokenCiphertext, accessTokenExpiresAt, at);
    }
    public StoredSession rotate(String accessCiphertext, String refreshCiphertext, Instant expiresAt) {
        return new StoredSession(sessionId, userId, tenantId, provider, accessCiphertext,
                refreshCiphertext, expiresAt, revokedAt);
    }
}
