package dev.jobflow.contacts;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class GmailContactDiscoveryAdapter implements ContactDiscoveryAdapter {
  private static final Pattern ADDRESS = Pattern.compile("(?i)(?:^|[<\\s])([a-z0-9._%+\\-]+@[a-z0-9.\\-]+\\.[a-z]{2,})(?:>|$)");
  @Override public String name() { return "gmail-evidence"; }

  @Override public List<DiscoveredContact> discover(DiscoveryRequest request) {
    List<DiscoveredContact> result = new ArrayList<>();
    for (GmailEvidence evidence : request.gmailEvidence()) {
      if (evidence == null || evidence.messageId() == null || evidence.sender() == null) continue;
      Matcher matcher = ADDRESS.matcher(evidence.sender());
      if (!matcher.find()) continue;
      String email = matcher.group(1).toLowerCase(Locale.ROOT);
      String excerpt = safeExcerpt(evidence.excerpt() == null ? evidence.subject() : evidence.excerpt());
      if (excerpt.isBlank()) excerpt = "Sender " + email + " observed in Gmail message " + evidence.messageId();
      result.add(DiscoveredContact.of(null, null, null, email, null, "gmail://message/" + evidence.messageId(), ContactSourceType.GMAIL,
          excerpt, evidence.messageId(), evidence.threadId(), 0.92, ContactStatus.VERIFIED, "SOURCE_CONFIRMED", name(), "v1", AllowedUse.OUTREACH_DRAFT));
    }
    return result;
  }

  private String safeExcerpt(String value) { return value == null ? "" : value.replaceAll("\\s+", " ").trim().substring(0, Math.min(value.replaceAll("\\s+", " ").trim().length(), 1000)); }
}
