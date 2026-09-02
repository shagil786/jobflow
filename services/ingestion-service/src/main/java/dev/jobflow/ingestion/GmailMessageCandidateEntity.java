package dev.jobflow.ingestion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "gmail_message_candidates")
class GmailMessageCandidateEntity {
    enum State { PENDING, ENRICHING, READY_FOR_REVIEW, CONFIRMED, DISMISSED, EXPIRED }

    @Id private UUID candidateId;
    @Column(nullable = false) private UUID connectionId;
    @Column(nullable = false, length = 120) private String tenantId;
    @Column(nullable = false, length = 120) private String userId;
    @Column(nullable = false, length = 255) private String providerMessageId;
    @Column(length = 255) private String threadId;
    private UUID runId;
    private UUID batchId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private State state;
    @Column(nullable = false) private double deterministicScore;
    @Column(nullable = false, columnDefinition = "text") private String deterministicSignalsJson;
    @Column(nullable = false) private Instant discoveredAt;
    private Instant expiresAt;

    protected GmailMessageCandidateEntity() {}

    static GmailMessageCandidateEntity pending(
            GmailMessageMetadata message, UUID runId, UUID batchId, GmailCandidateFilter.CandidateDecision decision, Instant now) {
        GmailMessageCandidateEntity entity = new GmailMessageCandidateEntity();
        entity.candidateId = UUID.randomUUID();
        entity.connectionId = message.connectionId();
        entity.tenantId = message.tenantId();
        entity.userId = message.userId();
        entity.providerMessageId = message.messageId();
        entity.threadId = message.threadId();
        entity.runId = runId;
        entity.batchId = batchId;
        entity.state = decision.state();
        entity.deterministicScore = decision.score();
        entity.deterministicSignalsJson = decision.signalsJson();
        entity.discoveredAt = now;
        return entity;
    }

    void refresh(GmailCandidateFilter.CandidateDecision decision, UUID runId, UUID batchId, Instant now) {
        if (state == State.CONFIRMED || state == State.DISMISSED) return;
        state = decision.state();
        deterministicScore = decision.score();
        deterministicSignalsJson = decision.signalsJson();
        this.runId = runId;
        this.batchId = batchId;
    }

    UUID getCandidateId() { return candidateId; }
    String getProviderMessageId() { return providerMessageId; }
    String getThreadId() { return threadId; }
    State getState() { return state; }
}
