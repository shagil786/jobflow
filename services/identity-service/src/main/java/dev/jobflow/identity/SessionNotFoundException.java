package dev.jobflow.identity;

public class SessionNotFoundException extends RuntimeException {
    public SessionNotFoundException(String sessionId) { super("session not found: " + sessionId); }
}
