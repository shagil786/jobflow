package dev.jobflow.contacts;

import java.net.URI;

/** Normalizes user-visible URL wrappers without granting access to private pages. */
final class PublicSourceUrl {
  private PublicSourceUrl() {}

  static String fetchableUrl(String raw) {
    if (raw == null || raw.isBlank()) return null;
    try {
      URI uri = URI.create(raw);
      String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
      if ((host.equals("www.linkedin.com") || host.equals("linkedin.com")) && uri.getPath() != null && uri.getPath().startsWith("/safety/go")) {
        for (String part : (uri.getRawQuery() == null ? "" : uri.getRawQuery()).split("&")) {
          if (part.startsWith("url=")) return fetchableUrl(java.net.URLDecoder.decode(part.substring(4), java.nio.charset.StandardCharsets.UTF_8));
        }
      }
      if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) return null;
      if (host.equals("linkedin.com") || host.endsWith(".linkedin.com")) return null;
      return uri.toString();
    } catch (IllegalArgumentException exception) { return null; }
  }
}
