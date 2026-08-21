package dev.jobflow.ingestion;

public record SyncCursor(String historyId, String pageToken) {
    public SyncCursor {
        if (historyId == null || historyId.isBlank()) throw new IllegalArgumentException("historyId is required");
    }

    public boolean requiresFullResync(String currentHistoryId) {
        return currentHistoryId == null || !currentHistoryId.equals(historyId);
    }
}
