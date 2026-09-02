package dev.jobflow.contacts;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class EmailPolicyTest {
  @Test void onlyAcceptsAnAddressActuallyPresentInEvidence() {
    assertThat(EmailPolicy.firstSourceConfirmedEmail("jane@company.example", "Contact Jane at jane@company.example")).isEqualTo("jane@company.example");
    assertThat(EmailPolicy.firstSourceConfirmedEmail("jane@company.example", "Contact Jane through the company")).isNull();
  }

  @Test void blocksPrivateAndLinkedInHostsForPublicAdapter() {
    assertThat(EmailPolicy.isRejectedHost("https://www.linkedin.com/in/jane")).isTrue();
    assertThat(EmailPolicy.isRejectedHost("http://127.0.0.1:8080/admin")).isTrue();
  }
}
