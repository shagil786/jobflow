package dev.jobflow.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ContextLogicTest {
    @Test
    void normalizesHtmlAndQuotedNoiseBeforeChunking() {
        String text = TextNormalizer.normalize("<style>.x{}</style><p>Interview confirmed.</p>\n\n> old reply");
        assertThat(text).contains("Interview confirmed.").doesNotContain("style").doesNotContain("old reply");
    }

    @Test
    void chunksAtParagraphBoundariesWithStableNonEmptyOutput() {
        List<String> chunks = SemanticChunker.chunk("First paragraph.\n\nSecond paragraph.");
        assertThat(chunks).containsExactly("First paragraph.\n\nSecond paragraph.");
    }

    @Test
    void contextQueryClampsUntrustedLimits() {
        ContextModels.ContextQuery query = new ContextModels.ContextQuery("tenant", "user", "correlation",
                ContextModels.Purpose.DRAFT, "follow up", List.of(), null, null, null, 500, 99_999);
        assertThat(query.limit()).isEqualTo(50);
        assertThat(query.maxTokens()).isEqualTo(16_000);
    }

    @Test
    void verifierRejectsEvidenceOutsideBundle() {
        ContextModels.ContextQuery query = new ContextModels.ContextQuery("tenant", "user", "correlation",
                ContextModels.Purpose.DRAFT, "follow up", List.of(), null, null, null, 10, 1000);
        ContextModels.RetrievedEvidence evidence = new ContextModels.RetrievedEvidence(
                java.util.UUID.randomUUID(), "gmail_message", "m1", "t1", "body", "reply", 0.9, "gmail_message:m1#0");
        ContextModels.ContextBundle bundle = new ContextModels.ContextBundle(query, List.of(evidence), 2,
                ContextModels.ReviewState.SUPPORTED, List.of());
        ContextModels.VerificationResult result = new GroundedOutputVerifier().verify("draft", bundle, List.of("forged:m9#0"));
        assertThat(result.state()).isEqualTo(ContextModels.ReviewState.UNKNOWN);
        assertThat(result.invalidEvidence()).contains("one or more citations are not in the context bundle");
    }
}
