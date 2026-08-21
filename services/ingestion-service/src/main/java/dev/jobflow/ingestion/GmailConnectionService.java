package dev.jobflow.ingestion;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
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
        StoredGmailConnection existing = store.findByOwner(command.tenantId(), command.userId()).orElse(null);
        UUID id = existing == null ? UUID.randomUUID() : existing.connectionId();
        store.save(new StoredGmailConnection(id, command.userId(), command.tenantId(), command.email(),
                cipher.encrypt(command.refreshToken()), command.historyId(), null, Instant.now(clock)));
        return new GmailConnectionRecord(id, command.email(), command.historyId(), null);
    }

    @Transactional
    public void advanceCursor(UUID connectionId, SyncCursor cursor) {
        StoredGmailConnection current = store.find(connectionId)
                .orElseThrow(UnknownGmailConnectionException::new);
        store.save(current.withCursor(cursor));
    }

    @Transactional(readOnly = true)
    public GmailConnectionStatus status(String tenantId, String userId) {
        return store.findByOwner(tenantId, userId)
                .map(connection -> new GmailConnectionStatus(connection.connectionId(), true, connection.email(), connection.connectedAt()))
                .orElse(new GmailConnectionStatus(null, false, null, null));
    }

    private static void require(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
    }
}
