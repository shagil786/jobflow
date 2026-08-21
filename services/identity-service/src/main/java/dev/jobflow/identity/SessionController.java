package dev.jobflow.identity;

import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/v1/sessions")
public class SessionController {
    private final SessionService service;
    private final String internalServiceKey;

    public SessionController(SessionService service, @Value("${JOBFLOW_INTERNAL_SERVICE_KEY}") String internalServiceKey) {
        this.service = service;
        this.internalServiceKey = internalServiceKey;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SessionRecord create(@RequestHeader("X-Internal-Service-Key") String key,
            @RequestBody CreateSessionRequest request) {
        authorize(key);
        SessionRecord created = service.create(new CreateSessionCommand(request.userId(), request.tenantId(), request.provider(),
                request.accessToken(), request.refreshToken(), request.accessTokenExpiresAt()));
        return created;
    }

    @PostMapping("/{sessionId}/access-token")
    public AccessTokenResult accessToken(@RequestHeader("X-Internal-Service-Key") String key,
            @PathVariable("sessionId") String sessionId) {
        authorize(key);
        return service.accessToken(sessionId);
    }

    @PostMapping("/{sessionId}/revoke")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@RequestHeader("X-Internal-Service-Key") String key,
            @PathVariable("sessionId") String sessionId) {
        authorize(key);
        service.revoke(sessionId);
    }

    @PostMapping("/{sessionId}/metadata")
    public SessionMetadata metadata(@RequestHeader("X-Internal-Service-Key") String key,
            @PathVariable("sessionId") String sessionId) {
        authorize(key);
        return service.metadata(sessionId);
    }

    private void authorize(String key) {
        if (internalServiceKey == null || internalServiceKey.isBlank() || !internalServiceKey.equals(key)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "internal authentication failed");
        }
    }

    public record CreateSessionRequest(String userId, String tenantId, String provider, String accessToken,
            String refreshToken, Instant accessTokenExpiresAt) {}
}
