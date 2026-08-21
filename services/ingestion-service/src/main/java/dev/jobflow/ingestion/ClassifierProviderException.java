package dev.jobflow.ingestion;

class ClassifierProviderException extends RuntimeException {
    ClassifierProviderException(String message) {
        super(message);
    }

    ClassifierProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
