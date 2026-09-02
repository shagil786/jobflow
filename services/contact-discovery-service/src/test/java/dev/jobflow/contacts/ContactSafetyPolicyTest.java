package dev.jobflow.contacts;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class ContactSafetyPolicyTest {
  @Test void rejectsProviderContactWithGuessedEmail() {
    var guessed = DiscoveredContact.of("Jane", "Recruiter", "Acme", "jane@acme.example", null, "https://provider.example/contact/1", ContactSourceType.PROVIDER, "Jane works at Acme", null, null, .8, ContactStatus.VERIFIED, "UNVERIFIED", "provider", "v1", AllowedUse.OUTREACH_DRAFT);
    assertThat(ContactSafetyPolicy.canPersist(guessed)).isFalse();
  }

  @Test void allowsAProfileOnlyReferenceWithoutMakingItDraftable() {
    var profile = DiscoveredContact.of("Jane", "Recruiter", "Acme", null, "https://example.com/team/jane", "https://example.com/team", ContactSourceType.PUBLIC_SOURCE, "Jane, Recruiter", null, null, .5, ContactStatus.NEEDS_REVIEW, "SOURCE_CONFIRMED", "public-source", "v1", AllowedUse.REFERENCE_ONLY);
    assertThat(ContactSafetyPolicy.canPersist(profile)).isTrue();
  }
}
