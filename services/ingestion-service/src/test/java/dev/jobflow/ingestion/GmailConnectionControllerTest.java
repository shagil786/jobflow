package dev.jobflow.ingestion;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = GmailConnectionController.class, properties = "JOBFLOW_INTERNAL_SERVICE_KEY=test-key")
class GmailConnectionControllerTest {
    @Autowired
    private MockMvc mvc;

    @MockBean
    private GmailConnectionService service;

    @MockBean
    private GmailSyncService syncService;

    @Test
    void rejectsWrongInternalKey() throws Exception {
        mvc.perform(post("/internal/v1/gmail/connections/00000000-0000-0000-0000-000000000000/sync")
                        .header("X-Internal-Service-Key", "wrong")
                        .header("X-Request-Id", "request-auth"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json("{\"code\":\"INTERNAL_AUTHENTICATION_FAILED\",\"message\":\"Internal service authentication failed\",\"requestId\":\"request-auth\"}"));
    }

    @Test
    void rejectsMissingInternalKeyWithAControlledClientError() throws Exception {
        mvc.perform(post("/internal/v1/gmail/connections/00000000-0000-0000-0000-000000000000/sync"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json("{\"code\":\"INTERNAL_AUTHENTICATION_FAILED\",\"message\":\"Internal service authentication failed\"}"));
    }

    @Test
    void returnsNotFoundForUnknownConnectionsWithoutLeakingOwnership() throws Exception {
        UUID connectionId = UUID.fromString("00000000-0000-0000-0000-000000000123");
        when(syncService.sync(connectionId)).thenThrow(new UnknownGmailConnectionException());

        mvc.perform(post("/internal/v1/gmail/connections/" + connectionId + "/sync")
                        .header("X-Internal-Service-Key", "test-key"))
                .andExpect(status().isNotFound())
                .andExpect(content().json("{\"code\":\"GMAIL_CONNECTION_NOT_FOUND\",\"message\":\"Gmail connection not found\"}"))
                .andExpect(content().string(not(containsString("owner"))))
                .andExpect(content().string(not(containsString("tenant"))));
    }

    @Test
    void returnsNotFoundForUnknownConnectionsOnCursorWithoutLeakingOwnership() throws Exception {
        UUID connectionId = UUID.fromString("00000000-0000-0000-0000-000000000123");
        doThrow(new UnknownGmailConnectionException())
                .when(service).advanceCursor(connectionId, new SyncCursor("history-8", "page-2"));

        mvc.perform(post("/internal/v1/gmail/connections/" + connectionId + "/cursor")
                        .header("X-Internal-Service-Key", "test-key")
                        .contentType("application/json")
                        .content("{\"historyId\":\"history-8\",\"pageToken\":\"page-2\"}"))
                .andExpect(status().isNotFound())
                .andExpect(content().json("{\"code\":\"GMAIL_CONNECTION_NOT_FOUND\",\"message\":\"Gmail connection not found\"}"))
                .andExpect(content().string(not(containsString("owner"))))
                .andExpect(content().string(not(containsString("tenant"))));
    }

    @Test
    void returnsSafeEnvelopeForGmailFetchFailures() throws Exception {
        UUID connectionId = UUID.fromString("00000000-0000-0000-0000-000000000123");
        when(syncService.sync(connectionId)).thenThrow(new GmailFetchException(
                new IllegalStateException("token=secret gmail response body")));

        mvc.perform(post("/internal/v1/gmail/connections/" + connectionId + "/sync")
                        .header("X-Internal-Service-Key", "test-key")
                        .header("X-Request-Id", "request-fetch"))
                .andExpect(status().isBadGateway())
                .andExpect(content().json("{\"code\":\"GMAIL_FETCH_FAILED\",\"message\":\"Gmail fetch failed\",\"requestId\":\"request-fetch\"}"))
                .andExpect(content().string(not(containsString("secret"))))
                .andExpect(content().string(not(containsString("response body"))));
    }

    @Test
    void returnsActionableLabelGuidanceWithoutLeakingProviderData() throws Exception {
        UUID connectionId = UUID.fromString("00000000-0000-0000-0000-000000000123");
        when(syncService.sync(connectionId)).thenThrow(new GmailTrackLabelNotFoundException());

        mvc.perform(post("/internal/v1/gmail/connections/" + connectionId + "/sync")
                        .header("X-Internal-Service-Key", "test-key"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().json("{\"code\":\"GMAIL_TRACK_LABEL_NOT_FOUND\",\"message\":\"Create the Gmail label JobFlow/Track, then sync again\"}"));
    }
}
