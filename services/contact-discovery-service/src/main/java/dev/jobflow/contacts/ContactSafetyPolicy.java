package dev.jobflow.contacts;

import java.util.Locale;

final class ContactSafetyPolicy {
  private ContactSafetyPolicy() {}

  static boolean canPersist(DiscoveredContact contact) {
    if (contact == null || contact.allowedUse() == AllowedUse.NOT_ALLOWED) return false;
    if ((contact.email() == null || contact.email().isBlank()) && (contact.profileUrl() == null || contact.profileUrl().isBlank())) return false;
    if (contact.email() == null || contact.email().isBlank()) return true;
    return contact.evidence() != null && contact.evidence().toLowerCase(Locale.ROOT).contains(contact.email().toLowerCase(Locale.ROOT));
  }
}
