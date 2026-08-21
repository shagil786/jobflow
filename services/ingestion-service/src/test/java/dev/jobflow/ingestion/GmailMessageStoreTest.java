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
@Import(JpaGmailMessageStore.class)
class GmailMessageStoreTest {
    @Autowired
    private GmailMessageStore store;

    @Autowired
    private GmailMessageRepository repository;

    @Autowired
    private GmailConnectionRepository connections;

    @Test
    void savesMessagesIdempotentlyWithinTheirProviderIdentity() {
        UUID connectionId = UUID.randomUUID();
        insertConnection(connectionId, "tenant-1", "user-1");
        GmailMessageMetadata first = metadata(connectionId, "tenant-1", "user-1", "message-1", "thread-1");

        assertThat(store.saveIfAbsent(first)).isTrue();
        assertThat(store.saveIfAbsent(first)).isFalse();
        assertThat(store.findByProviderIdentity("tenant-1", "user-1", connectionId, "message-1")).contains(first);
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void doesNotLeakMessagesAcrossTenantBoundaries() {
        UUID firstConnectionId = UUID.randomUUID();
        UUID secondConnectionId = UUID.randomUUID();
        insertConnection(firstConnectionId, "tenant-1", "user-1");
        insertConnection(secondConnectionId, "tenant-2", "user-2");
        GmailMessageMetadata firstTenant = metadata(firstConnectionId, "tenant-1", "user-1", "message-1", "thread-1");
        GmailMessageMetadata secondTenant = metadata(secondConnectionId, "tenant-2", "user-2", "message-1", "thread-2");

        assertThat(store.saveIfAbsent(firstTenant)).isTrue();
        assertThat(store.saveIfAbsent(secondTenant)).isTrue();

        assertThat(store.findByProviderIdentity("tenant-1", "user-1", firstTenant.connectionId(), "message-1")).contains(firstTenant);
        assertThat(store.findByProviderIdentity("tenant-1", "user-1", secondTenant.connectionId(), "message-1")).isEmpty();
    }

    @Test
    void rejectsReusingAProviderIdentityForADifferentOwner() {
        UUID connectionId = UUID.randomUUID();
        insertConnection(connectionId, "tenant-1", "user-1");
        GmailMessageMetadata first = metadata(connectionId, "tenant-1", "user-1", "message-1", "thread-1");
        GmailMessageMetadata mismatchedOwner = metadata(connectionId, "tenant-2", "user-2", "message-1", "thread-1");

        assertThat(store.saveIfAbsent(first)).isTrue();

        assertThatThrownBy(() -> store.saveIfAbsent(mismatchedOwner))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("gmail message identity already belongs to a different owner");
    }

    @Test
    void rejectsNullOrBlankOwnersBeforePersistence() {
        UUID connectionId = UUID.randomUUID();
        insertConnection(connectionId, "tenant-1", "user-1");

        assertThatThrownBy(() -> store.saveIfAbsent(metadata(connectionId, null, "user-1", "message-null", "thread-1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("tenantId must not be null or blank");
        assertThatThrownBy(() -> store.saveIfAbsent(metadata(connectionId, " ", "user-1", "message-blank", "thread-1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("tenantId must not be null or blank");
        assertThatThrownBy(() -> store.saveIfAbsent(metadata(connectionId, "tenant-1", null, "message-null-user", "thread-1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("userId must not be null or blank");
        assertThat(repository.count()).isZero();
    }

    private static GmailMessageMetadata metadata(UUID connectionId, String tenantId, String userId, String messageId, String threadId) {
        return new GmailMessageMetadata(
                connectionId,
                tenantId,
                userId,
                messageId,
                threadId,
                "sender@example.com",
                "reply-to@example.com",
                "one@example.com,two@example.com",
                "Subject",
                Instant.parse("2026-08-21T12:00:00Z"),
                "Label_JobFlowTrack",
                "hash-" + messageId);
    }

    private void insertConnection(UUID connectionId, String tenantId, String userId) {
        connections.save(new GmailConnectionEntity(new StoredGmailConnection(
                connectionId,
                userId,
                tenantId,
                userId + "@example.com",
                "encrypted:refresh",
                "history-1",
                null,
                Instant.parse("2026-08-21T10:00:00Z"))));
    }
}
