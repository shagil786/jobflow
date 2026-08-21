package dev.jobflow.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeterministicMessageIntentClassifierTest {
    private static final UUID CONNECTION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final MessageIntentClassifier classifier = new DeterministicMessageIntentClassifier();

    @Test
    void classifiesTheApprovedIntentMatrixDeterministically() {
        List<IntentCase> cases = List.of(
                new IntentCase(
                        MessageIntent.APPLICATION_CONFIRMATION,
                        inboundMessage(
                                "Thank you for applying to Example Corp",
                                "Your application has been submitted for the Senior Frontend Engineer role.",
                                "no-reply@greenhouse.io",
                                List.of("jobflow@example.com"),
                                List.of("INBOX")),
                        preparedEvidence("message-application-confirmation", "hash-application-confirmation")),
                new IntentCase(
                        MessageIntent.RECRUITER_OUTREACH,
                        inboundMessage(
                                "Interested in your background",
                                "I came across your profile and would love to chat about a Senior Frontend Engineer opportunity at Example Corp.",
                                "recruiter@example.com",
                                List.of("jobflow@example.com"),
                                List.of("INBOX")),
                        preparedEvidence("message-recruiter-outreach", "hash-recruiter-outreach")),
                new IntentCase(
                        MessageIntent.RECRUITER_REPLY,
                        inboundMessage(
                                "Re: Senior Frontend Engineer",
                                "Thanks for reaching out. I'd be happy to talk next week.",
                                "recruiter@example.com",
                                List.of("jobflow@example.com"),
                                List.of("INBOX")),
                        preparedEvidence("message-recruiter-reply", "hash-recruiter-reply")),
                new IntentCase(
                        MessageIntent.INTERVIEW_INVITATION,
                        inboundMessage(
                                "Interview invitation",
                                "We would like to schedule an interview. Please select a time for your interview.",
                                "coordinator@example.com",
                                List.of("jobflow@example.com"),
                                List.of("INBOX")),
                        preparedEvidence("message-interview-invitation", "hash-interview-invitation")),
                new IntentCase(
                        MessageIntent.INTERVIEW_RESCHEDULE,
                        inboundMessage(
                                "Reschedule your interview",
                                "We need to reschedule your interview to Thursday at 2 PM.",
                                "coordinator@example.com",
                                List.of("jobflow@example.com"),
                                List.of("INBOX")),
                        preparedEvidence("message-interview-reschedule", "hash-interview-reschedule")),
                new IntentCase(
                        MessageIntent.INTERVIEW_FEEDBACK,
                        inboundMessage(
                                "Interview feedback",
                                "Thank you for interviewing with us. We wanted to share feedback from your interview.",
                                "coordinator@example.com",
                                List.of("jobflow@example.com"),
                                List.of("INBOX")),
                        preparedEvidence("message-interview-feedback", "hash-interview-feedback")),
                new IntentCase(
                        MessageIntent.REJECTION,
                        inboundMessage(
                                "Update on your application",
                                "Unfortunately, we will not be moving forward with your application.",
                                "no-reply@example.com",
                                List.of("jobflow@example.com"),
                                List.of("INBOX")),
                        preparedEvidence("message-rejection", "hash-rejection")),
                new IntentCase(
                        MessageIntent.OFFER,
                        inboundMessage(
                                "Offer of employment",
                                "We are pleased to offer you the Senior Frontend Engineer position.",
                                "recruiter@example.com",
                                List.of("jobflow@example.com"),
                                List.of("INBOX")),
                        preparedEvidence("message-offer", "hash-offer")),
                new IntentCase(
                        MessageIntent.WITHDRAWAL,
                        outboundMessage(
                                "Withdrawing my application",
                                "I would like to withdraw my application for the Senior Frontend Engineer role.",
                                "jobflow@example.com",
                                List.of("recruiter@example.com")),
                        preparedEvidence("message-withdrawal", "hash-withdrawal")),
                new IntentCase(
                        MessageIntent.FOLLOW_UP_REQUEST,
                        inboundMessage(
                                "Availability for next steps",
                                "Could you please send your availability for a follow-up conversation?",
                                "recruiter@example.com",
                                List.of("jobflow@example.com"),
                                List.of("INBOX")),
                        preparedEvidence("message-follow-up-request", "hash-follow-up-request")),
                new IntentCase(
                        MessageIntent.EMPLOYER_UPDATE,
                        inboundMessage(
                                "Status update",
                                "We are still reviewing applications and will be in touch soon.",
                                "recruiter@example.com",
                                List.of("jobflow@example.com"),
                                List.of("INBOX")),
                        preparedEvidence("message-employer-update", "hash-employer-update")),
                new IntentCase(
                        MessageIntent.UNRELATED,
                        inboundMessage(
                                "Weekly newsletter",
                                "Unsubscribe from this mailing list for product updates.",
                                "newsletter@example.com",
                                List.of("jobflow@example.com"),
                                List.of("INBOX")),
                        preparedEvidence("message-unrelated", "hash-unrelated")));

        for (IntentCase testCase : cases) {
            ClassificationSuggestionRecord suggestion = classifier.classify(testCase.message(), testCase.evidence());

            assertThat(suggestion.suggestion().intent()).isEqualTo(testCase.expectedIntent());
            assertThat(suggestion.suggestion().direction()).isEqualTo(expectedDirection(testCase.message()));
            assertThat(suggestion.suggestion().requiresReview()).isTrue();
            assertThat(suggestion.suggestion().classifierVersion()).isEqualTo(DeterministicMessageIntentClassifier.CLASSIFIER_VERSION);
            assertThat(suggestion.suggestion().contentHash()).isEqualTo(testCase.evidence().contentHash());
            assertThat(suggestion.suggestion().confidence()).isBetween(0.60, 1.0);
            assertThat(suggestion.connectionId()).isEqualTo(CONNECTION_ID);
        }
    }

    @Test
    void keepsLowConfidenceMessagesUnknown() {
        SafeGmailMessage message = inboundMessage(
                "Checking in",
                "Just following up on the conversation when you have time.",
                "someone@example.com",
                List.of("jobflow@example.com"),
                List.of("INBOX"));

        ClassificationSuggestionRecord suggestion = classifier.classify(message, preparedEvidence("message-low-confidence", "hash-low-confidence"));

        assertThat(suggestion.suggestion().intent()).isEqualTo(MessageIntent.UNKNOWN);
        assertThat(suggestion.suggestion().confidence()).isLessThan(0.60);
        assertThat(suggestion.suggestion().contradictions()).isEmpty();
    }

    @Test
    void turnsContradictoryStrongSignalsIntoUnknownWithConflictsAndBothEvidenceSpans() {
        SafeGmailMessage message = inboundMessage(
                "Decision on your application",
                "We are pleased to offer you the role. Unfortunately, we will not be moving forward with your application.",
                "recruiter@example.com",
                List.of("jobflow@example.com"),
                List.of("INBOX"));

        ClassificationSuggestionRecord suggestion = classifier.classify(message, preparedEvidence("message-conflict", "hash-conflict"));

        assertThat(suggestion.suggestion().intent()).isEqualTo(MessageIntent.UNKNOWN);
        assertThat(suggestion.suggestion().contradictions()).anySatisfy(value -> assertThat(value).contains("OFFER"));
        assertThat(suggestion.suggestion().contradictions()).anySatisfy(value -> assertThat(value).contains("REJECTION"));
        assertThat(suggestion.suggestion().evidence())
                .extracting(EvidenceSpanV1::quotedText)
                .anySatisfy(value -> assertThat(value).contains("pleased to offer"))
                .anySatisfy(value -> assertThat(value).contains("not be moving forward"));
    }

    @Test
    void preservesPreparedEvidenceHashesCandidatesAndDirection() {
        IdentityCandidateExtractor.EvidenceSpan preparedSpan = span(
                "message-preserve",
                "thread-preserve",
                "body",
                "Your application has been submitted",
                "hash-preserve");
        GmailEvidenceService.PreparedEvidence evidence = new GmailEvidenceService.PreparedEvidence(
                "tenant-1",
                "user-1",
                CONNECTION_ID,
                "message-preserve",
                "thread-preserve",
                "UNKNOWN",
                "hash-preserve",
                true,
                candidate("Example Corp", "header", preparedSpan),
                candidate("Senior Frontend Engineer", "body", preparedSpan),
                candidate("2026-08-21", "body", preparedSpan),
                candidate("recruiter@example.com", "header", preparedSpan),
                List.of(preparedSpan),
                List.of());
        SafeGmailMessage message = inboundMessage(
                "Application received",
                "Your application has been submitted.",
                "recruiter@example.com",
                List.of("jobflow@example.com"),
                List.of("INBOX"));

        ClassificationSuggestionRecord suggestion = classifier.classify(message, evidence);

        assertThat(suggestion.suggestion().company()).isNotNull();
        assertThat(suggestion.suggestion().company().value()).isEqualTo("Example Corp");
        assertThat(suggestion.suggestion().role()).isNotNull();
        assertThat(suggestion.suggestion().role().value()).isEqualTo("Senior Frontend Engineer");
        assertThat(suggestion.suggestion().applicationDate()).isNotNull();
        assertThat(suggestion.suggestion().applicationDate().value()).isEqualTo("2026-08-21");
        assertThat(suggestion.suggestion().contact()).isNotNull();
        assertThat(suggestion.suggestion().contact().value()).isEqualTo("recruiter@example.com");
        assertThat(suggestion.suggestion().evidence())
                .extracting(EvidenceSpanV1::normalizedTextHash)
                .containsOnly("hash-preserve");
        assertThat(suggestion.suggestion().direction()).isEqualTo(MessageDirection.INBOUND);
        assertThat(suggestion.suggestion().missingFields()).isEmpty();
    }

    @Test
    void returnsUnknownDirectionWhenHeadersCannotSupportIt() {
        SafeGmailMessage message = new SafeGmailMessage(
                "message-direction-unknown",
                "thread-direction-unknown",
                null,
                null,
                List.of(),
                "Status update",
                Instant.parse("2026-08-21T12:00:00Z"),
                List.of("INBOX"),
                "We are still reviewing applications.",
                "hash-direction-unknown");

        ClassificationSuggestionRecord suggestion = classifier.classify(
                message, preparedEvidence("message-direction-unknown", "hash-direction-unknown"));

        assertThat(suggestion.suggestion().direction()).isEqualTo(MessageDirection.UNKNOWN);
    }

    private static GmailEvidenceService.PreparedEvidence preparedEvidence(String messageId, String contentHash) {
        IdentityCandidateExtractor.EvidenceSpan companySpan = span(
                messageId, "thread-" + messageId, "subject", "Example Corp", contentHash);
        IdentityCandidateExtractor.EvidenceSpan roleSpan = span(
                messageId, "thread-" + messageId, "body", "Senior Frontend Engineer", contentHash);
        IdentityCandidateExtractor.EvidenceSpan contactSpan = span(
                messageId, "thread-" + messageId, "header", "recruiter@example.com", contentHash);
        return new GmailEvidenceService.PreparedEvidence(
                "tenant-1",
                "user-1",
                CONNECTION_ID,
                messageId,
                "thread-" + messageId,
                "UNKNOWN",
                contentHash,
                true,
                candidate("Example Corp", "header", companySpan),
                candidate("Senior Frontend Engineer", "body", roleSpan),
                IdentityCandidateExtractor.ExtractedFieldCandidate.empty("body"),
                candidate("recruiter@example.com", "header", contactSpan),
                List.of(companySpan, roleSpan, contactSpan),
                List.of("applicationDate"));
    }

    private static IdentityCandidateExtractor.ExtractedFieldCandidate<String> candidate(
            String value,
            String source,
            IdentityCandidateExtractor.EvidenceSpan span) {
        return new IdentityCandidateExtractor.ExtractedFieldCandidate<>(value, 0.8, List.of(span), source, true, false);
    }

    private static IdentityCandidateExtractor.EvidenceSpan span(
            String messageId,
            String threadId,
            String source,
            String text,
            String contentHash) {
        return new IdentityCandidateExtractor.EvidenceSpan(
                messageId + ":" + source + ":" + text.hashCode(),
                messageId,
                threadId,
                source,
                text,
                contentHash,
                true);
    }

    private static SafeGmailMessage inboundMessage(
            String subject,
            String body,
            String sender,
            List<String> recipients,
            List<String> labelIds) {
        return new SafeGmailMessage(
                "message-" + Math.abs((subject + body).hashCode()),
                "thread-" + Math.abs(subject.hashCode()),
                sender,
                null,
                recipients,
                subject,
                Instant.parse("2026-08-21T12:00:00Z"),
                labelIds,
                body,
                "hash-" + Math.abs(body.hashCode()));
    }

    private static SafeGmailMessage outboundMessage(
            String subject,
            String body,
            String sender,
            List<String> recipients) {
        return new SafeGmailMessage(
                "message-" + Math.abs((subject + body).hashCode()),
                "thread-" + Math.abs(subject.hashCode()),
                sender,
                null,
                recipients,
                subject,
                Instant.parse("2026-08-21T12:00:00Z"),
                List.of("SENT"),
                body,
                "hash-" + Math.abs(body.hashCode()));
    }

    private static MessageDirection expectedDirection(SafeGmailMessage message) {
        if (message.labelIds().contains("SENT")) {
            return MessageDirection.OUTBOUND;
        }
        if ((message.sender() == null || message.sender().isBlank()) && message.recipients().isEmpty()) {
            return MessageDirection.UNKNOWN;
        }
        return MessageDirection.INBOUND;
    }

    private record IntentCase(
            MessageIntent expectedIntent,
            SafeGmailMessage message,
            GmailEvidenceService.PreparedEvidence evidence) {}
}
