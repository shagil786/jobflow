package dev.jobflow.contacts;

import java.util.List;

public interface ContactDiscoveryAdapter {
  String name();
  List<DiscoveredContact> discover(DiscoveryRequest request);
}
