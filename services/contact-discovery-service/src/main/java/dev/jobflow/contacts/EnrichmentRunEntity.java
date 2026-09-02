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
@Table(name = "contact_enrichment_runs")
public class EnrichmentRunEntity {
  @Id private UUID id;
  @Column(name = "application_id", nullable = false) private UUID applicationId;
  @Column(name = "tenant_id", nullable = false, length = 160) private String tenantId;
  @Column(name = "user_id", nullable = false, length = 160) private String userId;
  @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private EnrichmentStatus status;
  @Column(name = "idempotency_key", nullable = false, length = 160) private String idempotencyKey;
  @Column(name = "request_hash", nullable = false, length = 128) private String requestHash;
  @Column(name = "discovered_contacts", nullable = false) private int discoveredContacts;
  @Column(name = "verified_contacts", nullable = false) private int verifiedContacts;
  @Column(name = "created_at", nullable = false) private Instant createdAt;
  @Column(name = "completed_at") private Instant completedAt;

  protected EnrichmentRunEntity() {}

  EnrichmentRunEntity(UUID applicationId, String tenantId, String userId, String idempotencyKey, String requestHash) {
    this.id = UUID.randomUUID(); this.applicationId = applicationId; this.tenantId = tenantId; this.userId = userId;
    this.status = EnrichmentStatus.RUNNING; this.idempotencyKey = idempotencyKey; this.requestHash = requestHash;
    this.createdAt = Instant.now();
  }

  public UUID getId() { return id; }
  public UUID getApplicationId() { return applicationId; }
  public String getTenantId() { return tenantId; }
  public String getUserId() { return userId; }
  public EnrichmentStatus getStatus() { return status; }
  public String getIdempotencyKey() { return idempotencyKey; }
  public String getRequestHash() { return requestHash; }
  public int getDiscoveredContacts() { return discoveredContacts; }
  public int getVerifiedContacts() { return verifiedContacts; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getCompletedAt() { return completedAt; }
  void complete(EnrichmentStatus status, int discovered, int verified) { this.status = status; this.discoveredContacts = discovered; this.verifiedContacts = verified; this.completedAt = Instant.now(); }
  void advance(EnrichmentStatus status) { this.status = status; }
}
