package dev.jobflow.jobs;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "timeline_events")
public class TimelineEventEntity {
  @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
  @Column(name = "tenant_id", nullable = false, length = 120) private String tenantId;
  @Column(name = "user_id", nullable = false, length = 120) private String userId;
  @Column(name = "application_id", nullable = false) private UUID applicationId;
  @Column(nullable = false, length = 80) private String type;
  @Column(nullable = false, length = 2_000) private String summary;
  @Column(name = "occurred_at", nullable = false) private Instant occurredAt;

  protected TimelineEventEntity() {}
  public TimelineEventEntity(String tenantId, String userId, UUID applicationId, String type, String summary) {
    this.tenantId = tenantId; this.userId = userId; this.applicationId = applicationId; this.type = type; this.summary = summary; this.occurredAt = Instant.now();
  }
  public UUID getId() { return id; }
  public UUID getApplicationId() { return applicationId; }
  public String getType() { return type; }
  public String getSummary() { return summary; }
  public Instant getOccurredAt() { return occurredAt; }
}
