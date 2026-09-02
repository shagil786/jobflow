package dev.jobflow.contacts;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Apollo boundary. Disabled until the workspace explicitly opts in and a server-side key is configured. */
@Component
public class ApolloContactDiscoveryAdapter implements ProviderContactDiscoveryAdapter {
  private final boolean enabled;
  private final String endpoint;
  private final String apiKey;

  public ApolloContactDiscoveryAdapter(
      @Value("${JOBFLOW_APOLLO_ENABLED:false}") boolean enabled,
      @Value("${JOBFLOW_APOLLO_ENDPOINT:https://api.apollo.io/v1/people/match}") String endpoint,
      @Value("${JOBFLOW_APOLLO_API_KEY:}") String apiKey) {
    this.enabled = enabled; this.endpoint = endpoint; this.apiKey = apiKey;
  }

  @Override public String name() { return "apollo"; }
  @Override public String providerVersion() { return "adapter-v1"; }
  @Override public boolean enabled() { return enabled && !endpoint.isBlank() && !apiKey.isBlank(); }
  @Override public List<DiscoveredContact> discover(DiscoveryRequest request) {
    // The adapter is intentionally isolated until the provider contract and
    // workspace consent are configured. It must never guess an address.
    return List.of();
  }
}
