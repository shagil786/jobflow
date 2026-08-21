package dev.jobflow.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
@Import({JpaGmailConnectionStore.class, GmailConnectionOwnerLock.class})
class GmailConnectionPersistenceTest {
    @Autowired
    private GmailConnectionStore store;

    @Autowired
    private GmailConnectionRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private GmailConnectionOwnerLock ownerLock;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void persistsDistinctMailboxesAndReconnectsIdempotentlyAfterV6Migration() {
        GmailConnectionService service = new GmailConnectionService(
                store,
                new GmailTokenCipher() {
                    @Override public String encrypt(String value) { return "encrypted:" + value; }
                    @Override public String decrypt(String value) { return value.substring("encrypted:".length()); }
                },
                Clock.fixed(Instant.parse("2026-08-21T12:00:00Z"), ZoneOffset.UTC),
                ownerLock);

        GmailConnectionRecord first = service.connect(new GmailConnectionCommand(
                "user-1", "tenant-1", "first@gmail.com", "refresh-1", "history-1"));
        GmailConnectionRecord second = service.connect(new GmailConnectionCommand(
                "user-1", "tenant-1", "second@gmail.com", "refresh-2", "history-2"));
        GmailConnectionRecord otherOwner = service.connect(new GmailConnectionCommand(
                "user-2", "tenant-2", "FIRST@GMAIL.COM", "refresh-owner-2", "history-owner-2"));
        GmailConnectionRecord reconnected = service.connect(new GmailConnectionCommand(
                "user-1", "tenant-1", "FIRST@GMAIL.COM", "refresh-3", "history-3"));

        assertThat(first.connectionId()).isNotEqualTo(second.connectionId());
        assertThat(otherOwner.connectionId()).isNotEqualTo(first.connectionId());
        assertThat(reconnected.connectionId()).isEqualTo(first.connectionId());
        assertThat(repository.count()).isEqualTo(3);
        assertThat(service.status("tenant-1", "user-1").connectionId()).isEqualTo(first.connectionId());
        assertThat(jdbc.queryForObject("select count(*) from information_schema.indexes where table_name = 'GMAIL_CONNECTIONS' and index_name = 'UX_GMAIL_CONNECTION_OWNER'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from information_schema.indexes where table_name = 'GMAIL_CONNECTIONS' and index_name = 'UX_GMAIL_CONNECTION_MAILBOX'", Integer.class)).isEqualTo(1);
        assertThat(repository.findAllByTenantIdAndUserIdOrderByConnectedAtDesc("tenant-1", "user-1"))
                .extracting(GmailConnectionEntity::toModel)
                .extracting(StoredGmailConnection::email)
                .containsExactlyInAnyOrder("first@gmail.com", "second@gmail.com");
    }

    @Test
    void rejectsASecondDatabaseRowForTheSameNormalizedOwnerMailbox() {
        UUID connectionId = UUID.randomUUID();
        repository.saveAndFlush(new GmailConnectionEntity(new StoredGmailConnection(
                connectionId, "user-1", "tenant-1", "same@gmail.com", "encrypted:refresh", "history-1", null,
                Instant.parse("2026-08-21T12:00:00Z"), true)));

        assertThatThrownBy(() -> repository.saveAndFlush(new GmailConnectionEntity(new StoredGmailConnection(
                UUID.randomUUID(), "user-1", "tenant-1", "same@gmail.com", "encrypted:refresh-2", "history-2", null,
                Instant.parse("2026-08-21T12:00:00Z"), false))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void serializesConcurrentNewMailboxConnectsAndLeavesOneActiveRow() throws Exception {
        GmailConnectionService service = new GmailConnectionService(
                store,
                new GmailTokenCipher() {
                    @Override public String encrypt(String value) { return "encrypted:" + value; }
                    @Override public String decrypt(String value) { return value.substring("encrypted:".length()); }
                },
                Clock.fixed(Instant.parse("2026-08-21T12:00:00Z"), ZoneOffset.UTC),
                ownerLock);
        CountDownLatch start = new CountDownLatch(1);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        var executor = Executors.newFixedThreadPool(2);
        var first = executor.submit(() -> connectInTransaction(transaction, start, service, "first-race@gmail.com"));
        var second = executor.submit(() -> connectInTransaction(transaction, start, service, "second-race@gmail.com"));
        start.countDown();
        first.get(10, TimeUnit.SECONDS);
        second.get(10, TimeUnit.SECONDS);
        executor.shutdownNow();

        List<StoredGmailConnection> rows = repository.findAllByTenantIdAndUserIdOrderByConnectedAtDesc("race-tenant", "race-user")
                .stream().map(GmailConnectionEntity::toModel).toList();
        assertThat(rows).hasSize(2);
        assertThat(rows).filteredOn(StoredGmailConnection::active).hasSize(1);
        StoredGmailConnection active = rows.stream().filter(StoredGmailConnection::active).findFirst().orElseThrow();
        assertThat(service.status("race-tenant", "race-user").connectionId()).isEqualTo(active.connectionId());
    }

    private static void connectInTransaction(TransactionTemplate transaction, CountDownLatch start,
            GmailConnectionService service, String email) {
        try {
            start.await(10, TimeUnit.SECONDS);
            transaction.executeWithoutResult(status -> service.connect(new GmailConnectionCommand(
                    "race-user", "race-tenant", email, "refresh-" + email, "history-" + email)));
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(error);
        }
    }
}
