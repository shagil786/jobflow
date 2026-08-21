package dev.jobflow.jobs;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestControllerAdvice
public class ApiExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException exception) {
    Map<String, String> fields = exception.getBindingResult().getFieldErrors().stream().collect(java.util.stream.Collectors.toMap(error -> error.getField(), error -> error.getDefaultMessage() == null ? "invalid" : error.getDefaultMessage(), (first, ignored) -> first));
    return ResponseEntity.badRequest().body(error("VALIDATION_ERROR", "Request validation failed", fields));
  }

  @ExceptionHandler(JobService.NotFoundException.class)
  ResponseEntity<ErrorResponse> notFound(JobService.NotFoundException exception) { return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error("NOT_FOUND", exception.getMessage(), Map.of())); }

  @ExceptionHandler({JobService.InvalidRequestException.class, java.time.format.DateTimeParseException.class})
  ResponseEntity<ErrorResponse> invalid(Exception exception) { return ResponseEntity.badRequest().body(error("INVALID_REQUEST", "The request contains an unsupported value", Map.of())); }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ErrorResponse> unexpected(Exception exception) { log.error("Unhandled job-service request failure", exception); return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error("INTERNAL_ERROR", "The request could not be completed", Map.of())); }

  private ErrorResponse error(String code, String message, Map<String, String> fields) {
    return new ErrorResponse(new ApiError(code, message, fields), new Meta(java.util.UUID.randomUUID().toString()));
  }

  record ErrorResponse(ApiError error, Meta meta) {}
  record ApiError(String code, String message, Map<String, String> details) {}
  record Meta(String requestId) {}
}
