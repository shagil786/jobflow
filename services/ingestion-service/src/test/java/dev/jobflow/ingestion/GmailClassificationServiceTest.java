package dev.jobflow.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest
@Import({
    JpaGmailConnectionStore.class,
    JpaGmailMessageStore.class,
    GmailEvidenceService.class,
    DeterministicMessageIntentClassifier.class,
    JpaClassificationSuggestionStore.class,
    ClassificationSuggestionService.class,
    GmailClassificationService.class,
    EmailNormalizer.class,
    IdentityCandidateExtractor.class,
    GmailClassificationServiceTest.Config.class
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class GmailClassificationServiceTest {
    @Autowired
    private GmailClassificationService service;

    @Autowired
    private ClassificationSuggestionRepository suggestions;

    @Autowired
    private ClassificationSuggestionService suggestionService;

    @Autowired
    private GmailConnectionRepository connections;

    @Autowired
    private GmailMessageRepository messages;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private FakeGmailApiClient gmail;

    @BeforeEach
    void resetFakeGmail() {
        gmail.reset();
    }

    @Test
    void classifyRunsEvidenceClassificationAndPersistenceThroughTheTenantSafeBoundary() {
        UUID connectionId = UUID.randomUUID();
        insertConnection(connectionId, "tenant-1", "user-1");
        insertMessageMetadata(connectionId, "tenant-1", "user-1", "message-1", "thread-1");
        gmail.bodyByMessageId.put(
                "message-1",
                inboundBody(
                        "message-1",
                        "thread-1",
                        "Offer of employment",
                        "We are pleased to offer you the Senior Frontend Engineer position."));

        ClassificationSuggestionRecord saved = service.classify(connectionId, "message-1");

        assertThat(gmail.bodyFetchCount).isEqualTo(1);
        assertThat(saved.connectionId()).isEqualTo(connectionId);
        assertThat(saved.suggestion().tenantId()).isEqualTo("tenant-1");
        assertThat(saved.suggestion().userId()).isEqualTo("user-1");
        assertThat(saved.suggestion().intent()).isEqualTo(MessageIntent.OFFER);
        assertThat(saved.suggestion().requiresReview()).isTrue();
        assertThat(saved.suggestion().classifierVersion())
                .isEqualTo(DeterministicMessageIntentClassifier.CLASSIFIER_VERSION);
        assertThat(saved.suggestion().evidence()).allSatisfy(span -> assertThat(span.quotedText()).isNull());
        assertThat(suggestionService.findLatestByProviderIdentity("tenant-1", "user-1", connectionId, "message-1"))
                .contains(saved);
        assertThat(suggestions.count()).isEqualTo(1);
    }

    @Test
    void classifyReturnsTheSamePersistedSuggestionOnReplay() {
        UUID connectionId = UUID.randomUUID();
        insertConnection(connectionId, "tenant-1", "user-1");
        insertMessageMetadata(connectionId, "tenant-1", "user-1", "message-1", "thread-1");
        gmail.bodyByMessageId.put(
                "message-1",
                inboundBody(
                        "message-1",
                        "thread-1",
                        "Thank you for applying",
                        "Your application has been submitted for the Senior Frontend Engineer role."));

        ClassificationSuggestionRecord first = service.classify(connectionId, "message-1");
        ClassificationSuggestionRecord replayed = service.classify(connectionId, "message-1");

        assertThat(first).isEqualTo(replayed);
        assertThat(suggestions.count()).isEqualTo(1);
    }

    @Test
    void classifyRejectsUnknownMessagesWithoutFetchingOrPersisting() {
        UUID connectionId = UUID.randomUUID();
        insertConnection(connectionId, "tenant-1", "user-1");

        assertThatThrownBy(() -> service.classify(connectionId, "missing-message"))
                .isInstanceOf(UnknownGmailConnectionException.class)
                .hasMessage("Gmail connection not found");
        assertThat(gmail.bodyFetchCount).isZero();
        assertThat(suggestions.count()).isZero();
    }

    @Test
    void classifyLeavesNoSuggestionRowWhenBodyFetchFails() {
        UUID connectionId = UUID.randomUUID();
        insertConnection(connectionId, "tenant-1", "user-1");
        insertMessageMetadata(connectionId, "tenant-1", "user-1", "message-1", "thread-1");
        gmail.failuresByMessageId.put("message-1", new GmailFetchException(new IllegalStateException("boom")));

        assertThatThrownBy(() -> service.classify(connectionId, "message-1"))
                .isInstanceOf(GmailFetchException.class)
                .hasMessage("Gmail fetch failed");
        assertThat(gmail.bodyFetchCount).isEqualTo(1);
        assertThat(suggestions.count()).isZero();
    }

    @Test
    void classifyRejectsMessagesThatDoNotBelongToTheConnectionOwner() {
        UUID connectionId = UUID.randomUUID();
        insertConnection(connectionId, "tenant-1", "user-1");
        insertMessageMetadata(connectionId, "tenant-2", "user-2", "message-1", "thread-1");

        assertThatThrownBy(() -> service.classify(connectionId, "message-1"))
                .isInstanceOf(UnknownGmailConnectionException.class)
                .hasMessage("Gmail connection not found");
        assertThat(gmail.bodyFetchCount).isZero();
        assertThat(suggestions.count()).isZero();
    }

    @Test
    void classifyKeepsConflictingSignalsUnknownAndReviewRequired() {
        UUID connectionId = UUID.randomUUID();
        insertConnection(connectionId, "tenant-1", "user-1");
        insertMessageMetadata(connectionId, "tenant-1", "user-1", "message-conflict", "thread-conflict");
        gmail.bodyByMessageId.put(
                "message-conflict",
                inboundBody(
                        "message-conflict",
                        "thread-conflict",
                        "Decision on your application",
                        "We are pleased to offer you the role. Unfortunately, we will not be moving forward with your application."));

        ClassificationSuggestionRecord saved = service.classify(connectionId, "message-conflict");

        assertThat(saved.suggestion().intent()).isEqualTo(MessageIntent.UNKNOWN);
        assertThat(saved.suggestion().requiresReview()).isTrue();
        assertThat(saved.suggestion().contradictions()).anySatisfy(value -> assertThat(value).contains("OFFER"));
        assertThat(saved.suggestion().contradictions()).anySatisfy(value -> assertThat(value).contains("REJECTION"));
        assertThat(suggestions.count()).isEqualTo(1);
    }

    private void insertConnection(UUID connectionId, String tenantId, String userId) {
        inCommittedTransaction(() -> {
            connections.save(new GmailConnectionEntity(new StoredGmailConnection(
                    connectionId,
                    userId,
                    tenantId,
                    userId + "@example.com",
                    "encrypted:refresh",
                    "history-1",
                    null,
                    Instant.parse("2026-08-21T10:00:00Z"))));
            connections.flush();
        });
    }

    private void insertMessageMetadata(
            UUID connectionId,
            String tenantId,
            String userId,
            String messageId,
            String threadId) {
        inCommittedTransaction(() -> messages.save(new GmailMessageEntity(new GmailMessageMetadata(
                    connectionId,
                    tenantId,
                    userId,
                    messageId,
                    threadId,
                    "recruiter@example.com",
                    null,
                    "jobflow@example.com",
                    "Status update",
                    Instant.parse("2026-08-21T10:00:00Z"),
                    "INBOX",
                    null))));
    }

    private void inCommittedTransaction(Runnable action) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        template.executeWithoutResult(status -> action.run());
    }

    private static SafeGmailMessage inboundBody(String messageId, String threadId, String subject, String body) {
        return new SafeGmailMessage(
                messageId,
                threadId,
                "recruiter@example.com",
                null,
                List.of("jobflow@example.com"),
                subject,
                Instant.parse("2026-08-21T10:00:00Z"),
                List.of("INBOX"),
                body,
                null);
    }

    @TestConfiguration
    static class Config {
        @Bean
        GmailTokenCipher gmailTokenCipher() {
            return new GmailTokenCipher() {
                @Override
                public String encrypt(String value) {
                    return "encrypted:" + value;
                }

                @Override
                public String decrypt(String value) {
                    return value.substring("encrypted:".length());
                }
            };
        }

        @Bean
        FakeGmailApiClient gmailApiClient() {
            return new FakeGmailApiClient();
        }
    }

    static final class FakeGmailApiClient implements GmailApiClient {
        private final Map<String, SafeGmailMessage> bodyByMessageId = new HashMap<>();
        private final Map<String, RuntimeException> failuresByMessageId = new HashMap<>();
        private int bodyFetchCount;

        void reset() {
            bodyByMessageId.clear();
            failuresByMessageId.clear();
            bodyFetchCount = 0;
        }

        @Override
        public AccessToken refreshAccessToken(String refreshToken) {
            return new AccessToken("access", Instant.parse("2026-08-21T11:00:00Z"));
        }

        @Override
        public String trackLabelId(String accessToken) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String currentHistoryId(String accessToken) {
            throw new UnsupportedOperationException();
        }

        @Override
        public MessagePage listMessages(String accessToken, String query, String pageToken) {
            throw new UnsupportedOperationException();
        }

        @Override
        public HistoryPage listHistory(String accessToken, String startHistoryId, String labelId, String pageToken) {
            throw new UnsupportedOperationException();
        }

        @Override
        public SafeGmailMessage fetchMessageMetadata(String accessToken, String messageId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public SafeGmailMessage fetchMessageBodyForProcessing(String accessToken, String messageId) {
            bodyFetchCount++;
            RuntimeException failure = failuresByMessageId.get(messageId);
            if (failure != null) {
                throw failure;
            }
            return Optional.ofNullable(bodyByMessageId.get(messageId))
                    .orElseThrow(() -> new GmailFetchException(new IllegalArgumentException("missing body")));
        }
    }
}
