package dev.jobflow.jobs;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "applications", uniqueConstraints = @UniqueConstraint(name = "ux_capture_idempotency", columnNames = {"tenant_id", "user_id", "idempotency_key"}))
public class ApplicationEntity {
  @Id @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;
  @Column(name = "tenant_id", nullable = false, length = 120) private String tenantId;
  @Column(name = "user_id", nullable = false, length = 120) private String userId;
  @Column(nullable = false, length = 240) private String company;
  @Column(nullable = false, length = 240) private String role;
  @Column(name = "job_url", length = 2_048) private String jobUrl;
  @Column(name = "capture_title", nullable = false, length = 240) private String captureTitle;
  @Column(name = "description_preview", length = 2_000) private String descriptionPreview;
  @Column(name = "captured_at", nullable = false) private Instant capturedAt;
  @Column(length = 80) private String source;
  @Column(length = 240) private String location;
  @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private ApplicationStatus status = ApplicationStatus.CAPTURED;
  @Column(name = "applied_date") private LocalDate appliedDate;
  @Column(name = "next_follow_up_date") private LocalDate nextFollowUpDate;
  @Column(name = "idempotency_key", nullable = false, length = 240) private String idempotencyKey;
  @Column(name = "idempotency_payload_hash", length = 64) private String idempotencyPayloadHash;
  @Column(name = "created_at", nullable = false) private Instant createdAt;
  @Column(name = "updated_at", nullable = false) private Instant updatedAt;
  @Version private long version;

  @PrePersist void onCreate() { createdAt = Instant.now(); updatedAt = createdAt; }
  @PreUpdate void onUpdate() { updatedAt = Instant.now(); }

  public UUID getId() { return id; }
  public String getTenantId() { return tenantId; }
  public void setTenantId(String tenantId) { this.tenantId = tenantId; }
  public String getUserId() { return userId; }
  public void setUserId(String userId) { this.userId = userId; }
  public String getCompany() { return company; }
  public void setCompany(String company) { this.company = company; }
  public String getRole() { return role; }
  public void setRole(String role) { this.role = role; }
  public String getJobUrl() { return jobUrl; }
  public void setJobUrl(String jobUrl) { this.jobUrl = jobUrl; }
  public String getCaptureTitle() { return captureTitle; }
  public void setCaptureTitle(String captureTitle) { this.captureTitle = captureTitle; }
  public String getDescriptionPreview() { return descriptionPreview; }
  public void setDescriptionPreview(String descriptionPreview) { this.descriptionPreview = descriptionPreview; }
  public Instant getCapturedAt() { return capturedAt; }
  public void setCapturedAt(Instant capturedAt) { this.capturedAt = capturedAt; }
  public String getSource() { return source; }
  public void setSource(String source) { this.source = source; }
  public String getLocation() { return location; }
  public void setLocation(String location) { this.location = location; }
  public ApplicationStatus getStatus() { return status; }
  public void setStatus(ApplicationStatus status) { this.status = status; }
  public LocalDate getAppliedDate() { return appliedDate; }
  public void setAppliedDate(LocalDate appliedDate) { this.appliedDate = appliedDate; }
  public LocalDate getNextFollowUpDate() { return nextFollowUpDate; }
  public void setNextFollowUpDate(LocalDate nextFollowUpDate) { this.nextFollowUpDate = nextFollowUpDate; }
  public String getIdempotencyKey() { return idempotencyKey; }
  public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
  public String getIdempotencyPayloadHash() { return idempotencyPayloadHash; }
  public void setIdempotencyPayloadHash(String idempotencyPayloadHash) { this.idempotencyPayloadHash = idempotencyPayloadHash; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
}
