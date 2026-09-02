package dev.jobflow.contacts;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "contact_candidates")
public class ContactCandidateEntity {
  @Id private UUID id;
  @Column(name = "application_id", nullable = false) private UUID applicationId;
  @Column(name = "tenant_id", nullable = false, length = 160) private String tenantId;
  @Column(name = "user_id", nullable = false, length = 160) private String userId;
  @Column(length = 240) private String name;
  @Column(name = "contact_role", length = 240) private String role;
  @Column(length = 240) private String company;
  @Column(length = 320) private String email;
  @Column(name = "profile_url", length = 1000) private String profileUrl;
  @Column(name = "source_url", length = 1000) private String sourceUrl;
  @Enumerated(EnumType.STRING) @Column(name = "source_type", nullable = false, length = 32) private ContactSourceType sourceType;
  @Column(nullable = false, length = 2000) private String evidence;
  @Column(name = "source_message_id", length = 240) private String sourceMessageId;
  @Column(name = "source_thread_id", length = 240) private String sourceThreadId;
  @Column(nullable = false) private double confidence;
  @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private ContactStatus status;
  @Column(name = "verification_status", nullable = false, length = 32) private String verificationStatus;
  @Column(nullable = false, length = 120) private String provider;
  @Column(name = "provider_version", nullable = false, length = 80) private String providerVersion;
  @Enumerated(EnumType.STRING) @Column(name = "allowed_use", nullable = false, length = 32) private AllowedUse allowedUse;
  @Column(nullable = false) private boolean selected;
  @Column(nullable = false) private boolean preselected;
  @Column(name = "content_hash", nullable = false, length = 128) private String contentHash;
  @Column(name = "created_at", nullable = false) private Instant createdAt;
  @Column(name = "expires_at") private Instant expiresAt;

  protected ContactCandidateEntity() {}

  ContactCandidateEntity(UUID applicationId, String tenantId, String userId, DiscoveredContact contact) {
    this.id = UUID.randomUUID(); this.applicationId = applicationId; this.tenantId = tenantId; this.userId = userId;
    this.name = contact.name(); this.role = contact.role(); this.company = contact.company(); this.email = contact.email();
    this.profileUrl = contact.profileUrl(); this.sourceUrl = contact.sourceUrl(); this.sourceType = contact.sourceType();
    this.evidence = contact.evidence(); this.sourceMessageId = contact.messageId(); this.sourceThreadId = contact.threadId();
    this.confidence = contact.confidence(); this.status = contact.status(); this.verificationStatus = contact.verificationStatus();
    this.provider = contact.provider(); this.providerVersion = contact.providerVersion(); this.allowedUse = contact.allowedUse();
    this.selected = false; this.preselected = false; this.contentHash = contact.contentHash(); this.createdAt = Instant.now();
  }

  public UUID getId() { return id; } public UUID getApplicationId() { return applicationId; }
  public String getTenantId() { return tenantId; } public String getUserId() { return userId; }
  public String getName() { return name; } public String getRole() { return role; } public String getCompany() { return company; }
  public String getEmail() { return email; } public String getProfileUrl() { return profileUrl; } public String getSourceUrl() { return sourceUrl; }
  public ContactSourceType getSourceType() { return sourceType; } public String getEvidence() { return evidence; }
  public String getSourceMessageId() { return sourceMessageId; } public String getSourceThreadId() { return sourceThreadId; }
  public double getConfidence() { return confidence; } public ContactStatus getStatus() { return status; }
  public String getVerificationStatus() { return verificationStatus; } public String getProvider() { return provider; }
  public String getProviderVersion() { return providerVersion; } public AllowedUse getAllowedUse() { return allowedUse; }
  public boolean isSelected() { return selected; } public String getContentHash() { return contentHash; }
  public boolean isPreselected() { return preselected; }
  public Instant getCreatedAt() { return createdAt; } public Instant getExpiresAt() { return expiresAt; }
  void select() { this.selected = true; }
  void preselect() { this.preselected = true; }
  void unpreselect() { this.preselected = false; }
  void unselect() { this.selected = false; }
}
