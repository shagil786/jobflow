package dev.jobflow.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class IdentityCandidateExtractorTest {
    private final IdentityCandidateExtractor extractor = new IdentityCandidateExtractor();

    @Test
    void jobsMailboxMaySupportACompanyCandidateButRequiresReview() {
        IdentityCandidateExtractor.ExtractionResult result = extractor.extract(message(
                "Jobs <jobs@acme.com>",
                null,
                "Thanks for applying.",
                Instant.parse("2026-08-21T10:00:00Z")));

        assertThat(result.company().value()).isEqualTo("Acme");
        assertThat(result.company().requiresReview()).isTrue();
        assertThat(result.company().confidence()).isBetween(0.0, 1.0);
        assertThat(result.company().evidence()).hasSize(1);
        assertThat(result.company().evidence().get(0).source()).isEqualTo("sender");
    }

    @Test
    void greenhouseNoReplyDoesNotYieldACompanyWithoutEmployerEvidence() {
        IdentityCandidateExtractor.ExtractionResult result = extractor.extract(message(
                "Greenhouse <no-reply@greenhouse.io>",
                null,
                "Your application is being processed.",
                Instant.parse("2026-08-21T10:00:00Z")));

        assertThat(result.company().value()).isNull();
        assertThat(result.company().evidence()).isEmpty();
    }

    @Test
    void explicitApplicationSubmittedPhraseProducesABodyBackedDateCandidate() {
        IdentityCandidateExtractor.ExtractionResult result = extractor.extract(message(
                "Recruiter <recruiter@acme.com>",
                null,
                "Your application submitted on March 4 has been received.",
                Instant.parse("2026-08-21T10:00:00Z")));

        assertThat(result.applicationDate().value()).isEqualTo("March 4");
        assertThat(result.applicationDate().requiresReview()).isTrue();
        assertThat(result.applicationDate().evidence()).singleElement()
                .extracting(IdentityCandidateExtractor.EvidenceSpan::source, IdentityCandidateExtractor.EvidenceSpan::sourceAvailable)
                .containsExactly("body", true);
    }

    @Test
    void gmailReceivedTimeIsNotUsedAsAConfirmedApplicationDate() {
        IdentityCandidateExtractor.ExtractionResult result = extractor.extract(message(
                "Recruiter <recruiter@acme.com>",
                null,
                "Thanks for applying.",
                Instant.parse("2026-03-04T10:00:00Z")));

        assertThat(result.applicationDate().value()).isNull();
        assertThat(result.applicationDate().evidence()).isEmpty();
    }

    @Test
    void headerAddressCanProduceAContactButANameWithoutAddressCannot() {
        IdentityCandidateExtractor.ExtractionResult contactable = extractor.extract(message(
                "Jane Recruiter <jane@acme.com>",
                "talent@acme.com",
                "Let's talk soon.",
                Instant.parse("2026-08-21T10:00:00Z")));
        IdentityCandidateExtractor.ExtractionResult nonContactable = extractor.extract(message(
                "Jane Recruiter",
                null,
                "Let's talk soon.",
                Instant.parse("2026-08-21T10:00:00Z")));

        assertThat(contactable.contact().value()).isEqualTo("talent@acme.com");
        assertThat(contactable.contact().evidence()).hasSize(1);
        assertThat(nonContactable.contact().value()).isNull();
    }

    @Test
    void forwardedBlocksStaySeparateAndRequireReview() {
        IdentityCandidateExtractor.ExtractionResult result = extractor.extract(message(
                "Recruiter <recruiter@agency.com>",
                null,
                """
                Please review the forwarded details.
                ---------- Forwarded message ---------
                From: ATS <jobs@acme.com>
                Subject: Application confirmation
                Your application submitted on March 4 has been received.
                """,
                Instant.parse("2026-08-21T10:00:00Z")));

        assertThat(result.applicationDate().value()).isEqualTo("March 4");
        assertThat(result.applicationDate().requiresReview()).isTrue();
        assertThat(result.applicationDate().evidence()).hasSizeGreaterThanOrEqualTo(2);
        assertThat(result.applicationDate().evidence())
                .extracting(IdentityCandidateExtractor.EvidenceSpan::quotedText)
                .anyMatch(text -> text.contains("Forwarded message"))
                .anyMatch(text -> text.contains("application submitted on March 4"));
    }

    private static SafeGmailMessage message(String sender, String replyTo, String normalizedContent, Instant receivedAt) {
        return new SafeGmailMessage(
                "message-1",
                "thread-1",
                sender,
                replyTo,
                List.of("candidate@example.com"),
                "Application update",
                receivedAt,
                List.of("Label_JobFlowTrack"),
                normalizedContent,
                EmailNormalizer.sha256(normalizedContent == null ? "" : normalizedContent));
    }
}
