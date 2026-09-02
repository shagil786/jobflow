package dev.jobflow.ingestion;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/internal/v1/gmail")
class GmailBackfillController {
    private static final Logger log = LoggerFactory.getLogger(GmailBackfillController.class);
    private final GmailBackfillService backfills;
    private final ClassificationReviewService reviews;
    private final GmailThreadViewService threads;
    private final String key;

    GmailBackfillController(GmailBackfillService backfills, ClassificationReviewService reviews, GmailThreadViewService threads,
            @Value("${JOBFLOW_INTERNAL_SERVICE_KEY}") String key) {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("JOBFLOW_INTERNAL_SERVICE_KEY is required");
        this.backfills = backfills;
        this.reviews = reviews;
        this.threads = threads;
        this.key = key;
    }

    @PostMapping("/backfills")
    ResponseEntity<BackfillRunRecord> start(
            @RequestHeader("X-Internal-Service-Key") String provided,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String ignoredCorrelationId,
            @RequestParam("tenantId") String tenantId,
            @RequestParam("userId") String userId,
            @Valid @RequestBody BackfillHttpRequest request) {
        authorize(provided);
        BackfillRunRecord record = backfills.start(request.toCommand(), new BackfillOwnerContext(tenantId, userId), idempotencyKey);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .header("Location", "/internal/v1/gmail/backfills/" + record.runId())
                .body(record);
    }

    @GetMapping("/backfills/{runId}")
    BackfillRunRecord status(@RequestHeader("X-Internal-Service-Key") String provided,
            @PathVariable("runId") UUID runId, @RequestParam("tenantId") String tenantId, @RequestParam("userId") String userId) {
        authorize(provided);
        return backfills.status(runId, new BackfillOwnerContext(tenantId, userId));
    }

    @PostMapping("/backfills/{runId}/pause")
    ResponseEntity<BackfillRunRecord> pause(@RequestHeader("X-Internal-Service-Key") String provided,
            @PathVariable("runId") UUID runId, @RequestParam("tenantId") String tenantId, @RequestParam("userId") String userId) {
        authorize(provided);
        return accepted(backfills.pause(runId, owner(tenantId, userId)));
    }

    @PostMapping("/backfills/{runId}/resume")
    ResponseEntity<BackfillRunRecord> resume(@RequestHeader("X-Internal-Service-Key") String provided,
            @PathVariable("runId") UUID runId, @RequestParam("tenantId") String tenantId, @RequestParam("userId") String userId) {
        authorize(provided);
        return accepted(backfills.resume(runId, owner(tenantId, userId)));
    }

    @PostMapping("/backfills/{runId}/cancel")
    ResponseEntity<BackfillRunRecord> cancel(@RequestHeader("X-Internal-Service-Key") String provided,
            @PathVariable("runId") UUID runId, @RequestParam("tenantId") String tenantId, @RequestParam("userId") String userId) {
        authorize(provided);
        return accepted(backfills.cancel(runId, owner(tenantId, userId)));
    }

    @GetMapping("/review-items")
    ReviewItems reviewItems(@RequestHeader("X-Internal-Service-Key") String provided,
            @RequestParam("tenantId") String tenantId, @RequestParam("userId") String userId,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "limit", required = false, defaultValue = "50") int limit) {
        authorize(provided);
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("REVIEW_ITEMS_LIMIT_INVALID");
        List<ClassificationSuggestionRecord> all = reviews.queue(tenantId, userId);
        int offset = parseCursor(cursor, all.size());
        List<ClassificationSuggestionRecord> items = all.stream().skip(offset).limit(limit).toList();
        String nextCursor = offset + items.size() < all.size() ? Integer.toString(offset + items.size()) : null;
        List<String> rejectedCompanies = all.stream()
                .filter(record -> record.suggestion().intent() == MessageIntent.REJECTION)
                .map(record -> record.suggestion().company())
                .filter(candidate -> candidate != null && candidate.value() != null && !candidate.value().isBlank())
                .map(candidate -> candidate.value().trim())
                .distinct()
                .toList();
        return new ReviewItems(items, nextCursor, rejectedCompanies);
    }

    @GetMapping("/threads/{threadId}")
    ThreadView thread(@RequestHeader("X-Internal-Service-Key") String provided,
            @PathVariable("threadId") String threadId, @RequestParam("tenantId") String tenantId, @RequestParam("userId") String userId) {
        authorize(provided);
        try {
            return new ThreadView(threads.view(tenantId, userId, threadId));
        } catch (RuntimeException error) {
            log.warn("Gmail live thread fetch failed thread={} reason={}", threadId, error.getClass().getSimpleName());
            throw error;
        }
    }

    @GetMapping("/messages/{messageId}/thread")
    ThreadView messageThread(@RequestHeader("X-Internal-Service-Key") String provided,
            @PathVariable("messageId") String messageId, @RequestParam("tenantId") String tenantId, @RequestParam("userId") String userId) {
        authorize(provided);
        try {
            return new ThreadView(threads.viewFromMessage(tenantId, userId, messageId));
        } catch (RuntimeException error) {
            log.warn("Gmail live message thread fetch failed message={} reason={}", messageId, error.getClass().getSimpleName());
            throw error;
        }
    }

    private static int parseCursor(String cursor, int size) {
        if (cursor == null || cursor.isBlank()) return 0;
        try {
            int offset = Integer.parseInt(cursor);
            if (offset < 0 || offset > size) throw new NumberFormatException();
            return offset;
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("REVIEW_ITEMS_CURSOR_INVALID");
        }
    }

    private ResponseEntity<BackfillRunRecord> accepted(BackfillRunRecord record) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(record);
    }

    private static BackfillOwnerContext owner(String tenantId, String userId) {
        return new BackfillOwnerContext(tenantId, userId);
    }

    private void authorize(String provided) {
        if (provided == null || !MessageDigest.isEqual(key.getBytes(StandardCharsets.UTF_8), provided.getBytes(StandardCharsets.UTF_8)))
            throw new InvalidInternalServiceKeyException();
    }

    record BackfillHttpRequest(@NotNull UUID connectionId, Instant from, Instant to, BackfillMode mode) {
        BackfillRequest toCommand() { return new BackfillRequest(connectionId, from, to, mode); }
    }
    record ReviewItems(List<ClassificationSuggestionRecord> items, String nextCursor, List<String> rejectedCompanies) {}
    record ThreadView(GmailThreadViewService.ThreadView thread) {}
}
