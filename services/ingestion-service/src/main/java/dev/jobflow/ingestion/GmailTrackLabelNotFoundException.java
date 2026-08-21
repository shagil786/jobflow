package dev.jobflow.ingestion;

public class GmailTrackLabelNotFoundException extends RuntimeException {
    public GmailTrackLabelNotFoundException() {
        super("Gmail label JobFlow/Track was not found");
    }
}
