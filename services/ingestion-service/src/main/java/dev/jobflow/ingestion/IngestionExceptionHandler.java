package dev.jobflow.ingestion;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.MissingRequestHeaderException;

@RestControllerAdvice
class IngestionExceptionHandler {
    @ExceptionHandler(UnknownGmailConnectionException.class)
    ResponseEntity<ApiError> handleUnknownGmailConnection(HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(error("GMAIL_CONNECTION_NOT_FOUND", "Gmail connection not found", request));
    }

    @ExceptionHandler(InvalidInternalServiceKeyException.class)
    ResponseEntity<ApiError> handleInvalidInternalServiceKey(HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(error("INTERNAL_AUTHENTICATION_FAILED", "Internal service authentication failed", request));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    ResponseEntity<ApiError> handleMissingRequestHeader(MissingRequestHeaderException error, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(error("INTERNAL_AUTHENTICATION_FAILED", "Internal service authentication failed", request));
    }

    @ExceptionHandler(GmailFetchException.class)
    ResponseEntity<ApiError> handleGmailFetch(HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(error("GMAIL_FETCH_FAILED", "Gmail fetch failed", request));
    }

    private static ApiError error(String code, String message, HttpServletRequest request) {
        return new ApiError(code, message, request.getHeader("X-Request-Id"));
    }

    record ApiError(String code, String message, String requestId) {}
}
