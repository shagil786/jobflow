package dev.jobflow.ai;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@RestController @RequestMapping("/api/v1")
class DraftController {
  private final DraftService service; DraftController(DraftService service){this.service=service;}
  @GetMapping({"/resumes","/resumes/versions"}) List<ResumeResponse> resumes(@AuthenticationPrincipal Jwt p){return service.resumes(tenant(p),user(p)).stream().map(r->new ResumeResponse(r.getId(),r.getFilename(),r.getContentHash(),r.getCreatedAt())).toList();}
  @PostMapping(value={"/resumes","/resumes/versions"}, consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @ResponseStatus(HttpStatus.CREATED) ResumeResponse upload(@AuthenticationPrincipal Jwt p,@RequestPart("file") MultipartFile file){var r=service.upload(tenant(p),user(p),file);return new ResumeResponse(r.getId(),r.getFilename(),r.getContentHash(),r.getCreatedAt());}
  @PostMapping("/drafts") @ResponseStatus(HttpStatus.CREATED) DraftResponse create(@AuthenticationPrincipal Jwt p,@Valid @RequestBody CreatePayload r){return DraftResponse.from(service.create(tenant(p),user(p),new DraftService.CreateRequest(r.applicationId(),r.contactId(),r.resumeVersionId(),r.recipient(),r.contactName(),r.company(),r.role(),r.instructions())));}
  @GetMapping("/drafts/{id}") DraftResponse get(@AuthenticationPrincipal Jwt p,@PathVariable UUID id){return DraftResponse.from(service.get(tenant(p),user(p),id));}
  @PatchMapping("/drafts/{id}") DraftResponse edit(@AuthenticationPrincipal Jwt p,@PathVariable UUID id,@Valid @RequestBody EditPayload r){return DraftResponse.from(service.edit(tenant(p),user(p),id,r.subject(),r.body()));}
  @PostMapping("/drafts/{id}/persist-to-gmail") DraftResponse persist(@AuthenticationPrincipal Jwt p,@PathVariable UUID id){return DraftResponse.from(service.persist(tenant(p),user(p),id));}
  private static String user(Jwt p){if(p==null||p.getSubject()==null||p.getSubject().isBlank())throw new IllegalArgumentException("authenticated principal is required");return p.getSubject();}
  private static String tenant(Jwt p){if(p==null)throw new IllegalArgumentException("authenticated principal is required");String t=p.getClaimAsString("tenant_id");if(t==null||t.isBlank())t=p.getClaimAsString("https://jobflow.app/tenant_id");if(t!=null&&!t.isBlank())return t;try{return "personal:"+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(user(p).getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
  record ResumeResponse(UUID id,String filename,String contentHash,java.time.Instant createdAt){ public String name(){return filename;} public String version(){return contentHash.substring(0, Math.min(12, contentHash.length()));} public java.time.Instant updatedAt(){return createdAt;} }
  record CreatePayload(@NotBlank String applicationId,@NotBlank String contactId,@NotBlank String resumeVersionId,@NotBlank @Email String recipient,String contactName,@NotBlank String company,@NotBlank String role,String instructions){}
  record EditPayload(@NotBlank String subject,@NotBlank String body){}
  record DraftResponse(UUID draftId,String applicationId,String contactId,String resumeVersionId,String recipient,String subject,String body,String gmailDraftId,boolean sent){ public UUID id(){return draftId;} static DraftResponse from(DraftEntity d){return new DraftResponse(d.getId(),d.getApplicationId(),d.getContactId(),d.getResumeVersionId(),d.getRecipient(),d.getSubject(),d.getBody(),d.getGmailDraftId(),false);}}
}
