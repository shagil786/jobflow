package dev.jobflow.ingestion;

import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@EnableScheduling
class ApplicationPromotionOutboxService {
    private static final Logger log = LoggerFactory.getLogger(ApplicationPromotionOutboxService.class);
    private final ApplicationPromotionOutboxRepository outbox;
    private final JobPromotionClient client;

    ApplicationPromotionOutboxService(ApplicationPromotionOutboxRepository outbox, JobPromotionClient client) {
        this.outbox = outbox; this.client = client;
    }

    @Transactional
    void enqueue(ClassificationSuggestionV1 suggestion, UUID batchId) {
        if (outbox.findBySuggestionId(suggestion.suggestionId()).isEmpty()) {
            outbox.save(ApplicationPromotionOutboxEntity.pending(suggestion, batchId, Instant.now()));
        }
    }

    @Scheduled(fixedDelayString = "${JOBFLOW_PROMOTION_RETRY_DELAY_MS:5000}")
    void processDue() {
        for (ApplicationPromotionOutboxEntity item : outbox.findTop100ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(PromotionOutboxStatus.PENDING, Instant.now())) {
            process(item);
        }
    }

    @Transactional
    void process(ApplicationPromotionOutboxEntity item) {
        if (item.status() != PromotionOutboxStatus.PENDING) return;
        try {
            client.promote(item.tenantId(), item.userId(), item.suggestionId(), item.messageId(), item.threadId(), item.direction(), item.company(), item.role(), item.intent(), item.applicationDate());
            item.succeeded(Instant.now()); outbox.save(item);
            outbox.recordPromotionSuccess(item.batchId(), Instant.now());
        } catch (RuntimeException exception) {
            item.retry(exception.getClass().getSimpleName(), Instant.now()); outbox.save(item);
            log.warn("Gmail application promotion retry scheduled suggestion={} attempt={} status={}", item.suggestionId(), item.attempts(), item.status());
        }
    }
}
