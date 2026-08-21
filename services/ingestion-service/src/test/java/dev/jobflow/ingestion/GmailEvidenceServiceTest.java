package dev.jobflow.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GmailEvidenceServiceTest {
    @Test
    void prepareLoadsOneOwnedMessageFetchesBodyAndReturnsUnknownReviewRequiredEvidence() {
        UUID connectionId = UUID.randomUUID();
        InMemoryConnections connections = connected(connectionId, "history-10");
        InMemoryMessages messages = new InMemoryMessages();
        FakeGmail gmail = new FakeGmail();
        GmailMessageMetadata metadata = new GmailMessageMetadata(
                connectionId,
                "tenant",
                "user",
                "message-1",
                "thread-1",
                "Recruiter <jobs@acme.com>",
                "reply@acme.com",
                "candidate@example.com",
                "Application update",
                Instant.parse("2026-08-21T10:00:00Z"),
                "Label_JobFlowTrack",
                null);
        messages.values.put(connectionId + "::message-1", metadata);
        gmail.bodyByMessageId.put("message-1", new SafeGmailMessage(
                "message-1",
                "thread-1",
                null,
                null,
                List.of(),
                null,
                null,
                List.of("Label_JobFlowTrack"),
                """
                <div>Your application submitted on March 4 has been received.</div>
                <div>On Tue, March 5, 2026 at 11:00 AM Recruiter wrote:</div>
                <blockquote>Older reply</blockquote>
                """,
                null));

        GmailEvidenceService.PreparedEvidence prepared = new GmailEvidenceService(
                        connections,
                        new Cipher(),
                        gmail,
                        messages,
                        new EmailNormalizer(),
                        new IdentityCandidateExtractor())
                .prepare(connectionId, "message-1");

        assertThat(gmail.bodyFetchCount).isEqualTo(1);
        assertThat(prepared.intent()).isEqualTo("UNKNOWN");
        assertThat(prepared.requiresReview()).isTrue();
        assertThat(prepared.contentHash()).isEqualTo(EmailNormalizer.sha256(
                "Your application submitted on March 4 has been received.\n"
                        + "---------- Quoted content ---------\n"
                        + "On Tue, March 5, 2026 at 11:00 AM Recruiter wrote:\nOlder reply"));
        assertThat(prepared.applicationDate().value()).isEqualTo("March 4");
        assertThat(prepared.evidence()).isNotEmpty();
        assertThat(prepared.evidence()).allSatisfy(span -> {
            assertThat(span.sourceAvailable()).isTrue();
            assertThat(span.quotedText()).hasSizeLessThanOrEqualTo(160);
        });
        assertThat(prepared.missingFields()).contains("role");
    }

    @Test
    void prepareRejectsUnknownMessagesWithoutFetchingBody() {
        UUID connectionId = UUID.randomUUID();
        InMemoryConnections connections = connected(connectionId, "history-10");
        FakeGmail gmail = new FakeGmail();

        GmailEvidenceService service = new GmailEvidenceService(
                connections,
                new Cipher(),
                gmail,
                new InMemoryMessages(),
                new EmailNormalizer(),
                new IdentityCandidateExtractor());

        assertThatThrownBy(() -> service.prepare(connectionId, "missing-message"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Gmail message not found");
        assertThat(gmail.bodyFetchCount).isZero();
    }

    @Test
    void prepareHashesQuotedOnlyExtractionTextAndEvidenceWithTheSameHash() {
        UUID connectionId = UUID.randomUUID();
        InMemoryConnections connections = connected(connectionId, "history-10");
        InMemoryMessages messages = new InMemoryMessages();
        FakeGmail gmail = new FakeGmail();
        GmailMessageMetadata metadata = new GmailMessageMetadata(
                connectionId, "tenant", "user", "message-quoted", "thread-1",
                "Recruiter <recruiter@agency.com>", null, "candidate@example.com", "Application update",
                Instant.parse("2026-08-21T10:00:00Z"), "Label_JobFlowTrack", null);
        messages.values.put(connectionId + "::message-quoted", metadata);
        String quotedOnly = "---------- Forwarded message ---------\nFrom: ATS <jobs@acme.com>\nApplication with Acme";
        gmail.bodyByMessageId.put("message-quoted", new SafeGmailMessage(
                "message-quoted", "thread-1", null, null, List.of(), null, null,
                List.of("Label_JobFlowTrack"), quotedOnly, null));

        GmailEvidenceService.PreparedEvidence prepared = new GmailEvidenceService(
                        connections, new Cipher(), gmail, messages, new EmailNormalizer(), new IdentityCandidateExtractor())
                .prepare(connectionId, "message-quoted");

        String expectedExtractionText = new EmailNormalizer().normalize(quotedOnly).extractionText();
        String expectedHash = EmailNormalizer.sha256(expectedExtractionText);
        assertThat(prepared.contentHash()).isEqualTo(expectedHash);
        assertThat(prepared.company().evidence()).allSatisfy(span ->
                assertThat(span.normalizedTextHash()).isEqualTo(expectedHash));
        assertThat(expectedHash).isNotEqualTo(EmailNormalizer.sha256(
                new EmailNormalizer().normalize(quotedOnly.replace("Acme", "Beta")).extractionText()));
    }

    private static InMemoryConnections connected(UUID id, String historyId) {
        InMemoryConnections store = new InMemoryConnections();
        store.values.put(id, new StoredGmailConnection(id, "user", "tenant", "person@example.com", "encrypted:refresh", historyId, null, Instant.EPOCH));
        return store;
    }

    private static final class Cipher implements GmailTokenCipher {
        @Override public String encrypt(String value) { return "encrypted:" + value; }
        @Override public String decrypt(String value) { return value.substring("encrypted:".length()); }
    }

    private static final class InMemoryConnections implements GmailConnectionStore {
        private final Map<UUID, StoredGmailConnection> values = new HashMap<>();
        @Override public StoredGmailConnection save(StoredGmailConnection value) { values.put(value.connectionId(), value); return value; }
        @Override public Optional<StoredGmailConnection> find(UUID id) { return Optional.ofNullable(values.get(id)); }
        @Override public Optional<StoredGmailConnection> findByOwner(String tenantId, String userId) {
            return values.values().stream().filter(v -> v.tenantId().equals(tenantId) && v.userId().equals(userId)).findFirst();
        }
        @Override public Optional<StoredGmailConnection> findByOwnerAndEmail(String tenantId, String userId, String email) {
            return values.values().stream().filter(v -> v.tenantId().equals(tenantId) && v.userId().equals(userId) && v.email().equalsIgnoreCase(email)).findFirst();
        }
        @Override public List<StoredGmailConnection> findAllByOwner(String tenantId, String userId) {
            return values.values().stream().filter(v -> v.tenantId().equals(tenantId) && v.userId().equals(userId)).toList();
        }
    }

    private static final class InMemoryMessages implements GmailMessageStore {
        private final Map<String, GmailMessageMetadata> values = new HashMap<>();
        @Override public boolean saveIfAbsent(GmailMessageMetadata message) { return values.putIfAbsent(key(message.connectionId(), message.messageId()), message) == null; }
        @Override public Optional<GmailMessageMetadata> findByProviderIdentity(String tenantId, String userId, UUID connectionId, String messageId) {
            GmailMessageMetadata message = values.get(key(connectionId, messageId));
            if (message == null) {
                return Optional.empty();
            }
            if (!message.tenantId().equals(tenantId) || !message.userId().equals(userId)) {
                return Optional.empty();
            }
            return Optional.of(message);
        }
        @Override public long countForConnection(UUID connectionId) { return values.values().stream().filter(message -> message.connectionId().equals(connectionId)).count(); }
        private String key(UUID connectionId, String messageId) { return connectionId + "::" + messageId; }
    }

    private static final class FakeGmail implements GmailApiClient {
        private final Map<String, SafeGmailMessage> bodyByMessageId = new HashMap<>();
        private int bodyFetchCount;
        @Override public AccessToken refreshAccessToken(String refreshToken) { return new AccessToken("access", Instant.now().plusSeconds(300)); }
        @Override public String trackLabelId(String accessToken) { throw new UnsupportedOperationException(); }
        @Override public String currentHistoryId(String accessToken) { throw new UnsupportedOperationException(); }
        @Override public MessagePage listMessages(String accessToken, String query, String pageToken) { throw new UnsupportedOperationException(); }
        @Override public HistoryPage listHistory(String accessToken, String startHistoryId, String labelId, String pageToken) { throw new UnsupportedOperationException(); }
        @Override public SafeGmailMessage fetchMessageMetadata(String accessToken, String messageId) { throw new UnsupportedOperationException(); }
        @Override public SafeGmailMessage fetchMessageBodyForProcessing(String accessToken, String messageId) {
            bodyFetchCount++;
            return bodyByMessageId.get(messageId);
        }
    }
}
