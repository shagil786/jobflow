package dev.jobflow.ingestion;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ClassificationReviewController.class, properties = "JOBFLOW_INTERNAL_SERVICE_KEY=test-key")
class ClassificationReviewControllerTest {
    @Autowired private MockMvc mvc;

    @MockBean private ClassificationReviewService service;

    @Test
    void rejectsWrongInternalKey() throws Exception {
        mvc.perform(get("/internal/v1/classification-suggestions/review-queue")
                        .param("tenantId", "tenant-1").param("userId", "user-1")
                        .header("X-Internal-Service-Key", "wrong"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json("{\"code\":\"INTERNAL_AUTHENTICATION_FAILED\",\"message\":\"Internal service authentication failed\"}"));
    }

    @Test
    void rejectsMissingInternalKey() throws Exception {
        mvc.perform(get("/internal/v1/classification-suggestions/review-queue")
                        .param("tenantId", "tenant-1").param("userId", "user-1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsOnlyTheScopedQueue() throws Exception {
        when(service.queue("tenant-1", "user-1")).thenReturn(List.of());

        mvc.perform(get("/internal/v1/classification-suggestions/review-queue")
                        .param("tenantId", "tenant-1").param("userId", "user-1")
                        .header("X-Internal-Service-Key", "test-key"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        verify(service).queue("tenant-1", "user-1");
    }

    @Test
    void validatesReviewDecisionBeforeCallingService() throws Exception {
        mvc.perform(post("/internal/v1/classification-suggestions/suggestion-1/review")
                        .param("tenantId", "tenant-1").param("userId", "user-1")
                        .header("X-Internal-Service-Key", "test-key")
                        .contentType("application/json")
                        .content("{\"decision\":null}"))
                .andExpect(status().isBadRequest());
    }
}
