package dev.jobflow.ingestion;

import java.util.UUID;

public record ClassificationSuggestionRecord(UUID connectionId, ClassificationSuggestionV1 suggestion) {}
