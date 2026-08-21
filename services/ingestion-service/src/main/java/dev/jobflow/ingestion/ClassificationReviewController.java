package dev.jobflow.ingestion;

import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/classification-suggestions")
class ClassificationReviewController {
    private final ClassificationReviewService service;
    private final String key;

    ClassificationReviewController(ClassificationReviewService service, @Value("${JOBFLOW_INTERNAL_SERVICE_KEY}") String key) {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("JOBFLOW_INTERNAL_SERVICE_KEY is required");
        this.service = service;
        this.key = key;
    }

    @GetMapping("/review-queue")
    List<ClassificationSuggestionRecord> queue(@RequestHeader("X-Internal-Service-Key") String provided, @RequestParam("tenantId") String tenantId, @RequestParam("userId") String userId) {
        authorize(provided);
        return service.queue(tenantId, userId);
    }

    @PostMapping("/{suggestionId}/review")
    @ResponseStatus(HttpStatus.CREATED)
    ClassificationReviewResponse review(@RequestHeader("X-Internal-Service-Key") String provided, @PathVariable("suggestionId") String suggestionId, @RequestParam("tenantId") String tenantId, @RequestParam("userId") String userId, @Valid @RequestBody ClassificationReviewRequest request) {
        authorize(provided);
        return service.review(tenantId, userId, suggestionId, request);
    }

    private void authorize(String provided) { if (provided == null || !MessageDigest.isEqual(key.getBytes(StandardCharsets.UTF_8), provided.getBytes(StandardCharsets.UTF_8))) throw new InvalidInternalServiceKeyException(); }
}
