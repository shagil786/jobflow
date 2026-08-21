package dev.jobflow.ingestion;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class IngestionExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException error) {
        HttpStatus status = isNotFound(error.getMessage()) ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(new ApiError(error.getMessage()));
    }

    private static boolean isNotFound(String message) {
        return "Gmail connection not found".equals(message) || "Gmail message not found".equals(message);
    }

    record ApiError(String message) {}
}
