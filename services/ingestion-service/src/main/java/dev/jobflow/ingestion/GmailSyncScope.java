package dev.jobflow.ingestion;

public final class GmailSyncScope {
    public static final String TRACK_LABEL = "JobFlow/Track";

    private GmailSyncScope() {}

    public static String queryForLabel(String label) {
        if (!TRACK_LABEL.equals(label)) {
            throw new IllegalArgumentException("Gmail sync is restricted to the JobFlow/Track label");
        }
        return "label:" + TRACK_LABEL;
    }
}
