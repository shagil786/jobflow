package dev.jobflow.ingestion;

public class UnknownGmailConnectionException extends RuntimeException {
    public UnknownGmailConnectionException() {
        super("Gmail connection not found");
    }
}
