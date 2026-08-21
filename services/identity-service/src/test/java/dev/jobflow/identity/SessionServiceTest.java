package dev.jobflow.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class SessionServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-21T10:00:00Z");

    @Test
    void createsAnOpaqueSessionAndCanReadItBeforeExpiry() {
        InMemorySessionStore store = new InMemorySessionStore();
        SessionService service = new SessionService(
                store,
                new TestTokenCipher(),
                new FailingTokenRefresher(),
                Clock.fixed(NOW, ZoneOffset.UTC));

        SessionRecord created = service.create(new CreateSessionCommand(
                "user-1", "tenant-1", "google", "access-1", "refresh-1", NOW.plusSeconds(3600)));

        assertThat(created.sessionId()).isNotEqualTo("access-1");
        assertThat(service.accessToken(created.sessionId()).accessToken()).isEqualTo("access-1");
        assertThat(store.rawAccessToken(created.sessionId())).isNotEqualTo("access-1");
    }

    @Test
    void rotatesAccessAndRefreshTokensAfterExpiry() {
        InMemorySessionStore store = new InMemorySessionStore();
        RotatingTokenRefresher refresher = new RotatingTokenRefresher();
        SessionService service = new SessionService(
                store,
                new TestTokenCipher(),
                refresher,
                Clock.fixed(NOW.plusSeconds(3601), ZoneOffset.UTC));

        SessionRecord created = service.create(new CreateSessionCommand(
                "user-1", "tenant-1", "google", "access-1", "refresh-1", NOW.plusSeconds(3600)));

        AccessTokenResult result = service.accessToken(created.sessionId());

        assertThat(result.accessToken()).isEqualTo("access-2");
        assertThat(result.expiresAt()).isEqualTo(NOW.plusSeconds(7201));
        assertThat(store.rawRefreshToken(created.sessionId())).isNotEqualTo("refresh-1");
        assertThat(refresher.receivedRefreshToken()).isEqualTo("refresh-1");
    }

    @Test
    void revocationPreventsAccessAndUnknownSessionsFailClosed() {
        InMemorySessionStore store = new InMemorySessionStore();
        SessionService service = new SessionService(
                store,
                new TestTokenCipher(),
                new FailingTokenRefresher(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        SessionRecord created = service.create(new CreateSessionCommand(
                "user-1", "tenant-1", "google", "access-1", "refresh-1", NOW.plusSeconds(3600)));

        service.revoke(created.sessionId());

        assertThatThrownBy(() -> service.accessToken(created.sessionId()))
                .isInstanceOf(SessionNotFoundException.class);
        assertThatThrownBy(() -> service.accessToken(UUID.randomUUID().toString()))
                .isInstanceOf(SessionNotFoundException.class);
    }

    private static final class FailingTokenRefresher implements TokenRefresher {
        @Override
        public RefreshedTokens refresh(String refreshToken) {
            throw new AssertionError("refresh should not be called");
        }
    }

    private static final class RotatingTokenRefresher implements TokenRefresher {
        private String receivedRefreshToken;

        @Override
        public RefreshedTokens refresh(String refreshToken) {
            receivedRefreshToken = refreshToken;
            return new RefreshedTokens("access-2", "refresh-2", 3600);
        }

        String receivedRefreshToken() {
            return receivedRefreshToken;
        }
    }

    private static final class TestTokenCipher implements TokenCipher {
        @Override
        public String encrypt(String plaintext) {
            return "ciphertext:" + plaintext;
        }

        @Override
        public String decrypt(String ciphertext) {
            return ciphertext.substring("ciphertext:".length());
        }
    }
}
