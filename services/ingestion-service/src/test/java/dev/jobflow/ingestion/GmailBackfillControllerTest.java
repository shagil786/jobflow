package dev.jobflow.ingestion;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = GmailBackfillController.class, properties = "JOBFLOW_INTERNAL_SERVICE_KEY=test-key")
class GmailBackfillControllerTest {
    @Autowired private MockMvc mvc;
    @MockBean private GmailBackfillService service;
    @MockBean private ClassificationReviewService reviews;
    @MockBean private GmailThreadViewService threadView;

    private static final UUID CONNECTION_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID RUN_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Test
    void rejectsWrongInternalKey() throws Exception {
        mvc.perform(post("/internal/v1/gmail/backfills")
                        .header("X-Internal-Service-Key", "wrong")
                        .header("Idempotency-Key", "idem-1")
                        .param("tenantId", "tenant-1").param("userId", "user-1")
                        .contentType("application/json")
                        .content("{\"connectionId\":\"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void startsOwnedBackfillAsynchronouslyWithLocation() throws Exception {
        when(service.start(any(), eq(new BackfillOwnerContext("tenant-1", "user-1")), eq("idem-1")))
                .thenReturn(record(BackfillRunStatus.QUEUED));

        mvc.perform(post("/internal/v1/gmail/backfills")
                        .header("X-Internal-Service-Key", "test-key")
                        .header("Idempotency-Key", "idem-1")
                        .header("X-Correlation-Id", "corr-1")
                        .param("tenantId", "tenant-1").param("userId", "user-1")
                        .contentType("application/json")
                        .content("{\"connectionId\":\"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa\",\"from\":\"2026-06-22T00:00:00Z\",\"to\":\"2026-08-21T00:00:00Z\",\"mode\":\"AUTOMATIC\"}"))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", "/internal/v1/gmail/backfills/" + RUN_ID));

        verify(service).start(any(), eq(new BackfillOwnerContext("tenant-1", "user-1")), eq("idem-1"));
    }

    @Test
    void scopesStatusAndLifecycleCommandsToOwner() throws Exception {
        when(service.status(RUN_ID, new BackfillOwnerContext("tenant-1", "user-1"))).thenReturn(record(BackfillRunStatus.RUNNING));
        when(service.pause(RUN_ID, new BackfillOwnerContext("tenant-1", "user-1"))).thenReturn(record(BackfillRunStatus.PAUSING));
        when(service.resume(RUN_ID, new BackfillOwnerContext("tenant-1", "user-1"))).thenReturn(record(BackfillRunStatus.RUNNING));
        when(service.cancel(RUN_ID, new BackfillOwnerContext("tenant-1", "user-1"))).thenReturn(record(BackfillRunStatus.CANCELLING));

        mvc.perform(get("/internal/v1/gmail/backfills/" + RUN_ID).header("X-Internal-Service-Key", "test-key").param("tenantId", "tenant-1").param("userId", "user-1")).andExpect(status().isOk());
        mvc.perform(post("/internal/v1/gmail/backfills/" + RUN_ID + "/pause").header("X-Internal-Service-Key", "test-key").param("tenantId", "tenant-1").param("userId", "user-1")).andExpect(status().isAccepted());
        mvc.perform(post("/internal/v1/gmail/backfills/" + RUN_ID + "/resume").header("X-Internal-Service-Key", "test-key").param("tenantId", "tenant-1").param("userId", "user-1")).andExpect(status().isAccepted());
        mvc.perform(post("/internal/v1/gmail/backfills/" + RUN_ID + "/cancel").header("X-Internal-Service-Key", "test-key").param("tenantId", "tenant-1").param("userId", "user-1")).andExpect(status().isAccepted());

        verify(service).status(RUN_ID, new BackfillOwnerContext("tenant-1", "user-1"));
        verify(service).pause(RUN_ID, new BackfillOwnerContext("tenant-1", "user-1"));
        verify(service).resume(RUN_ID, new BackfillOwnerContext("tenant-1", "user-1"));
        verify(service).cancel(RUN_ID, new BackfillOwnerContext("tenant-1", "user-1"));
    }

    @Test
    void rejectsMissingIdempotencyKey() throws Exception {
        mvc.perform(post("/internal/v1/gmail/backfills")
                        .header("X-Internal-Service-Key", "test-key")
                        .param("tenantId", "tenant-1").param("userId", "user-1")
                        .contentType("application/json")
                        .content("{\"connectionId\":\"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa\"}"))
                .andExpect(status().isUnauthorized());
    }

    private static BackfillRunRecord record(BackfillRunStatus status) {
        return new BackfillRunRecord(RUN_ID, "tenant-1", "user-1", CONNECTION_ID, BackfillMode.AUTOMATIC, status,
                Instant.parse("2026-06-22T00:00:00Z"), Instant.parse("2026-08-21T00:00:00Z"), 10, 1, 0, 4);
    }
}
