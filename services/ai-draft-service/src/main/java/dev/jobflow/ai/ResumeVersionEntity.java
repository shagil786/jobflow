package dev.jobflow.ai;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "resume_versions")
class ResumeVersionEntity {
  @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
  @Column(nullable=false, length=120) private String tenantId;
  @Column(nullable=false, length=120) private String userId;
  @Column(nullable=false, length=240) private String filename;
  @Column(nullable=false, length=64) private String contentHash;
  @Column(nullable=false, length=32) private String storageKey;
  @Column(nullable=false) private Instant createdAt;
  @Column(nullable=false) private boolean active;
  protected ResumeVersionEntity() {}
  ResumeVersionEntity(String tenantId, String userId, String filename, String contentHash, String storageKey) { this.tenantId=tenantId; this.userId=userId; this.filename=filename; this.contentHash=contentHash; this.storageKey=storageKey; this.createdAt=Instant.now(); this.active=true; }
  UUID getId(){return id;} String getFilename(){return filename;} String getContentHash(){return contentHash;} Instant getCreatedAt(){return createdAt;} boolean isActive(){return active;}
}
