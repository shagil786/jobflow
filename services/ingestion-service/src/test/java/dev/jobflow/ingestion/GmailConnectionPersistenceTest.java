package dev.jobflow.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(JpaGmailConnectionStore.class)
class GmailConnectionPersistenceTest {
    @Autowired
    private GmailConnectionStore store;

    @Autowired
    private GmailConnectionRepository repository;

    @Test
    void persistsDistinctMailboxesAndReconnectsIdempotentlyAfterV6Migration() {
        GmailConnectionService service = new GmailConnectionService(
                store,
                new GmailTokenCipher() {
                    @Override public String encrypt(String value) { return "encrypted:" + value; }
                    @Override public String decrypt(String value) { return value.substring("encrypted:".length()); }
                },
                Clock.fixed(Instant.parse("2026-08-21T12:00:00Z"), ZoneOffset.UTC));

        GmailConnectionRecord first = service.connect(new GmailConnectionCommand(
                "user-1", "tenant-1", "first@gmail.com", "refresh-1", "history-1"));
        GmailConnectionRecord second = service.connect(new GmailConnectionCommand(
                "user-1", "tenant-1", "second@gmail.com", "refresh-2", "history-2"));
        GmailConnectionRecord reconnected = service.connect(new GmailConnectionCommand(
                "user-1", "tenant-1", "FIRST@GMAIL.COM", "refresh-3", "history-3"));

        assertThat(first.connectionId()).isNotEqualTo(second.connectionId());
        assertThat(reconnected.connectionId()).isEqualTo(first.connectionId());
        assertThat(repository.count()).isEqualTo(2);
        assertThat(java.util.List.of(first.connectionId(), second.connectionId()))
                .contains(service.status("tenant-1", "user-1").connectionId());
        assertThat(repository.findAllByTenantIdAndUserIdOrderByConnectedAtDesc("tenant-1", "user-1"))
                .extracting(GmailConnectionEntity::toModel)
                .extracting(StoredGmailConnection::email)
                .containsExactlyInAnyOrder("first@gmail.com", "second@gmail.com");
    }
}
