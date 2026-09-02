package dev.jobflow.ingestion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

enum PromotionOutboxStatus { PENDING, SUCCEEDED, DEAD_LETTERED }

@Entity
@Table(name = "gmail_application_promotion_outbox")
class ApplicationPromotionOutboxEntity {
    @Id private UUID outboxId;
    @Column(nullable = false, length = 120) private String tenantId;
    @Column(nullable = false, length = 120) private String userId;
    @Column(nullable = false) private UUID batchId;
    @Column(nullable = false, unique = true, length = 255) private String suggestionId;
    @Column(nullable = false, length = 255) private String messageId;
    @Column(length = 255) private String threadId;
    @Column(length = 16) private String direction;
    @Column(length = 255) private String company;
    @Column(nullable = false, length = 255) private String role;
    @Column(nullable = false, length = 64) private String intent;
    @Column(length = 32) private String applicationDate;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private PromotionOutboxStatus status;
    @Column(nullable = false) private int attempts;
    @Column(nullable = false) private Instant nextAttemptAt;
    @Column(length = 512) private String lastError;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;

    protected ApplicationPromotionOutboxEntity() {}

    static ApplicationPromotionOutboxEntity pending(ClassificationSuggestionV1 suggestion, UUID batchId, Instant now) {
        ApplicationPromotionOutboxEntity value = new ApplicationPromotionOutboxEntity();
        value.outboxId = UUID.randomUUID(); value.tenantId = suggestion.tenantId(); value.userId = suggestion.userId();
        value.batchId = batchId; value.suggestionId = suggestion.suggestionId(); value.messageId = suggestion.messageId();
        value.threadId = suggestion.threadId(); value.direction = suggestion.direction() == null ? null : suggestion.direction().name();
        value.company = suggestion.company() == null ? null : suggestion.company().value();
        value.role = suggestion.role().value(); value.intent = suggestion.intent().name();
        value.applicationDate = suggestion.applicationDate() == null ? null : suggestion.applicationDate().value();
        value.status = PromotionOutboxStatus.PENDING; value.nextAttemptAt = now; value.createdAt = now; value.updatedAt = now;
        return value;
    }

    UUID outboxId() { return outboxId; }
    UUID batchId() { return batchId; }
    String tenantId() { return tenantId; }
    String userId() { return userId; }
    String suggestionId() { return suggestionId; }
    String messageId() { return messageId; }
    String threadId() { return threadId; }
    String direction() { return direction; }
    String company() { return company; }
    String role() { return role; }
    String intent() { return intent; }
    String applicationDate() { return applicationDate; }
    PromotionOutboxStatus status() { return status; }
    int attempts() { return attempts; }
    void succeeded(Instant now) { status = PromotionOutboxStatus.SUCCEEDED; updatedAt = now; lastError = null; }
    void retry(String error, Instant now) {
        attempts++;
        if (attempts >= 8) status = PromotionOutboxStatus.DEAD_LETTERED;
        lastError = error == null ? "promotion failed" : error.substring(0, Math.min(512, error.length()));
        nextAttemptAt = now.plusSeconds(Math.min(3600, 15L * (1L << Math.min(attempts, 7)))); updatedAt = now;
    }
}
