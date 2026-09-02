package dev.jobflow.contacts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ContactDiscoveryServiceTest {
  private final EnrichmentRunRepository runs = mock(EnrichmentRunRepository.class);
  private final ContactCandidateRepository contacts = mock(ContactCandidateRepository.class);
  private final ContactDiscoveryAdapter gmail = new GmailContactDiscoveryAdapter();
  private final ContactDiscoveryAdapter publicSource = new PublicSourceContactDiscoveryAdapter();
  private final ContactDiscoveryService service = new ContactDiscoveryService(runs, contacts, List.of(gmail, publicSource));

  @Test void persistsSourceConfirmedGmailAndRejectsGuessedAddress() {
    UUID applicationId = UUID.randomUUID();
    when(runs.findByTenantIdAndUserIdAndApplicationIdAndIdempotencyKey(any(), any(), any(), any())).thenReturn(Optional.empty());
    when(runs.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(contacts.findByTenantIdAndUserIdAndApplicationIdAndContentHash(any(), any(), any(), any())).thenReturn(Optional.empty());
    when(contacts.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    var run = service.enrich("tenant-a", "user-a", applicationId, new ContactDiscoveryService.Request(
        List.of(new PublicSource("https://example.com/jobs/1", "Recruiting contact: recruiting@example.com", "job")),
        List.of(new GmailEvidence("m-1", "t-1", "Jane Recruiter <jane@company.example>", "Interview", "Please reply to jane@company.example", null))), "key-1");
    assertThat(run.getStatus()).isEqualTo(EnrichmentStatus.READY);
    assertThat(run.getVerifiedContacts()).isEqualTo(1);
    verify(contacts, times(2)).save(any(ContactCandidateEntity.class));
    assertThat(publicSource.discover(new DiscoveryRequest(List.of(new PublicSource("https://example.com/jobs/1", "Jane at jane@example.com", "job")), List.of())))
        .singleElement().extracting(DiscoveredContact::email).isEqualTo("jane@example.com");
  }

  @Test void idempotencyRejectsSameKeyWithDifferentPayload() {
    UUID applicationId = UUID.randomUUID();
    EnrichmentRunEntity existing = new EnrichmentRunEntity(applicationId, "tenant-a", "user-a", "key-1", "different");
    when(runs.findByTenantIdAndUserIdAndApplicationIdAndIdempotencyKey("tenant-a", "user-a", applicationId, "key-1")).thenReturn(Optional.of(existing));
    assertThatThrownBy(() -> service.enrich("tenant-a", "user-a", applicationId, new ContactDiscoveryService.Request(List.of(), List.of()), "key-1"))
        .isInstanceOf(ContactDiscoveryService.InvalidRequestException.class);
  }

  @Test void selectionCannotCrossTenantBoundary() {
    UUID applicationId = UUID.randomUUID(); UUID contactId = UUID.randomUUID();
    ContactCandidateEntity candidate = new ContactCandidateEntity(applicationId, "tenant-b", "user-b", DiscoveredContact.of(null, null, null, "jane@company.example", null, "gmail://message/m", ContactSourceType.GMAIL, "observed", "m", "t", .9, ContactStatus.VERIFIED, "SOURCE_CONFIRMED", "gmail-evidence", "v1", AllowedUse.OUTREACH_DRAFT));
    when(contacts.findById(contactId)).thenReturn(Optional.of(candidate));
    assertThatThrownBy(() -> service.select("tenant-a", "user-a", applicationId, contactId)).isInstanceOf(ContactDiscoveryService.NotFoundException.class);
  }
}
