package dev.jobflow.identity;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

public class SessionService {
    private final SessionStore store;
    private final TokenCipher cipher;
    private final TokenRefresher refresher;
    private final Clock clock;

    public SessionService(SessionStore store, TokenCipher cipher, TokenRefresher refresher, Clock clock) {
        this.store = store;
        this.cipher = cipher;
        this.refresher = refresher;
        this.clock = clock;
    }

    public SessionRecord create(CreateSessionCommand command) {
        if (command.accessToken() == null || command.accessToken().isBlank() || command.accessTokenExpiresAt() == null) {
            throw new IllegalArgumentException("an access token and expiry are required");
        }
        String sessionId = UUID.randomUUID().toString();
        store.save(new StoredSession(sessionId, command.userId(), command.tenantId(), command.provider(),
                cipher.encrypt(command.accessToken()),
                command.refreshToken() == null ? null : cipher.encrypt(command.refreshToken()),
                command.accessTokenExpiresAt(), null));
        return new SessionRecord(sessionId, command.userId(), command.tenantId(), command.provider(), command.accessTokenExpiresAt());
    }

    @Transactional
    public AccessTokenResult accessToken(String sessionId) {
        StoredSession session = store.findForUpdate(sessionId).filter(s -> s.revokedAt() == null)
                .orElseThrow(() -> new SessionNotFoundException(sessionId));
        Instant now = Instant.now(clock);
        if (session.accessTokenExpiresAt().isAfter(now)) {
            return new AccessTokenResult(cipher.decrypt(session.accessTokenCiphertext()), session.accessTokenExpiresAt());
        }
        if (session.refreshTokenCiphertext() == null) {
            store.save(session.revoke(now));
            throw new SessionNotFoundException(sessionId);
        }
        try {
            RefreshedTokens refreshed = refresher.refresh(cipher.decrypt(session.refreshTokenCiphertext()));
            if (refreshed.accessToken() == null || refreshed.accessToken().isBlank()
                    || refreshed.expiresInSeconds() <= 0
                    || (refreshed.refreshToken() != null && refreshed.refreshToken().isBlank())) {
                throw new IllegalStateException("provider returned invalid rotated tokens");
            }
            Instant expiresAt = now.plusSeconds(refreshed.expiresInSeconds());
            String refreshToken = refreshed.refreshToken() == null ? cipher.decrypt(session.refreshTokenCiphertext()) : refreshed.refreshToken();
            store.save(session.rotate(cipher.encrypt(refreshed.accessToken()), cipher.encrypt(refreshToken), expiresAt));
            return new AccessTokenResult(refreshed.accessToken(), expiresAt);
        } catch (RuntimeException failure) {
            store.save(session.revoke(now));
            throw new SessionNotFoundException(sessionId);
        }
    }

    public void revoke(String sessionId) {
        store.find(sessionId).ifPresent(session -> store.save(session.revoke(Instant.now(clock))));
    }

    public SessionMetadata metadata(String sessionId) {
        StoredSession session = store.find(sessionId).filter(s -> s.revokedAt() == null)
                .orElseThrow(() -> new SessionNotFoundException(sessionId));
        return new SessionMetadata(session.userId(), session.tenantId());
    }
}
