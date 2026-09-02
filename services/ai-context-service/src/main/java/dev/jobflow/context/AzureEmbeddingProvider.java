package dev.jobflow.context;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class AzureEmbeddingProvider implements EmbeddingProvider {
    private final RestClient client;
    private final ObjectMapper mapper;
    private final AzureSecretResolver secrets;
    private final String endpoint;
    private final String deployment;
    private final String apiVersion;

    @Autowired
    public AzureEmbeddingProvider(AzureSecretResolver secrets, ObjectMapper mapper,
            @Value("${azure.openai.endpoint:}") String endpoint,
            @Value("${azure.openai.embedding-deployment:}") String deployment,
            @Value("${azure.openai.api-version:}") String apiVersion) {
        this(RestClient.builder().requestFactory(new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build())).build(), secrets, mapper, endpoint, deployment, apiVersion);
    }

    AzureEmbeddingProvider(RestClient client, AzureSecretResolver secrets, ObjectMapper mapper,
                           String endpoint, String deployment, String apiVersion) {
        this.client = client; this.secrets = secrets; this.mapper = mapper;
        this.endpoint = endpoint == null ? "" : endpoint.replaceAll("/$", "");
        this.deployment = deployment == null ? "" : deployment.trim(); this.apiVersion = apiVersion == null ? "" : apiVersion.trim();
    }

    @Override public String modelVersion() { return deployment.isBlank() ? "keyword-only:v1" : "azure:" + deployment; }

    @Override public List<Float> embed(String text) {
        // Evidence indexing remains useful before the separate embedding deployment is
        // provisioned: the context store can still serve tenant-scoped keyword search.
        // Semantic retrieval is intentionally disabled rather than inventing vectors.
        if (deployment.isBlank() || endpoint.isBlank()) return List.of();
        Map<String,Object> request = new LinkedHashMap<>(); request.put("input", text);
        try {
            String body = client.post().uri(uri()).contentType(MediaType.APPLICATION_JSON).header("api-key", secrets.resolve()).body(request).retrieve().body(String.class);
            JsonNode data = mapper.readTree(body).path("data").path(0).path("embedding");
            if (!data.isArray() || data.isEmpty()) throw new ContextUnavailableException("Azure returned no embedding");
            List<Float> result = new ArrayList<>(); data.forEach(value -> result.add((float)value.asDouble())); return List.copyOf(result);
        } catch (RestClientResponseException exception) { throw new ContextUnavailableException("Azure embedding request failed with status " + exception.getStatusCode().value(), exception);
        } catch (RestClientException | java.io.IOException exception) { throw new ContextUnavailableException("Azure embedding request failed", exception); }
    }

    private java.net.URI uri() {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(endpoint + "/embeddings").queryParam("api-version", apiVersion);
        return builder.build().encode().toUri();
    }
}
