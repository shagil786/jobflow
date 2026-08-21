package dev.jobflow.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "sessions", indexes = {
        @Index(name = "idx_sessions_user_id", columnList = "user_id"),
        @Index(name = "idx_sessions_revoked_at", columnList = "revoked_at")
})
public class SessionEntity {
    @Id
    @Column(length = 64, nullable = false, updatable = false)
    private String sessionId;
    @Column(name = "user_id", length = 255, nullable = false)
    private String userId;
    @Column(name = "tenant_id", length = 255, nullable = false)
    private String tenantId;
    @Column(length = 64, nullable = false)
    private String provider;
    @Column(name = "access_token_ciphertext", length = 8192, nullable = false)
    private String accessTokenCiphertext;
    @Column(name = "refresh_token_ciphertext", length = 8192)
    private String refreshTokenCiphertext;
    @Column(name = "access_token_expires_at", nullable = false)
    private Instant accessTokenExpiresAt;
    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected SessionEntity() {}

    SessionEntity(StoredSession session) { update(session); }

    void update(StoredSession session) {
        this.sessionId = session.sessionId();
        this.userId = session.userId();
        this.tenantId = session.tenantId();
        this.provider = session.provider();
        this.accessTokenCiphertext = session.accessTokenCiphertext();
        this.refreshTokenCiphertext = session.refreshTokenCiphertext();
        this.accessTokenExpiresAt = session.accessTokenExpiresAt();
        this.revokedAt = session.revokedAt();
    }

    StoredSession toModel() {
        return new StoredSession(sessionId, userId, tenantId, provider, accessTokenCiphertext,
                refreshTokenCiphertext, accessTokenExpiresAt, revokedAt);
    }
}
