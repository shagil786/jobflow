package dev.jobflow.ingestion;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
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
                        .header("X-Internal-Service-Key", "wrong"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsMissingInternalKeyWithAControlledClientError() throws Exception {
        mvc.perform(post("/internal/v1/gmail/connections/00000000-0000-0000-0000-000000000000/sync"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsNotFoundForUnknownConnectionsWithoutLeakingOwnership() throws Exception {
        UUID connectionId = UUID.fromString("00000000-0000-0000-0000-000000000123");
        when(syncService.sync(connectionId)).thenThrow(new IllegalArgumentException("Gmail connection not found"));

        mvc.perform(post("/internal/v1/gmail/connections/" + connectionId + "/sync")
                        .header("X-Internal-Service-Key", "test-key"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString("owner"))))
                .andExpect(content().string(not(containsString("tenant"))));
    }
}
