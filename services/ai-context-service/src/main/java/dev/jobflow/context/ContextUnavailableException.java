package dev.jobflow.context;

public class ContextUnavailableException extends RuntimeException {
    public ContextUnavailableException(String message) { super(message); }
    public ContextUnavailableException(String message, Throwable cause) { super(message, cause); }
}
