package dev.jobflow.jobs;

import java.util.List;

/** Provider boundary for attributable, official job feeds. No web scraping belongs here. */
public interface OfficialJobFeedAdapter {
  String sourceName();
  boolean enabled();
  List<OfficialJob> fetch(String tenantId, String userId);

  record OfficialJob(String sourceId, String title, String company, String jobUrl, String description,
                     String sourceFreshness, List<String> citations) {}
}
