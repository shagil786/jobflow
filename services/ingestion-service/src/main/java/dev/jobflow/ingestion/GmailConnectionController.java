package dev.jobflow.ingestion;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/internal/v1/gmail/connections")
public class GmailConnectionController {
    private final GmailConnectionService service;
    private final String key;
    public GmailConnectionController(GmailConnectionService service, GmailSyncService syncService, @Value("${JOBFLOW_INTERNAL_SERVICE_KEY}") String key) { this.service=service; this.syncService=syncService; if(key==null||key.isBlank()) throw new IllegalArgumentException("JOBFLOW_INTERNAL_SERVICE_KEY is required"); this.key=key; }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    GmailConnectionRecord connect(@RequestHeader("X-Internal-Service-Key") String provided, @RequestBody ConnectRequest request) { authorize(provided); return service.connect(new GmailConnectionCommand(request.userId(),request.tenantId(),request.email(),request.refreshToken(),request.historyId())); }
    @GetMapping("/status")
    GmailConnectionStatus status(@RequestHeader("X-Internal-Service-Key") String provided, @RequestParam("tenantId") String tenantId, @RequestParam("userId") String userId) { authorize(provided); return service.status(tenantId, userId); }
    @PostMapping("/{connectionId}/cursor")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void cursor(@RequestHeader("X-Internal-Service-Key") String provided, @PathVariable("connectionId") UUID connectionId, @RequestBody CursorRequest request) { authorize(provided); service.advanceCursor(connectionId,new SyncCursor(request.historyId(),request.pageToken())); }
    @PostMapping("/{connectionId}/sync")
    GmailSyncService.GmailSyncResult sync(@RequestHeader("X-Internal-Service-Key") String provided, @PathVariable("connectionId") UUID connectionId) { authorize(provided); return syncService.sync(connectionId); }
    private final GmailSyncService syncService;
    private void authorize(String provided) { if(provided==null || !MessageDigest.isEqual(key.getBytes(StandardCharsets.UTF_8),provided.getBytes(StandardCharsets.UTF_8))) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"internal authorization failed"); }
    record ConnectRequest(String userId,String tenantId,String email,String refreshToken,String historyId) {}
    record CursorRequest(String historyId,String pageToken) {}
}
