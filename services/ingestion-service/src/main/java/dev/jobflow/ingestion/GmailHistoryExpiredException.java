package dev.jobflow.ingestion;

public class GmailHistoryExpiredException extends RuntimeException {
    public GmailHistoryExpiredException() { super("Gmail history cursor expired"); }
}
