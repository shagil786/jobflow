package dev.jobflow.contacts;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class PublicSourceContactDiscoveryAdapter implements ContactDiscoveryAdapter {
  private static final Pattern EMAIL = Pattern.compile("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b");
  private final RestClient client = RestClient.builder().build();
  @Override public String name() { return "public-source"; }

  @Override public List<DiscoveredContact> discover(DiscoveryRequest request) {
    List<DiscoveredContact> result = new ArrayList<>();
    for (PublicSource source : request.publicSources()) {
      if (source == null || source.sourceUrl() == null) continue;
      String content = source.content();
      if (content == null || content.isBlank()) {
        String fetchableUrl = PublicSourceUrl.fetchableUrl(source.sourceUrl());
        if (fetchableUrl == null || EmailPolicy.isRejectedHost(fetchableUrl)) continue;
        content = fetch(fetchableUrl);
      }
      if (content == null || content.isBlank()) continue;
      content = content.substring(0, Math.min(content.length(), 200_000));
      Matcher matcher = EMAIL.matcher(content);
      while (matcher.find()) {
        String email = matcher.group().toLowerCase(Locale.ROOT);
        String excerpt = excerpt(content, matcher.start(), matcher.end());
        result.add(DiscoveredContact.of(null, null, null, EmailPolicy.firstSourceConfirmedEmail(email, excerpt), null, source.sourceUrl(), ContactSourceType.PUBLIC_SOURCE,
            excerpt, null, null, 0.78, ContactStatus.NEEDS_REVIEW, "SOURCE_CONFIRMED", name(), "v1", AllowedUse.OUTREACH_DRAFT));
      }
    }
    return result;
  }

  private String fetch(String sourceUrl) {
    try {
      java.net.URI uri = java.net.URI.create(sourceUrl);
      if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))) return null;
      java.net.InetAddress address = java.net.InetAddress.getByName(uri.getHost());
      if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress()) return null;
      return client.get().uri(uri).retrieve().body(String.class);
    } catch (RuntimeException | java.net.UnknownHostException exception) { return null; }
  }

  private String excerpt(String content, int start, int end) {
    int from = Math.max(0, start - 180); int to = Math.min(content.length(), end + 180);
    return content.substring(from, to).replaceAll("\\s+", " ").trim();
  }
}
