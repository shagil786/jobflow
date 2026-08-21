package dev.jobflow.ingestion;

import java.time.Instant;
import java.util.UUID;

record ClassificationReviewResponse(
        ClassificationSuggestionRecord suggestion,
        UUID reviewId,
        ClassificationReviewDecision decision,
        String company,
        String role,
        String applicationDate,
        String contact,
        Instant reviewedAt) {}
