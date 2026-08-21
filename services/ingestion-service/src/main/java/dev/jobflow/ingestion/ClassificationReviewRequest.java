package dev.jobflow.ingestion;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

record ClassificationReviewRequest(
        @NotNull ClassificationReviewDecision decision,
        @Size(max = 240) String company,
        @Size(max = 240) String role,
        @Size(max = 64) String applicationDate,
        @Size(max = 320) String contact) {}
