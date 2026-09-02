package dev.jobflow.contacts;

import java.util.List;

/** Provider-neutral boundary. Implementations must return source-backed contacts only. */
public interface ProviderContactDiscoveryAdapter extends ContactDiscoveryAdapter {
  String providerVersion();
  default boolean enabled() { return false; }
  @Override default List<DiscoveredContact> discover(DiscoveryRequest request) { return List.of(); }
}
