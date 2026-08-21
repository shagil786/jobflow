package dev.jobflow.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest
@Import({JpaClassificationSuggestionStore.class, ClassificationSuggestionService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ClassificationSuggestionTransactionalContextTest {
    @Autowired
    private ClassificationSuggestionService service;

    @Autowired
    private ClassificationSuggestionRepository suggestions;

    @Autowired
    private GmailConnectionRepository connections;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EntityManager entityManager;

    @SpyBean
    private ClassificationSuggestionRepository repositorySpy;

    @AfterEach
    void cleanUpCommittedFixtures() {
        suggestions.deleteAllInBatch();
        connections.deleteAllInBatch();
    }

    @Test
    void duplicateRecoveryPreservesCallerOwnedPersistenceContextAndPendingWork() throws Exception {
        UUID connectionId = UUID.randomUUID();
        insertConnection(connectionId, "tenant-outer", "user-outer", "outer@example.com");
        ClassificationSuggestionRecord suggestion = suggestion(connectionId);
        CountDownLatch bothReadsFinished = new CountDownLatch(2);
        CountDownLatch competingInsertCommitted = new CountDownLatch(1);
        AtomicInteger firstReads = new AtomicInteger();
        AtomicReference<Thread> outerThread = new AtomicReference<>();
        AtomicReference<UUID> pendingConnectionId = new AtomicReference<>();

        doAnswer(invocation -> {
            Object result = findByJpa(invocation.getArguments());
            if (firstReads.incrementAndGet() <= 2) {
                bothReadsFinished.countDown();
                assertThat(bothReadsFinished.await(10, TimeUnit.SECONDS)).isTrue();
                if (Thread.currentThread() == outerThread.get()) {
                    assertThat(competingInsertCommitted.await(10, TimeUnit.SECONDS)).isTrue();
                }
            }
            return result;
        }).when(repositorySpy).findByTenantIdAndUserIdAndConnectionIdAndMessageIdAndClassifierVersionAndContentHash(
                "tenant-outer", "user-outer", connectionId, "message-outer", "rules-outer-v1", "hash-outer");

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<ClassificationSuggestionRecord> competing = executor.submit(() -> {
            try {
                return new TransactionTemplate(transactionManager).execute(status -> service.saveIfAbsent(suggestion));
            } finally {
                competingInsertCommitted.countDown();
            }
        });

        AtomicReference<ClassificationSuggestionRecord> outerResult = new AtomicReference<>();
        TransactionTemplate outerTransaction = new TransactionTemplate(transactionManager);
        outerThread.set(Thread.currentThread());
        outerTransaction.executeWithoutResult(status -> {
            UUID pendingId = UUID.randomUUID();
            GmailConnectionEntity pending = new GmailConnectionEntity(new StoredGmailConnection(
                    pendingId,
                    "user-outer",
                    "tenant-outer",
                    "pending@example.com",
                    "encrypted:pending",
                    "history-pending",
                    null,
                    Instant.parse("2026-08-21T10:01:00Z")));
            pendingConnectionId.set(pendingId);
            entityManager.persist(pending);
            outerResult.set(service.saveIfAbsent(suggestion));
            assertThat(entityManager.contains(pending)).isTrue();
        });

        ClassificationSuggestionRecord competingResult = competing.get(10, TimeUnit.SECONDS);
        executor.shutdownNow();

        assertThat(outerResult).hasValue(competingResult);
        assertThat(suggestions.count()).isEqualTo(1);
        assertThat(connections.findById(pendingConnectionId.get())).isPresent();
    }

    private void insertConnection(UUID connectionId, String tenantId, String userId, String email) {
        connections.saveAndFlush(new GmailConnectionEntity(new StoredGmailConnection(
                connectionId,
                userId,
                tenantId,
                email,
                "encrypted:refresh",
                "history-1",
                null,
                Instant.parse("2026-08-21T10:00:00Z"))));
    }

    private static ClassificationSuggestionRecord suggestion(UUID connectionId) {
        ClassificationSuggestionV1 value = new ClassificationSuggestionV1(
                "tenant-outer",
                "user-outer",
                "input-id",
                "message-outer",
                "thread-outer",
                MessageIntent.APPLICATION_CONFIRMATION,
                MessageDirection.INBOUND,
                null,
                null,
                null,
                null,
                0.9,
                List.of(),
                List.of("role"),
                List.of(),
                true,
                "rules-outer-v1",
                "hash-outer");
        return new ClassificationSuggestionRecord(connectionId, value);
    }

    private Optional<ClassificationSuggestionEntity> findByJpa(Object[] arguments) {
        return entityManager.createQuery(
                        "select suggestion from ClassificationSuggestionEntity suggestion "
                                + "where suggestion.tenantId = :tenantId "
                                + "and suggestion.userId = :userId "
                                + "and suggestion.connectionId = :connectionId "
                                + "and suggestion.messageId = :messageId "
                                + "and suggestion.classifierVersion = :classifierVersion "
                                + "and suggestion.contentHash = :contentHash",
                        ClassificationSuggestionEntity.class)
                .setParameter("tenantId", arguments[0])
                .setParameter("userId", arguments[1])
                .setParameter("connectionId", arguments[2])
                .setParameter("messageId", arguments[3])
                .setParameter("classifierVersion", arguments[4])
                .setParameter("contentHash", arguments[5])
                .getResultStream()
                .findFirst();
    }
}
