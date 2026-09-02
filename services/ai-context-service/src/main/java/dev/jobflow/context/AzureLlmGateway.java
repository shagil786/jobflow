package dev.jobflow.context;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.time.Duration;
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
public class AzureLlmGateway implements GroundedGeneration.Gateway {
    private final RestClient client; private final ObjectMapper mapper; private final AzureSecretResolver secrets;
    private final GroundedOutputVerifier verifier; private final String endpoint, deployment, apiVersion;

    @Autowired
    public AzureLlmGateway(AzureSecretResolver secrets, ObjectMapper mapper, GroundedOutputVerifier verifier,
            @Value("${azure.openai.endpoint:}") String endpoint, @Value("${azure.openai.deployment:}") String deployment,
            @Value("${azure.openai.api-version:}") String apiVersion) {
        this(RestClient.builder().requestFactory(new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build())).build(), secrets, mapper, verifier, endpoint, deployment, apiVersion);
    }
    AzureLlmGateway(RestClient client, AzureSecretResolver secrets, ObjectMapper mapper, GroundedOutputVerifier verifier, String endpoint, String deployment, String apiVersion) {
        this.client=client; this.secrets=secrets; this.mapper=mapper; this.verifier=verifier; this.endpoint=endpoint==null?"":endpoint.replaceAll("/$",""); this.deployment=deployment==null?"":deployment.trim(); this.apiVersion=apiVersion==null?"":apiVersion.trim();
    }
    @Override public GroundedGeneration.Response generate(GroundedGeneration.Request request) {
        if (deployment.isBlank() || endpoint.isBlank()) throw new ContextUnavailableException("Azure LLM deployment is not configured");
        String context = request.context().evidence().stream().map(e -> "["+e.citation()+"] "+e.text()).reduce("", (a,b)->a+"\n"+b);
        Map<String,Object> body=new LinkedHashMap<>(); body.put("model",deployment); body.put("temperature",0); body.put("messages",List.of(
                Map.of("role","system","content","Use only the supplied evidence. Return JSON when a schema is supplied. Never invent claims. Keep the result reviewable and unsent."),
                Map.of("role","user","content",request.instruction()+"\nEvidence:\n"+context)));
        try {
            String raw=client.post().uri(uri()).contentType(MediaType.APPLICATION_JSON).header("api-key",secrets.resolve()).body(body).retrieve().body(String.class);
            JsonNode root=mapper.readTree(raw); String content=root.path("choices").path(0).path("message").path("content").asText("");
            List<String> citations=request.context().evidence().stream().map(ContextModels.RetrievedEvidence::citation).toList();
            return new GroundedGeneration.Response(content,citations,verifier.verify(content,request.context(),citations),"azure:"+deployment,false);
        } catch (RestClientResponseException exception) { throw new ContextUnavailableException("Azure LLM request failed with status "+exception.getStatusCode().value(),exception);
        } catch (RestClientException | java.io.IOException exception) { throw new ContextUnavailableException("Azure LLM request failed",exception); }
    }
    private java.net.URI uri(){ return UriComponentsBuilder.fromUriString(endpoint+"/chat/completions").queryParam("api-version",apiVersion).build().encode().toUri(); }
}
