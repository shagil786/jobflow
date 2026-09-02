package dev.jobflow.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import com.fasterxml.jackson.databind.ObjectMapper;

class AzureOpenAiClassifierProviderTest {
    private static final UUID CONNECTION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void sendsStructuredRequestAndKeepsOnlyVerifiableEvidence() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient client = builder.build();
        AzureOpenAiSecretResolver secrets = mock(AzureOpenAiSecretResolver.class);
        when(secrets.resolve()).thenReturn("secret-value");
        AzureOpenAiClassifierProvider provider = provider(client, secrets);

        server.expect(requestTo("https://azure.test/openai/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("api-key", "secret-value"))
                .andRespond(withSuccess(
                        responseFor("""
                        {"jobRelated":true,"intent":"INTERVIEW_INVITATION","confidence":0.91,"company":{"value":"Example Corp","quote":"interview with Example Corp"},"role":{"value":"Senior Frontend Engineer","quote":"Senior Frontend Engineer"},"applicationDate":null,"contact":{"value":"recruiter@example.com","quote":"recruiter@example.com"},"evidence":[{"field":"intent","quote":"schedule an interview"}],"contradictions":[]}
                        """),
                        MediaType.APPLICATION_JSON));

        ClassificationSuggestionV1 result = provider.classify(input());

        server.verify();
        assertThat(result.intent()).isEqualTo(MessageIntent.INTERVIEW_INVITATION);
        assertThat(result.company().value()).isEqualTo("Example Corp");
        assertThat(result.role().value()).isEqualTo("Senior Frontend Engineer");
        assertThat(result.contact().value()).isEqualTo("recruiter@example.com");
        assertThat(result.applicationDate()).isNull();
        assertThat(result.missingFields()).containsExactly("applicationDate");
        assertThat(result.evidence()).extracting(EvidenceSpanV1::source)
                .containsExactlyInAnyOrder("body", "body", "body", "body");
        assertThat(result.requiresReview()).isTrue();
        assertThat(result.classifierVersion()).isEqualTo(AzureOpenAiClassifierProvider.CLASSIFIER_VERSION);
    }

    @Test
    void rejectsUnsupportedValuesInsteadOfPersistingHallucinatedFields() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient client = builder.build();
        AzureOpenAiSecretResolver secrets = mock(AzureOpenAiSecretResolver.class);
        when(secrets.resolve()).thenReturn("secret-value");
        AzureOpenAiClassifierProvider provider = provider(client, secrets);

        server.expect(requestTo("https://azure.test/openai/v1/chat/completions"))
                .andRespond(withSuccess(
                        responseFor("""
                        {"jobRelated":true,"intent":"OFFER","confidence":0.99,"company":{"value":"Invented Corp","quote":"never appears"},"role":null,"applicationDate":null,"contact":null,"evidence":[],"contradictions":[]}
                        """),
                        MediaType.APPLICATION_JSON));

        ClassificationSuggestionV1 result = provider.classify(input());

        server.verify();
        assertThat(result.company()).isNull();
        assertThat(result.intent()).isEqualTo(MessageIntent.OFFER);
        assertThat(result.confidence()).isLessThanOrEqualTo(0.25);
        assertThat(result.contradictions()).contains("company value has no verifiable evidence");
    }

    @Test
    void doesNotExposeProviderResponseWhenAzureFails() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient client = builder.build();
        AzureOpenAiSecretResolver secrets = mock(AzureOpenAiSecretResolver.class);
        when(secrets.resolve()).thenReturn("secret-value");
        AzureOpenAiClassifierProvider provider = provider(client, secrets);

        server.expect(requestTo("https://azure.test/openai/v1/chat/completions"))
                .andRespond(withServerError().body("private provider response"));

        assertThatThrownBy(() -> provider.classify(input()))
                .isInstanceOf(ClassifierProviderException.class)
                .hasMessage("Azure OpenAI classification request failed with status 500")
                .hasMessageNotContaining("private provider response");
        server.verify();
    }

    private static AzureOpenAiClassifierProvider provider(RestClient client, AzureOpenAiSecretResolver secrets) {
        return new AzureOpenAiClassifierProvider(client, secrets, new ObjectMapper(), "https://azure.test/openai/v1", "gpt-5.6-luna", "");
    }

    private static String responseFor(String classification) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.writeValueAsString(java.util.Map.of(
                    "choices", List.of(java.util.Map.of("message", java.util.Map.of("content", classification)))));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static ClassifierProvider.ClassificationInput input() {
        SafeGmailMessage message = new SafeGmailMessage(
                "message-1",
                "thread-1",
                "Recruiter <recruiter@example.com>",
                "recruiter@example.com",
                List.of("candidate@example.com"),
                "Interview invitation",
                Instant.parse("2026-08-21T10:15:30Z"),
                List.of("INBOX"),
                "We would like to schedule an interview with Example Corp for the Senior Frontend Engineer role. Contact recruiter@example.com.",
                "hash-1");
        GmailEvidenceService.PreparedEvidence evidence = new GmailEvidenceService.PreparedEvidence(
                "tenant-1", "user-1", CONNECTION_ID, "message-1", "thread-1", "UNKNOWN", "hash-1", true,
                null, null, null, null, List.of(), List.of("company", "role", "applicationDate", "contact"));
        return new ClassifierProvider.ClassificationInput(message, evidence, null);
    }
}
