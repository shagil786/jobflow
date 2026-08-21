package dev.jobflow.ingestion;

public record EvidenceSpanV1(
        String tenantId,
        String userId,
        String evidenceId,
        String messageId,
        String threadId,
        String source,
        String quotedText,
        String normalizedTextHash,
        boolean sourceAvailable) {}
