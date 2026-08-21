package dev.jobflow.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@Import({JpaClassificationSuggestionStore.class, ClassificationSuggestionService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ClassificationSuggestionStoreTest {
    @Autowired
    private ClassificationSuggestionStore store;

    @Autowired
    private ClassificationSuggestionService service;

    @Autowired
    private ClassificationSuggestionRepository repository;

    @Autowired
    private GmailConnectionRepository connections;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUpCommittedFixtures() {
        repository.deleteAllInBatch();
        connections.deleteAllInBatch();
    }

    @Test
    void savesSuggestionsIdempotentlyAndDropsQuotedEvidenceTextBeforePersistence() {
        UUID connectionId = UUID.randomUUID();
        insertConnection(connectionId, "tenant-1", "user-1");
        ClassificationSuggestionRecord first = suggestionRecord(
                connectionId,
                "tenant-1",
                "user-1",
                "message-1",
                "thread-1",
                "rules-2026-08-21-v1",
                "hash-1",
                "We are pleased to offer you the role.");

        ClassificationSuggestionRecord saved = store.saveIfAbsent(first);
        ClassificationSuggestionRecord replayed = store.saveIfAbsent(first);

        assertThat(saved).isEqualTo(replayed);
        assertThat(saved.suggestion().evidence()).allSatisfy(span -> assertThat(span.quotedText()).isNull());
        assertThat(saved.suggestion().company()).isNotNull();
        assertThat(saved.suggestion().company().evidence()).allSatisfy(span -> assertThat(span.quotedText()).isNull());
        assertThat(repository.count()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "select evidence_json from classification_suggestions where suggestion_id = ?",
                        String.class,
                        saved.suggestion().suggestionId()))
                .doesNotContain("We are pleased to offer you the role.");
    }

    @Test
    void changedContentAndClassifierVersionCreateNewImmutableRowsAndLatestLookupReturnsNewest() {
        UUID connectionId = UUID.randomUUID();
        insertConnection(connectionId, "tenant-1", "user-1");

        ClassificationSuggestionRecord first = suggestionRecord(
                connectionId,
                "tenant-1",
                "user-1",
                "message-1",
                "thread-1",
                "rules-2026-08-21-v1",
                "hash-1",
                "application received");
        ClassificationSuggestionRecord changedContent = suggestionRecord(
                connectionId,
                "tenant-1",
                "user-1",
                "message-1",
                "thread-1",
                "rules-2026-08-21-v1",
                "hash-2",
                "regret to inform you");
        ClassificationSuggestionRecord changedVersion = suggestionRecord(
                connectionId,
                "tenant-1",
                "user-1",
                "message-1",
                "thread-1",
                "rules-2026-08-22-v2",
                "hash-2",
                "regret to inform you");

        ClassificationSuggestionRecord savedFirst = store.saveIfAbsent(first);
        ClassificationSuggestionRecord savedChangedContent = store.saveIfAbsent(changedContent);
        ClassificationSuggestionRecord savedChangedVersion = store.saveIfAbsent(changedVersion);

        jdbcTemplate.update(
                "update classification_suggestions set created_at = ? where suggestion_id = ?",
                Instant.parse("2026-08-21T12:00:00Z"),
                savedChangedVersion.suggestion().suggestionId());
        jdbcTemplate.update(
                "update classification_suggestions set created_at = ? where suggestion_id = ?",
                Instant.parse("2026-08-21T13:00:00Z"),
                savedChangedContent.suggestion().suggestionId());

        assertThat(repository.count()).isEqualTo(3);
        assertThat(savedChangedContent.suggestion().suggestionId()).isNotEqualTo(savedFirst.suggestion().suggestionId());
        assertThat(savedChangedVersion.suggestion().suggestionId()).isNotEqualTo(savedChangedContent.suggestion().suggestionId());
        assertThat(store.findLatestByProviderIdentity("tenant-1", "user-1", connectionId, "message-1"))
                .contains(savedChangedVersion);
    }

    @Test
    void allowsSameMessageAndContentAcrossDifferentConnectionOwnerScopes() {
        UUID firstConnectionId = UUID.randomUUID();
        UUID secondConnectionId = UUID.randomUUID();
        insertConnection(firstConnectionId, "tenant-1", "user-1");
        insertConnection(secondConnectionId, "tenant-2", "user-2");

        ClassificationSuggestionRecord first = suggestionRecord(
                firstConnectionId,
                "tenant-1",
                "user-1",
                "message-1",
                "thread-1",
                "rules-2026-08-21-v1",
                "hash-1",
                "Thank you for applying");
        ClassificationSuggestionRecord second = suggestionRecord(
                secondConnectionId,
                "tenant-2",
                "user-2",
                "message-1",
                "thread-1",
                "rules-2026-08-21-v1",
                "hash-1",
                "Thank you for applying");

        ClassificationSuggestionRecord savedFirst = store.saveIfAbsent(first);
        ClassificationSuggestionRecord savedSecond = store.saveIfAbsent(second);

        assertThat(repository.count()).isEqualTo(2);
        assertThat(savedSecond.suggestion().suggestionId())
                .isNotEqualTo(savedFirst.suggestion().suggestionId());
        assertThat(store.findLatestByProviderIdentity("tenant-1", "user-1", firstConnectionId, "message-1"))
                .contains(savedFirst);
        assertThat(store.findLatestByProviderIdentity("tenant-2", "user-2", secondConnectionId, "message-1"))
                .contains(savedSecond);
    }

    @Test
    void concurrentSameScopedSavesReturnTheSamePersistedSuggestion() throws Exception {
        UUID connectionId = UUID.randomUUID();
        insertConnection(connectionId, "tenant-race", "user-race");
        ClassificationSuggestionRecord suggestion = suggestionRecord(
                connectionId,
                "tenant-race",
                "user-race",
                "message-race",
                "thread-race",
                "rules-race-v1",
                "hash-race",
                "Application received");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Future<ClassificationSuggestionRecord> first = executor.submit(() -> saveAfter(start, suggestion));
        Future<ClassificationSuggestionRecord> second = executor.submit(() -> saveAfter(start, suggestion));
        start.countDown();

        ClassificationSuggestionRecord firstResult = first.get(10, TimeUnit.SECONDS);
        ClassificationSuggestionRecord secondResult = second.get(10, TimeUnit.SECONDS);
        executor.shutdownNow();

        assertThat(firstResult).isEqualTo(secondResult);
        assertThat(repository.count()).isEqualTo(1);
        assertThat(store.findLatestByProviderIdentity("tenant-race", "user-race", connectionId, "message-race"))
                .contains(firstResult);
    }

    @Test
    void rejectsCrossTenantWritesWhenTheConnectionOwnerDoesNotMatch() {
        UUID connectionId = UUID.randomUUID();
        insertConnection(connectionId, "tenant-1", "user-1");

        assertThatThrownBy(() -> store.saveIfAbsent(suggestionRecord(
                        connectionId,
                        "tenant-2",
                        "user-2",
                        "message-1",
                        "thread-1",
                        "rules-2026-08-21-v1",
                        "hash-1",
                        "Thank you for applying")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("classification suggestion owner does not match the Gmail connection owner");
        assertThat(repository.count()).isZero();
    }

    @Test
    void latestLookupRemainsTenantScoped() {
        UUID firstConnectionId = UUID.randomUUID();
        UUID secondConnectionId = UUID.randomUUID();
        insertConnection(firstConnectionId, "tenant-1", "user-1");
        insertConnection(secondConnectionId, "tenant-2", "user-2");
        ClassificationSuggestionRecord firstTenant = suggestionRecord(
                firstConnectionId,
                "tenant-1",
                "user-1",
                "message-1",
                "thread-1",
                "rules-2026-08-21-v1",
                "hash-1",
                "Thank you for applying");
        ClassificationSuggestionRecord secondTenant = suggestionRecord(
                secondConnectionId,
                "tenant-2",
                "user-2",
                "message-1",
                "thread-9",
                "rules-2026-08-21-v1",
                "hash-9",
                "Thank you for applying");

        store.saveIfAbsent(firstTenant);
        store.saveIfAbsent(secondTenant);

        assertThat(store.findLatestByProviderIdentity("tenant-1", "user-1", firstConnectionId, "message-1"))
                .hasValueSatisfying(record -> {
                    assertThat(record.connectionId()).isEqualTo(firstConnectionId);
                    assertThat(record.suggestion().tenantId()).isEqualTo("tenant-1");
                    assertThat(record.suggestion().userId()).isEqualTo("user-1");
                    assertThat(record.suggestion().messageId()).isEqualTo("message-1");
                });
        assertThat(store.findLatestByProviderIdentity("tenant-1", "user-1", secondConnectionId, "message-1")).isEmpty();
    }

    private static ClassificationSuggestionRecord suggestionRecord(
            UUID connectionId,
            String tenantId,
            String userId,
            String messageId,
            String threadId,
            String classifierVersion,
            String contentHash,
            String quotedText) {
        EvidenceSpanV1 evidence = new EvidenceSpanV1(
                tenantId,
                userId,
                "evidence-" + messageId + "-" + contentHash,
                messageId,
                threadId,
                "body",
                quotedText,
                contentHash,
                true);
        ExtractedFieldCandidateV1<String> company = new ExtractedFieldCandidateV1<>(
                "Example Corp",
                0.91,
                List.of(evidence),
                "body",
                true,
                false);
        ClassificationSuggestionV1 suggestion = new ClassificationSuggestionV1(
                tenantId,
                userId,
                UUID.nameUUIDFromBytes((messageId + classifierVersion + contentHash).getBytes()).toString(),
                messageId,
                threadId,
                MessageIntent.APPLICATION_CONFIRMATION,
                MessageDirection.INBOUND,
                company,
                null,
                null,
                null,
                0.91,
                List.of(evidence),
                List.of("role"),
                List.of(),
                true,
                classifierVersion,
                contentHash);
        return new ClassificationSuggestionRecord(connectionId, suggestion);
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

    private ClassificationSuggestionRecord saveAfter(
            CountDownLatch start, ClassificationSuggestionRecord suggestion) {
        try {
            assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
            return service.saveIfAbsent(suggestion);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(error);
        }
    }
}
