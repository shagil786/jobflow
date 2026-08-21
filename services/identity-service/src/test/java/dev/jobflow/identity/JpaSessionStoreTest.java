package dev.jobflow.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class JpaSessionStoreTest {
    @Autowired
    private SessionJpaRepository repository;

    @Test
    void persistsAndLoadsEncryptedSessionMaterial() {
        JpaSessionStore store = new JpaSessionStore(repository);
        StoredSession expected = new StoredSession("session-1", "user-1", "tenant-1", "google",
                "ciphertext-access", "ciphertext-refresh", Instant.parse("2026-08-21T11:00:00Z"), null);

        store.save(expected);

        assertThat(store.find("session-1")).contains(expected);
        assertThat(repository.findById("session-1")).get().extracting(SessionEntity::toModel).isEqualTo(expected);
    }
}
