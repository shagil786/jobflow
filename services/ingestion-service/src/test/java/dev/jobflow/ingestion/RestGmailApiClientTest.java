package dev.jobflow.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

class RestGmailApiClientTest {
    @Test
    void fetchMessageMetadataRequestsOnlySafeHeaders() {
        ClientFixture fixture = createClientFixture();
        fixture.server()
                .expect(request -> {
                    MultiValueMap<String, String> queryParams = parseQuery(request.getURI().getRawQuery());
                    assertThat(request.getURI().getPath()).isEqualTo("/gmail/v1/users/me/messages/msg-1");
                    assertThat(queryParams.getFirst("format")).isEqualTo("metadata");
                    assertThat(queryParams.get("metadataHeaders"))
                            .containsExactly(
                                    "From",
                                    "Reply-To",
                                    "To",
                                    "Cc",
                                    "Subject",
                                    "Date",
                                    "Message-ID",
                                    "In-Reply-To",
                                    "References");
                    assertThat(queryParams.keySet()).containsExactlyInAnyOrder("format", "metadataHeaders");
                    assertThat(request.getHeaders().getFirst("Authorization")).isEqualTo("Bearer access-token");
                })
                .andRespond(withSuccess(
                        """
                        {
                          "id": "msg-1",
                          "threadId": "thread-1",
                          "labelIds": ["Label_JobFlowTrack"],
                          "payload": {
                            "headers": [
                              {"name": "From", "value": "Recruiter <recruiter@example.com>"},
                              {"name": "Reply-To", "value": "reply@example.com"},
                              {"name": "To", "value": "candidate@example.com, team@example.com"},
                              {"name": "Cc", "value": "manager@example.com"},
                              {"name": "Subject", "value": "Interview update"},
                              {"name": "Date", "value": "Fri, 21 Aug 2026 10:15:30 +0000"},
                              {"name": "Message-ID", "value": "<gmail-message-id>"},
                              {"name": "In-Reply-To", "value": "<prior-message-id>"},
                              {"name": "References", "value": "<thread-root>"}
                            ]
                          }
                        }
                        """,
                        MediaType.APPLICATION_JSON));

        SafeGmailMessage message = fixture.client().fetchMessageMetadata("access-token", "msg-1");

        fixture.server().verify();
        assertThat(message.messageId()).isEqualTo("msg-1");
        assertThat(message.threadId()).isEqualTo("thread-1");
        assertThat(message.sender()).isEqualTo("Recruiter <recruiter@example.com>");
        assertThat(message.replyTo()).isEqualTo("reply@example.com");
        assertThat(message.recipients()).containsExactly("candidate@example.com", "team@example.com", "manager@example.com");
        assertThat(message.subject()).isEqualTo("Interview update");
        assertThat(message.receivedAt()).isEqualTo(Instant.parse("2026-08-21T10:15:30Z"));
        assertThat(message.labelIds()).containsExactly("Label_JobFlowTrack");
        assertThat(message.normalizedContent()).isNull();
        assertThat(message.normalizedContentHash()).isNull();
    }

    @Test
    void fetchMessageBodyForProcessingSanitizesHtmlAndIgnoresAttachmentBytes() {
        ClientFixture fixture = createClientFixture();
        fixture.server()
                .expect(request -> {
                    assertThat(request.getURI().getPath()).isEqualTo("/gmail/v1/users/me/messages/msg-2");
                    assertThat(parseQuery(request.getURI().getRawQuery()).getFirst("format")).isEqualTo("full");
                })
                .andRespond(withSuccess(
                        """
                        {
                          "id": "msg-2",
                          "threadId": "thread-2",
                          "labelIds": ["Label_JobFlowTrack"],
                          "payload": {
                            "mimeType": "multipart/mixed",
                            "parts": [
                              {
                                "mimeType": "text/html",
                                "body": {
                                  "data": "%s"
                                }
                              },
                              {
                                "mimeType": "application/pdf",
                                "filename": "resume.pdf",
                                "body": {
                                  "data": "%s"
                                }
                              }
                            ]
                          }
                        }
                        """
                                .formatted(
                                        encode("<div>Hello <b>World</b> &amp; friends</div><script>evil()</script>"),
                                        encode("ATTACHMENT SECRET")),
                        MediaType.APPLICATION_JSON));

        SafeGmailMessage message = fixture.client().fetchMessageBodyForProcessing("access-token", "msg-2");

        fixture.server().verify();
        assertThat(message.messageId()).isEqualTo("msg-2");
        assertThat(message.threadId()).isEqualTo("thread-2");
        assertThat(message.normalizedContent()).isEqualTo("Hello World & friends");
        assertThat(message.normalizedContent()).doesNotContain("<b>").doesNotContain("ATTACHMENT SECRET");
        assertThat(message.normalizedContentHash()).isEqualTo(sha256("Hello World & friends"));
    }

    @Test
    void fetchMessageBodyForProcessingExcludesNestedTextInsideAttachmentParts() {
        ClientFixture fixture = createClientFixture();
        fixture.server()
                .expect(request -> assertThat(request.getURI().getPath()).isEqualTo("/gmail/v1/users/me/messages/msg-attachment"))
                .andRespond(withSuccess(
                        """
                        {
                          "id": "msg-attachment",
                          "threadId": "thread-attachment",
                          "payload": {
                            "mimeType": "multipart/mixed",
                            "parts": [
                              {
                                "mimeType": "multipart/alternative",
                                "parts": [
                                  {
                                    "mimeType": "text/plain",
                                    "body": {"data": "%s"}
                                  },
                                  {
                                    "mimeType": "text/html",
                                    "body": {"data": "%s"}
                                  }
                                ]
                              },
                              {
                                "mimeType": "multipart/mixed",
                                "filename": "forwarded-message.eml",
                                "parts": [
                                  {
                                    "mimeType": "text/plain",
                                    "body": {"data": "%s"}
                                  },
                                  {
                                    "mimeType": "multipart/alternative",
                                    "parts": [
                                      {
                                        "mimeType": "text/html",
                                        "body": {"data": "%s"}
                                      }
                                    ]
                                  }
                                ]
                              }
                            ]
                          }
                        }
                        """
                                .formatted(
                                        encode("Legitimate body text"),
                                        encode("<p>Legitimate body html</p>"),
                                        encode("Nested attached eml secret"),
                                        encode("<p>Nested attached html secret</p>")),
                        MediaType.APPLICATION_JSON));

        SafeGmailMessage message = fixture.client().fetchMessageBodyForProcessing("access-token", "msg-attachment");

        fixture.server().verify();
        assertThat(message.normalizedContent()).isEqualTo("Legitimate body text");
        assertThat(message.normalizedContent())
                .doesNotContain("Nested attached eml secret")
                .doesNotContain("Nested attached html secret");
    }

    @Test
    void safeGmailMessageToStringDoesNotIncludeNormalizedContent() {
        SafeGmailMessage message = new SafeGmailMessage(
                "msg-debug",
                "thread-debug",
                "sender@example.com",
                null,
                List.of("recipient@example.com"),
                "Subject",
                Instant.parse("2026-08-21T10:15:30Z"),
                List.of("Label_JobFlowTrack"),
                "TOP SECRET EMAIL BODY",
                "hash");

        assertThat(message.toString()).doesNotContain("TOP SECRET EMAIL BODY");
    }

    @Test
    void fetchMessageBodyForProcessingRejectsDecodedContentAbove256KiB() {
        ClientFixture fixture = createClientFixture();
        fixture.server()
                .expect(request -> assertThat(request.getURI().getPath()).isEqualTo("/gmail/v1/users/me/messages/msg-3"))
                .andRespond(withSuccess(
                        """
                        {
                          "id": "msg-3",
                          "threadId": "thread-3",
                          "payload": {
                            "mimeType": "text/plain",
                            "body": {
                              "data": "%s"
                            }
                          }
                        }
                        """
                                .formatted(encode("a".repeat(262_145))),
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> fixture.client().fetchMessageBodyForProcessing("access-token", "msg-3"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("256 KiB");

        fixture.server().verify();
    }

    private static ClientFixture createClientFixture() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestGmailApiClient client = new RestGmailApiClient(
                builder.build(),
                "https://oauth.example.test/token",
                "https://gmail.example.test/gmail/v1/users/me",
                "client-id",
                "client-secret");
        return new ClientFixture(client, server);
    }

    private static MultiValueMap<String, String> parseQuery(String rawQuery) {
        return rawQuery == null
                ? new LinkedMultiValueMap<>()
                : UriComponentsBuilder.newInstance().query(rawQuery).build().getQueryParams();
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte part : hash) {
                builder.append(String.format("%02x", part));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private record ClientFixture(RestGmailApiClient client, MockRestServiceServer server) {}
}
