package dev.jobflow.contacts;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Hunter boundary. Disabled until the workspace explicitly opts in and a server-side key is configured. */
@Component
public class HunterContactDiscoveryAdapter implements ProviderContactDiscoveryAdapter {
  private final boolean enabled;
  private final String endpoint;
  private final String apiKey;

  public HunterContactDiscoveryAdapter(
      @Value("${JOBFLOW_HUNTER_ENABLED:false}") boolean enabled,
      @Value("${JOBFLOW_HUNTER_ENDPOINT:https://api.hunter.io/v2/domain-search}") String endpoint,
      @Value("${JOBFLOW_HUNTER_API_KEY:}") String apiKey) {
    this.enabled = enabled; this.endpoint = endpoint; this.apiKey = apiKey;
  }

  @Override public String name() { return "hunter"; }
  @Override public String providerVersion() { return "adapter-v1"; }
  @Override public boolean enabled() { return enabled && !endpoint.isBlank() && !apiKey.isBlank(); }
  @Override public List<DiscoveredContact> discover(DiscoveryRequest request) { return List.of(); }
}
