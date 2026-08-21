package dev.jobflow.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(JpaGmailThreadStore.class)
class GmailThreadStoreTest {
    @Autowired
    private GmailThreadStore store;

    @Autowired
    private GmailThreadRepository repository;

    @Autowired
    private GmailConnectionRepository connections;

    @Test
    void savesThreadsIdempotentlyAndReturnsTheCanonicalRecord() {
        UUID connectionId = UUID.randomUUID();
        connections.save(new GmailConnectionEntity(new StoredGmailConnection(
                connectionId,
                "user-1",
                "tenant-1",
                "user-1@example.com",
                "encrypted:refresh",
                "history-1",
                null,
                Instant.parse("2026-08-21T10:00:00Z"))));

        GmailThreadRecord first = store.saveIfAbsent(connectionId, "tenant-1", "user-1", "thread-1");
        GmailThreadRecord second = store.saveIfAbsent(connectionId, "tenant-1", "user-1", "thread-1");

        assertThat(first.connectionId()).isEqualTo(connectionId);
        assertThat(first.tenantId()).isEqualTo("tenant-1");
        assertThat(first.userId()).isEqualTo("user-1");
        assertThat(first.threadId()).isEqualTo("thread-1");
        assertThat(second).isEqualTo(first);
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void rejectsThreadOwnerMismatch() {
        UUID connectionId = UUID.randomUUID();
        connections.save(new GmailConnectionEntity(new StoredGmailConnection(
                connectionId, "user-1", "tenant-1", "user-1@example.com", "encrypted:refresh", "history-1", null,
                Instant.parse("2026-08-21T10:00:00Z"))));
        store.saveIfAbsent(connectionId, "tenant-1", "user-1", "thread-1");

        assertThatThrownBy(() -> store.saveIfAbsent(connectionId, "tenant-2", "user-2", "thread-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("gmail thread identity already belongs to a different owner");
        assertThat(repository.count()).isEqualTo(1);
    }
}
