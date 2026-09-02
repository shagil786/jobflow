package dev.jobflow.ai;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="drafts", uniqueConstraints=@UniqueConstraint(name="ux_draft_request", columnNames={"tenant_id","user_id","application_id","contact_id","resume_version_id"}))
class DraftEntity {
  @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
  @Column(name="tenant_id",nullable=false,length=120) private String tenantId;
  @Column(name="user_id",nullable=false,length=120) private String userId;
  @Column(name="application_id",nullable=false,length=80) private String applicationId;
  @Column(name="contact_id",nullable=false,length=80) private String contactId;
  @Column(name="resume_version_id",nullable=false,length=80) private String resumeVersionId;
  @Column(nullable=false,length=320) private String recipient;
  @Column(nullable=false,length=240) private String subject;
  @Column(nullable=false, columnDefinition="text") private String body;
  @Column(nullable=false,length=32) private String status="DRAFT";
  @Column(name="gmail_draft_id",length=160) private String gmailDraftId;
  @Column(nullable=false) private Instant createdAt=Instant.now();
  @Column(nullable=false) private Instant updatedAt=Instant.now();
  protected DraftEntity() {}
  DraftEntity(String tenantId,String userId,String applicationId,String contactId,String resumeVersionId,String recipient,String subject,String body){this.tenantId=tenantId;this.userId=userId;this.applicationId=applicationId;this.contactId=contactId;this.resumeVersionId=resumeVersionId;this.recipient=recipient;this.subject=subject;this.body=body;}
  UUID getId(){return id;} String getTenantId(){return tenantId;} String getUserId(){return userId;} String getRecipient(){return recipient;} String getSubject(){return subject;} String getBody(){return body;} String getGmailDraftId(){return gmailDraftId;} String getApplicationId(){return applicationId;} String getContactId(){return contactId;} String getResumeVersionId(){return resumeVersionId;}
  void edit(String subject,String body){this.subject=subject;this.body=body;this.updatedAt=Instant.now();}
  void persisted(String gmailDraftId){this.gmailDraftId=gmailDraftId;this.updatedAt=Instant.now();}
}
