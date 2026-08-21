package dev.jobflow.identity;

import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class SessionExceptionHandler {
    @ExceptionHandler(SessionNotFoundException.class)
    ResponseEntity<Map<String, Object>> notFound(SessionNotFoundException ignored) {
        return envelope(HttpStatus.UNAUTHORIZED, "SESSION_INVALID", "session is invalid or expired");
    }

    @ExceptionHandler({MissingRequestHeaderException.class, HttpMessageNotReadableException.class, IllegalArgumentException.class, MethodArgumentNotValidException.class})
    ResponseEntity<Map<String, Object>> badRequest(Exception ignored) {
        return envelope(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "the request is invalid");
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Map<String, Object>> status(ResponseStatusException exception) {
        HttpStatus status = HttpStatus.resolve(exception.getStatusCode().value());
        return envelope(status == null ? HttpStatus.INTERNAL_SERVER_ERROR : status, "INTERNAL_AUTHORIZATION_FAILED", "internal authorization failed");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, Object>> unexpected(Exception ignored) {
        return envelope(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "the request could not be completed");
    }

    private ResponseEntity<Map<String, Object>> envelope(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "error", Map.of("code", code, "message", message),
                "meta", Map.of("requestId", UUID.randomUUID().toString())));
    }
}
