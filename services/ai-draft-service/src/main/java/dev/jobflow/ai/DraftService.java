package dev.jobflow.ai;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.web.client.RestClient;

@Service
class DraftService {
  private final ResumeVersionRepository resumes; private final DraftRepository drafts; private final Path storage; private final RestClient gmailClient; private final String gmailDraftUrl; private final String internalKey;
  DraftService(ResumeVersionRepository resumes,DraftRepository drafts,org.springframework.core.env.Environment env){this.resumes=resumes;this.drafts=drafts;this.storage=Path.of(env.getProperty("JOBFLOW_RESUME_STORAGE_DIR","./data/resumes"));this.gmailDraftUrl=env.getProperty("INGESTION_SERVICE_URL","http://localhost:8082")+"/internal/v1/gmail/drafts";this.internalKey=env.getProperty("JOBFLOW_INTERNAL_SERVICE_KEY","");this.gmailClient=RestClient.builder().build();}
  List<ResumeVersionEntity> resumes(String tenant,String user){return resumes.findByTenantIdAndUserIdOrderByCreatedAtDesc(tenant,user);}
  @Transactional
  ResumeVersionEntity upload(String tenant,String user,MultipartFile file) {
    if(file==null||file.isEmpty()) throw new IllegalArgumentException("resume file is required");
    try { Files.createDirectories(storage); byte[] bytes=file.getBytes(); String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); String key=UUID.randomUUID().toString(); Files.write(storage.resolve(key),bytes,StandardOpenOption.CREATE_NEW); return resumes.save(new ResumeVersionEntity(tenant,user,Objects.requireNonNullElse(file.getOriginalFilename(),"resume"),hash,key)); }
    catch(IOException|java.security.NoSuchAlgorithmException e){throw new IllegalStateException("resume storage unavailable",e);}
  }
  @Transactional
  DraftEntity create(String tenant,String user,CreateRequest r){
    if(!r.recipient().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) throw new IllegalArgumentException("a verified email recipient is required");
    UUID resumeId=parse(r.resumeVersionId()); resumes.findByIdAndTenantIdAndUserId(resumeId,tenant,user).orElseThrow(()->new IllegalArgumentException("resume version is not owned by this user"));
    return drafts.findByTenantIdAndUserIdAndApplicationIdAndContactIdAndResumeVersionId(tenant,user,r.applicationId(),r.contactId(),r.resumeVersionId()).orElseGet(()->drafts.save(new DraftEntity(tenant,user,r.applicationId(),r.contactId(),r.resumeVersionId(),r.recipient(),subject(r),body(r))));
  }
  DraftEntity get(String tenant,String user,UUID id){return drafts.findByIdAndTenantIdAndUserId(id,tenant,user).orElseThrow(()->new NoSuchElementException("draft not found"));}
  @Transactional DraftEntity edit(String tenant,String user,UUID id,String subject,String body){DraftEntity d=get(tenant,user,id); if(subject==null||subject.isBlank()||body==null||body.isBlank()) throw new IllegalArgumentException("subject and body are required"); d.edit(subject,body); return d;}
  @Transactional DraftEntity persist(String tenant,String user,UUID id){ DraftEntity d=get(tenant,user,id); if(internalKey.isBlank()) throw new IllegalStateException("Gmail draft persistence is not configured for this environment"); try { var response=gmailClient.post().uri(gmailDraftUrl).header("X-Internal-Service-Key",internalKey).body(new GmailDraftRequest(tenant,user,d.getRecipient(),d.getSubject(),d.getBody())).retrieve().body(GmailDraftResponse.class); if(response==null||response.gmailDraftId()==null||response.gmailDraftId().isBlank()) throw new IllegalStateException("Gmail draft id was not returned"); d.persisted(response.gmailDraftId()); return d; } catch(org.springframework.web.client.RestClientException e){throw new IllegalStateException("Gmail draft persistence failed",e);} }
  private static UUID parse(String value){try{return UUID.fromString(value);}catch(Exception e){throw new IllegalArgumentException("resumeVersionId is required");}}
  private static String subject(CreateRequest r){return "Following up on the "+r.role()+" opportunity at "+r.company();}
  private static String body(CreateRequest r){return "Hi "+(r.contactName()==null?"there":r.contactName())+",\n\nI’m reaching out about the "+r.role()+" opportunity at "+r.company()+". I’d appreciate the chance to learn more about the role and share how my experience could contribute.\n\nBest,\n[Your name]";}
  record GmailDraftRequest(String tenantId,String userId,String recipient,String subject,String body){}
  record GmailDraftResponse(String gmailDraftId,boolean sent){}
  record CreateRequest(String applicationId,String contactId,String resumeVersionId,String recipient,String contactName,String company,String role,String instructions){}
}
