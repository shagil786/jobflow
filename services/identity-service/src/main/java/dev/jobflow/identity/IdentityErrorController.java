package dev.jobflow.identity;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class IdentityErrorController implements ErrorController {
    @RequestMapping("/error")
    ResponseEntity<Map<String, Object>> error(HttpServletRequest request) {
        Object rawStatus = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int statusCode = rawStatus instanceof Integer value ? value : 500;
        HttpStatus status = HttpStatus.resolve(statusCode);
        if (status == null) status = HttpStatus.INTERNAL_SERVER_ERROR;
        String code = status == HttpStatus.NOT_FOUND ? "NOT_FOUND" : "REQUEST_FAILED";
        String message = status == HttpStatus.NOT_FOUND ? "the requested resource was not found" : "the request could not be completed";
        return ResponseEntity.status(status).body(Map.of(
                "error", Map.of("code", code, "message", message),
                "meta", Map.of("requestId", UUID.randomUUID().toString())));
    }
}
