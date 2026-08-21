package dev.jobflow.ingestion;

public class GmailFetchException extends RuntimeException {
    public GmailFetchException(Throwable cause) {
        super(cause);
    }
}
