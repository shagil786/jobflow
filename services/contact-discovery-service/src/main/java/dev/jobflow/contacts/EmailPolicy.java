package dev.jobflow.contacts;

import java.util.Locale;
import java.util.regex.Pattern;

final class EmailPolicy {
  private static final Pattern EMAIL = Pattern.compile("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b");
  private EmailPolicy() {}

  static String firstSourceConfirmedEmail(String value, String sourceText) {
    if (value == null || sourceText == null) return null;
    String normalized = value.trim().toLowerCase(Locale.ROOT);
    if (!EMAIL.matcher(normalized).matches()) return null;
    return EMAIL.matcher(sourceText).results().map(match -> match.group().toLowerCase(Locale.ROOT))
        .filter(normalized::equals).findFirst().orElse(null);
  }

  static boolean isRejectedHost(String url) {
    if (url == null) return true;
    try {
      java.net.URI uri = java.net.URI.create(url);
      String host = uri.getHost();
      if (host == null || host.equalsIgnoreCase("linkedin.com") || host.toLowerCase(Locale.ROOT).endsWith(".linkedin.com")) return true;
      java.net.InetAddress address = java.net.InetAddress.getByName(host);
      return address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress();
    } catch (IllegalArgumentException | java.net.UnknownHostException exception) { return true; }
  }
}
