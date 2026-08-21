package dev.jobflow.ingestion;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.List;
import java.util.Locale;
import org.springframework.transaction.annotation.Transactional;

public class GmailConnectionService {
    private final GmailConnectionStore store;
    private final GmailTokenCipher cipher;
    private final Clock clock;

    public GmailConnectionService(GmailConnectionStore store, GmailTokenCipher cipher, Clock clock) {
        this.store = store;
        this.cipher = cipher;
        this.clock = clock;
    }

    @Transactional
    public GmailConnectionRecord connect(GmailConnectionCommand command) {
        require(command.userId(), "userId"); require(command.tenantId(), "tenantId");
        require(command.email(), "email"); require(command.refreshToken(), "refreshToken"); require(command.historyId(), "historyId");
        String email = normalizeEmail(command.email());
        StoredGmailConnection existing = store.findByOwnerAndEmail(command.tenantId(), command.userId(), email).orElse(null);
        UUID id = existing == null ? UUID.randomUUID() : existing.connectionId();
        store.save(new StoredGmailConnection(id, command.userId(), command.tenantId(), email,
                cipher.encrypt(command.refreshToken()), command.historyId(), null, Instant.now(clock)));
        return new GmailConnectionRecord(id, email, command.historyId(), null);
    }

    @Transactional
    public void advanceCursor(UUID connectionId, SyncCursor cursor) {
        StoredGmailConnection current = store.find(connectionId)
                .orElseThrow(UnknownGmailConnectionException::new);
        store.save(current.withCursor(cursor));
    }

    @Transactional(readOnly = true)
    public GmailConnectionStatus status(String tenantId, String userId) {
        List<StoredGmailConnection> connections = store.findAllByOwner(tenantId, userId);
        if (connections.isEmpty()) {
            return new GmailConnectionStatus(null, false, null, null);
        }
        StoredGmailConnection latest = connections.get(0);
        return new GmailConnectionStatus(latest.connectionId(), true, latest.email(), latest.connectedAt());
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static void require(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
    }
}
