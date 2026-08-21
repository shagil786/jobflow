package dev.jobflow.ingestion;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class IngestionExceptionHandler {
    @ExceptionHandler(UnknownGmailConnectionException.class)
    ResponseEntity<ApiError> handleUnknownGmailConnection() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("GMAIL_CONNECTION_NOT_FOUND", "Gmail connection not found"));
    }

    record ApiError(String code, String message) {}
}
