package dev.jobflow.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class GmailBackfillServiceTest {
    private final GmailBackfillRepository runs = mock(GmailBackfillRepository.class);
    private final GmailBackfillBatchRepository batches = mock(GmailBackfillBatchRepository.class);
    private final GmailConnectionStore connections = mock(GmailConnectionStore.class);
    private final BackfillQueue queue = mock(BackfillQueue.class);
    private GmailBackfillService service;
    private final UUID connectionId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-08-21T12:00:00Z");

    @BeforeEach
    void setUp() {
        service = new GmailBackfillService(runs, batches, connections,
                new GmailBackfillWindowPlanner(), queue, Clock.fixed(now, ZoneOffset.UTC));
        when(connections.find(connectionId)).thenReturn(Optional.of(new StoredGmailConnection(
                connectionId, "user-1", "tenant-1", "person@example.com", "encrypted", "history", null, now, true)));
        when(runs.findByTenantIdAndUserIdAndIdempotencyKey("tenant-1", "user-1", "idem-1")).thenReturn(Optional.empty());
        when(runs.findActiveByTenantIdAndUserId("tenant-1", "user-1")).thenReturn(Optional.empty());
        when(runs.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(batches.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsOneRunAndOrderedBatchesForAnOwnedConnection() {
        BackfillRunRecord record = service.start(new BackfillRequest(connectionId,
                Instant.parse("2026-08-01T12:00:00Z"), now, BackfillMode.AUTOMATIC),
                new BackfillOwnerContext("tenant-1", "user-1"), "idem-1");

        assertThat(record.status()).isEqualTo(BackfillRunStatus.QUEUED);
        assertThat(record.totalBatches()).isEqualTo(4);
        ArgumentCaptor<GmailBackfillRunEntity> runCaptor = ArgumentCaptor.forClass(GmailBackfillRunEntity.class);
        verify(runs).save(runCaptor.capture());
        assertThat(runCaptor.getValue().getTenantId()).isEqualTo("tenant-1");
        assertThat(runCaptor.getValue().getUserId()).isEqualTo("user-1");
        ArgumentCaptor<List<GmailBackfillBatchEntity>> batchCaptor = ArgumentCaptor.forClass(List.class);
        verify(batches).saveAll(batchCaptor.capture());
        assertThat(batchCaptor.getValue()).hasSize(4);
        assertThat(batchCaptor.getValue()).extracting(GmailBackfillBatchEntity::getSequenceNo).containsExactly(0, 1, 2, 3);
        ArgumentCaptor<BackfillQueue.BatchPayload> payloadCaptor = ArgumentCaptor.forClass(BackfillQueue.BatchPayload.class);
        verify(queue, times(4)).publish(payloadCaptor.capture());
        assertThat(payloadCaptor.getAllValues()).allSatisfy(payload -> {
            assertThat(payload.runId()).isEqualTo(record.runId());
            assertThat(payload.connectionId()).isEqualTo(connectionId);
            assertThat(payload.tenantId()).isEqualTo("tenant-1");
            assertThat(payload.userId()).isEqualTo("user-1");
            assertThat(payload.correlationId()).isNotBlank();
            assertThat(payload.attemptId()).isNotBlank();
        });
    }

    @Test
    void replaysAnIdempotentRequestWithoutCreatingAnotherRun() {
        GmailBackfillRunEntity existing = GmailBackfillRunEntity.queued(
                UUID.randomUUID(), "tenant-1", "user-1", connectionId, "idem-1", BackfillMode.AUTOMATIC,
                now.minusSeconds(100), now, 7, "corr-existing", now);
        when(runs.findByTenantIdAndUserIdAndIdempotencyKey("tenant-1", "user-1", "idem-1")).thenReturn(Optional.of(existing));

        BackfillRunRecord record = service.start(new BackfillRequest(connectionId, now.minusSeconds(100), now, BackfillMode.AUTOMATIC),
                new BackfillOwnerContext("tenant-1", "user-1"), "idem-1");

        assertThat(record.runId()).isEqualTo(existing.getRunId());
        verify(runs, never()).save(any());
        verify(batches, never()).saveAll(any());
        verifyNoInteractions(queue);
    }

    @Test
    void rejectsConnectionOwnedByAnotherTenantAndSecondActiveRun() {
        when(connections.find(connectionId)).thenReturn(Optional.of(new StoredGmailConnection(
                connectionId, "other-user", "other-tenant", "person@example.com", "encrypted", "history", null, now, true)));
        assertThatThrownBy(() -> service.start(new BackfillRequest(connectionId, now.minusSeconds(100), now, BackfillMode.AUTOMATIC),
                new BackfillOwnerContext("tenant-1", "user-1"), "idem-1"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("GMAIL_CONNECTION_NOT_OWNED");

        when(connections.find(connectionId)).thenReturn(Optional.of(new StoredGmailConnection(
                connectionId, "user-1", "tenant-1", "person@example.com", "encrypted", "history", null, now, true)));
        when(runs.findActiveByTenantIdAndUserId("tenant-1", "user-1")).thenReturn(Optional.of(GmailBackfillRunEntity.queued(
                UUID.randomUUID(), "tenant-1", "user-1", connectionId, "other-idem", BackfillMode.AUTOMATIC,
                now.minusSeconds(100), now, 7, "corr", now)));
        assertThatThrownBy(() -> service.start(new BackfillRequest(connectionId, now.minusSeconds(100), now, BackfillMode.AUTOMATIC),
                new BackfillOwnerContext("tenant-1", "user-1"), "idem-1"))
                .isInstanceOf(IllegalStateException.class).hasMessage("GMAIL_BACKFILL_ALREADY_ACTIVE");
    }
}
