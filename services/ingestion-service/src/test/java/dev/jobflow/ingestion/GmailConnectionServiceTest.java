package dev.jobflow.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GmailConnectionServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-21T12:00:00Z");

    @Test
    void storesOnlyEncryptedRefreshMaterialAndReturnsOpaqueConnectionMetadata() {
        InMemoryGmailConnectionStore store = new InMemoryGmailConnectionStore();
        GmailConnectionService service = new GmailConnectionService(store, new TestCipher(), Clock.fixed(NOW, ZoneOffset.UTC));

        GmailConnectionRecord record = service.connect(new GmailConnectionCommand(
                "user-1", "tenant-1", "gmail-user@example.com", "refresh-token", "history-7"));

        assertThat(record.connectionId()).isNotNull();
        assertThat(record.email()).isEqualTo("gmail-user@example.com");
        assertThat(store.rawRefreshToken(record.connectionId())).isNotEqualTo("refresh-token");
        assertThat(store.find(record.connectionId()).orElseThrow().lastHistoryId()).isEqualTo("history-7");
    }

    @Test
    void updatesCursorWithoutReplacingTheEncryptedRefreshToken() {
        InMemoryGmailConnectionStore store = new InMemoryGmailConnectionStore();
        GmailConnectionService service = new GmailConnectionService(store, new TestCipher(), Clock.fixed(NOW, ZoneOffset.UTC));
        GmailConnectionRecord record = service.connect(new GmailConnectionCommand(
                "user-1", "tenant-1", "gmail-user@example.com", "refresh-token", "history-7"));
        String ciphertext = store.rawRefreshToken(record.connectionId());

        service.advanceCursor(record.connectionId(), new SyncCursor("history-8", "page-2"));

        assertThat(store.rawRefreshToken(record.connectionId())).isEqualTo(ciphertext);
        assertThat(store.find(record.connectionId()).orElseThrow().pageToken()).isEqualTo("page-2");
    }

    @Test
    void reportsMissingConnectionsDuringCursorUpdatesAsClientErrors() {
        InMemoryGmailConnectionStore store = new InMemoryGmailConnectionStore();
        GmailConnectionService service = new GmailConnectionService(store, new TestCipher(), Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> service.advanceCursor(UUID.fromString("00000000-0000-0000-0000-000000000123"), new SyncCursor("history-8", "page-2")))
                .isInstanceOf(UnknownGmailConnectionException.class)
                .hasMessage("Gmail connection not found");
    }

    @Test
    void givesAReconnectToADifferentGmailMailboxANewConnectionIdentity() {
        InMemoryGmailConnectionStore store = new InMemoryGmailConnectionStore();
        GmailConnectionService service = new GmailConnectionService(store, new TestCipher(), Clock.fixed(NOW, ZoneOffset.UTC));

        GmailConnectionRecord first = service.connect(new GmailConnectionCommand(
                "user-1", "tenant-1", "first@gmail.com", "refresh-token-1", "history-1"));
        GmailConnectionRecord second = service.connect(new GmailConnectionCommand(
                "user-1", "tenant-1", "second@gmail.com", "refresh-token-2", "history-2"));

        assertThat(second.connectionId()).isNotEqualTo(first.connectionId());
        assertThat(store.find(first.connectionId()).orElseThrow().email()).isEqualTo("first@gmail.com");
        assertThat(store.find(second.connectionId()).orElseThrow().email()).isEqualTo("second@gmail.com");
    }

    @Test
    void reusesTheConnectionIdentityForTheSameGmailMailbox() {
        InMemoryGmailConnectionStore store = new InMemoryGmailConnectionStore();
        GmailConnectionService service = new GmailConnectionService(store, new TestCipher(), Clock.fixed(NOW, ZoneOffset.UTC));

        GmailConnectionRecord first = service.connect(new GmailConnectionCommand(
                "user-1", "tenant-1", "first@gmail.com", "refresh-token-1", "history-1"));
        GmailConnectionRecord reconnected = service.connect(new GmailConnectionCommand(
                "user-1", "tenant-1", "FIRST@gmail.com", "refresh-token-2", "history-2"));

        assertThat(reconnected.connectionId()).isEqualTo(first.connectionId());
        assertThat(store.find(first.connectionId()).orElseThrow().lastHistoryId()).isEqualTo("history-2");
    }

    @Test
    void selectsTheNewlyConnectedMailboxWhenConnectedAtTimestampsTie() {
        InMemoryGmailConnectionStore store = new InMemoryGmailConnectionStore();
        GmailConnectionService service = new GmailConnectionService(store, new TestCipher(), Clock.fixed(NOW, ZoneOffset.UTC));

        service.connect(new GmailConnectionCommand("user-1", "tenant-1", "first@gmail.com", "refresh-1", "history-1"));
        GmailConnectionRecord second = service.connect(new GmailConnectionCommand("user-1", "tenant-1", "second@gmail.com", "refresh-2", "history-2"));

        assertThat(service.status("tenant-1", "user-1").connectionId()).isEqualTo(second.connectionId());
        assertThat(service.status("tenant-1", "user-1").email()).isEqualTo("second@gmail.com");
    }

    @Test
    void recoversAnOwnerConnectionWhenTheLegacyActiveFlagIsFalse() {
        InMemoryGmailConnectionStore store = new InMemoryGmailConnectionStore();
        GmailConnectionService service = new GmailConnectionService(store, new TestCipher(), Clock.fixed(NOW, ZoneOffset.UTC));
        UUID connectionId = UUID.randomUUID();
        store.save(new StoredGmailConnection(connectionId, "user-1", "tenant-1", "gmail-user@example.com",
                "encrypted:refresh", "history-1", null, NOW, false));

        assertThat(service.status("tenant-1", "user-1").connectionId()).isEqualTo(connectionId);
        assertThat(service.status("tenant-1", "user-1").connected()).isTrue();
    }

    private static final class TestCipher implements GmailTokenCipher {
        @Override public String encrypt(String value) { return "encrypted:" + value; }
        @Override public String decrypt(String value) { return value.substring("encrypted:".length()); }
    }

    private static final class InMemoryGmailConnectionStore implements GmailConnectionStore {
        private final Map<UUID, StoredGmailConnection> values = new HashMap<>();
        @Override public StoredGmailConnection save(StoredGmailConnection value) { values.put(value.connectionId(), value); return value; }
        @Override public Optional<StoredGmailConnection> find(UUID id) { return Optional.ofNullable(values.get(id)); }
        @Override public Optional<StoredGmailConnection> findByOwner(String tenantId, String userId) { return values.values().stream().filter(v -> v.tenantId().equals(tenantId) && v.userId().equals(userId)).findFirst(); }
        @Override public Optional<StoredGmailConnection> findByOwnerAndEmail(String tenantId, String userId, String email) { return values.values().stream().filter(v -> v.tenantId().equals(tenantId) && v.userId().equals(userId) && v.email().equalsIgnoreCase(email)).findFirst(); }
        @Override public List<StoredGmailConnection> findAllByOwner(String tenantId, String userId) { return values.values().stream().filter(v -> v.tenantId().equals(tenantId) && v.userId().equals(userId)).sorted((a, b) -> b.connectedAt().compareTo(a.connectedAt())).toList(); }
        @Override public StoredGmailConnection saveAsActive(StoredGmailConnection value) { values.replaceAll((id, existing) -> existing.tenantId().equals(value.tenantId()) && existing.userId().equals(value.userId()) ? existing.withActive(false) : existing); return save(value.withActive(true)); }
        @Override public Optional<StoredGmailConnection> findActiveByOwner(String tenantId, String userId) { return values.values().stream().filter(v -> v.active() && v.tenantId().equals(tenantId) && v.userId().equals(userId)).findFirst(); }
        String rawRefreshToken(UUID id) { return values.get(id).refreshTokenCiphertext(); }
    }
}
