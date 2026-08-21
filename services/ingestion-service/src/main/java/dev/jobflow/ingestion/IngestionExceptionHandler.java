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

    @ExceptionHandler(GmailTrackLabelNotFoundException.class)
    ResponseEntity<ApiError> handleMissingTrackLabel(HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(error("GMAIL_TRACK_LABEL_NOT_FOUND", "Create the Gmail label JobFlow/Track, then sync again", request));
    }

    @ExceptionHandler(UnknownClassificationSuggestionException.class)
    ResponseEntity<ApiError> handleUnknownSuggestion(HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error("CLASSIFICATION_SUGGESTION_NOT_FOUND", "Classification suggestion not found", request));
    }

    @ExceptionHandler(ClassificationReviewStateException.class)
    ResponseEntity<ApiError> handleReviewState(ClassificationReviewStateException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error("CLASSIFICATION_REVIEW_CONFLICT", exception.getMessage(), request));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException exception, HttpServletRequest request) {
        String code = exception.getMessage() == null ? "INVALID_REQUEST" : exception.getMessage();
        HttpStatus status = switch (code) {
            case "GMAIL_BACKFILL_ALREADY_ACTIVE" -> HttpStatus.CONFLICT;
            case "GMAIL_CONNECTION_NOT_FOUND", "GMAIL_BACKFILL_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status).body(error(code, safeMessage(code), request));
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ApiError> handleIllegalState(IllegalStateException exception, HttpServletRequest request) {
        String code = exception.getMessage() == null ? "INVALID_STATE" : exception.getMessage();
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error(code, safeMessage(code), request));
    }

    private static String safeMessage(String code) {
        return switch (code) {
            case "GMAIL_BACKFILL_ALREADY_ACTIVE" -> "A Gmail backfill is already active";
            case "GMAIL_CONNECTION_NOT_FOUND" -> "Gmail connection not found";
            case "GMAIL_BACKFILL_NOT_FOUND" -> "Gmail backfill not found";
            case "IDEMPOTENCY_KEY_REQUIRED" -> "Idempotency-Key is required";
            case "BACKFILL_OWNER_REQUIRED" -> "Backfill owner is required";
            case "REVIEW_ITEMS_CURSOR_INVALID" -> "Review items cursor is invalid";
            case "REVIEW_ITEMS_LIMIT_INVALID" -> "Review items limit must be between 1 and 100";
            default -> "The request could not be completed";
        };
    }

    private static ApiError error(String code, String message, HttpServletRequest request) {
        return new ApiError(code, message, request.getHeader("X-Request-Id"));
    }

    record ApiError(String code, String message, String requestId) {}
}
