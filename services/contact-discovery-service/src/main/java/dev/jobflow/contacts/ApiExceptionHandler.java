package dev.jobflow.contacts;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestControllerAdvice
public class ApiExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
  @ExceptionHandler(ContactDiscoveryService.NotFoundException.class) ResponseEntity<ErrorResponse> notFound(Exception e) { return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error("NOT_FOUND", e.getMessage())); }
  @ExceptionHandler(ContactDiscoveryService.InvalidRequestException.class) ResponseEntity<ErrorResponse> invalid(Exception e) { return ResponseEntity.badRequest().body(error("INVALID_REQUEST", e.getMessage())); }
  @ExceptionHandler(Exception.class) ResponseEntity<ErrorResponse> unexpected(Exception e) { log.error("Unhandled contact-discovery request failure", e); return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error("INTERNAL_ERROR", "The request could not be completed")); }
  private ErrorResponse error(String code, String message) { return new ErrorResponse(new ApiError(code, message, Map.of()), new Meta(java.util.UUID.randomUUID().toString())); }
  record ErrorResponse(ApiError error, Meta meta) {} record ApiError(String code, String message, Map<String, String> details) {} record Meta(String requestId) {}
}
