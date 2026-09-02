package dev.jobflow.ingestion;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/internal/v1/gmail/drafts")
class GmailDraftController {
  private final GmailConnectionStore connections; private final GmailTokenCipher cipher; private final GmailApiClient gmail; private final String key;
  GmailDraftController(GmailConnectionStore connections, GmailTokenCipher cipher, GmailApiClient gmail, @Value("${JOBFLOW_INTERNAL_SERVICE_KEY}") String key) { this.connections=connections; this.cipher=cipher; this.gmail=gmail; this.key=key; }
  @PostMapping
  DraftResponse create(@RequestHeader("X-Internal-Service-Key") String provided, @RequestBody DraftRequest request) {
    authorize(provided); if (request.tenantId()==null||request.userId()==null||request.recipient()==null||request.subject()==null||request.body()==null) throw new IllegalArgumentException("draft fields are required");
    var connection=connections.findActiveByOwner(request.tenantId(),request.userId()).or(()->connections.findByOwner(request.tenantId(),request.userId())).orElseThrow(UnknownGmailConnectionException::new);
    String token=gmail.refreshAccessToken(cipher.decrypt(connection.refreshTokenCiphertext())).value();
    return new DraftResponse(gmail.createDraft(token,request.recipient(),request.subject(),request.body()),false);
  }
  private void authorize(String provided){if(provided==null||!MessageDigest.isEqual(key.getBytes(StandardCharsets.UTF_8),provided.getBytes(StandardCharsets.UTF_8)))throw new InvalidInternalServiceKeyException();}
  record DraftRequest(String tenantId,String userId,String recipient,String subject,String body){}
  record DraftResponse(String gmailDraftId,boolean sent){}
}
