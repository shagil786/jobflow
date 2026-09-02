package dev.jobflow.contacts;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PublicSourceUrlTest {
  @Test
  void unwrapsLinkedInSafetyRedirectToItsPublicDestination() {
    assertThat(PublicSourceUrl.fetchableUrl("https://lnkd.in/abc123")).isEqualTo("https://lnkd.in/abc123");
    String wrapped = "https://www.linkedin.com/safety/go/?url=https%3A%2F%2Flnkd.in%2Fabc123&urlhash=x";
    assertThat(PublicSourceUrl.fetchableUrl(wrapped)).isEqualTo("https://lnkd.in/abc123");
  }

  @Test
  void refusesDirectLinkedInAndPrivateDestinations() {
    assertThat(PublicSourceUrl.fetchableUrl("https://www.linkedin.com/jobs/view/1")).isNull();
    assertThat(EmailPolicy.isRejectedHost("http://127.0.0.1:8080/private")).isTrue();
  }
}
