package dev.jobflow.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class EmailNormalizerTest {
    private final EmailNormalizer normalizer = new EmailNormalizer();

    @Test
    void removesHtmlWhilePreservingVisibleTextAndDropsScriptAndStyleContent() {
        EmailNormalizer.NormalizedEmail normalized = normalizer.normalize(
                """
                <html>
                  <head>
                    <style>.hidden { display:none; }</style>
                    <script>stealCookies()</script>
                  </head>
                  <body>
                    <div>Hello <strong>World</strong></div>
                    <p>Application submitted on March 4</p>
                  </body>
                </html>
                """);

        assertThat(normalized.plainText()).isEqualTo("Hello World\nApplication submitted on March 4");
        assertThat(normalized.quotedText()).isEmpty();
        assertThat(normalized.plainText()).doesNotContain("stealCookies").doesNotContain("hidden");
        assertThat(normalized.contentHash()).isEqualTo(EmailNormalizer.sha256("Hello World\nApplication submitted on March 4"));
    }

    @Test
    void separatesQuotedReplyContentFromFreshContent() {
        EmailNormalizer.NormalizedEmail normalized = normalizer.normalize(
                """
                Thanks for the update.
                We received your application.

                On Tue, March 5, 2026 at 11:00 AM Recruiter wrote:
                > Previous body line
                > Previous follow-up
                """);

        assertThat(normalized.plainText()).isEqualTo("Thanks for the update.\nWe received your application.");
        assertThat(normalized.quotedText()).isEqualTo("On Tue, March 5, 2026 at 11:00 AM Recruiter wrote:\nPrevious body line\nPrevious follow-up");
    }

    @Test
    void returnsAValidEmptyResultForEmptyOrMalformedInput() {
        EmailNormalizer.NormalizedEmail blank = normalizer.normalize(null);
        EmailNormalizer.NormalizedEmail malformed = normalizer.normalize("<html><body><div><span></body></html>");

        assertThat(blank.plainText()).isEmpty();
        assertThat(blank.quotedText()).isEmpty();
        assertThat(blank.contentHash()).isEqualTo(EmailNormalizer.sha256(""));

        assertThat(malformed.plainText()).isEmpty();
        assertThat(malformed.quotedText()).isEmpty();
        assertThat(malformed.contentHash()).isEqualTo(EmailNormalizer.sha256(""));
    }

    @Test
    void rejectsInputAboveTheExistingBoundedContentLimit() {
        String oversized = "a".repeat(262_145 / "a".getBytes(StandardCharsets.UTF_8).length);

        assertThatThrownBy(() -> normalizer.normalize(oversized))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Gmail decoded content exceeds 256 KiB");
    }
}
