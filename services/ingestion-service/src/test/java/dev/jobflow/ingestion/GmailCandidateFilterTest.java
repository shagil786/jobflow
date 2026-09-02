package dev.jobflow.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GmailCandidateFilterTest {
    private final GmailCandidateFilter filter = new GmailCandidateFilter();

    @Test
    void recruiterAndInterviewSignalsBecomeReviewCandidates() {
        GmailMessageMetadata message = message(
                "Recruiting Team <recruiting@acme.example>",
                "Next steps for your Software Engineer application");

        GmailCandidateFilter.CandidateDecision decision = filter.evaluate(message);

        assertThat(decision.candidate()).isTrue();
        assertThat(decision.state()).isEqualTo(GmailMessageCandidateEntity.State.PENDING);
        assertThat(decision.score()).isGreaterThanOrEqualTo(2.0);
        assertThat(decision.signals()).contains("application", "interview");
    }

    @Test
    void promotionalMessagesAreExcludedEvenWhenTheyMentionJobs() {
        GmailMessageMetadata message = message(
                "Jobs Newsletter <news@jobs.example>",
                "New jobs for you - unsubscribe from this newsletter");

        GmailCandidateFilter.CandidateDecision decision = filter.evaluate(message);

        assertThat(decision.candidate()).isFalse();
        assertThat(decision.signals()).contains("promotional");
    }

    @Test
    void genericSenderDoesNotCreateCompanyIdentity() {
        GmailMessageMetadata message = message(
                "no-reply <no-reply@notifications.example>",
                "Application received for Frontend Engineer");

        GmailCandidateFilter.CandidateDecision decision = filter.evaluate(message);

        assertThat(decision.candidate()).isTrue();
        assertThat(decision.company()).isEmpty();
    }

    @Test
    void recognizesCommonJobLanguageWithoutRequiringARecruiterSender() {
        GmailCandidateFilter.CandidateDecision decision = filter.evaluate(message(
                "People Operations <people@example.com>",
                "A career opportunity for you"));

        assertThat(decision.candidate()).isTrue();
        assertThat(decision.signals()).contains("job-context");
    }

    private static GmailMessageMetadata message(String sender, String subject) {
        return new GmailMessageMetadata(
                UUID.randomUUID(), "tenant", "user", "message", "thread", sender, null,
                "candidate@example.com", subject, Instant.parse("2026-08-21T10:00:00Z"), null, null);
    }
}
