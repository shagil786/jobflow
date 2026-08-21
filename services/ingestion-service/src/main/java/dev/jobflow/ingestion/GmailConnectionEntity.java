package dev.jobflow.ingestion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "gmail_connections")
public class GmailConnectionEntity {
    @Id private UUID connectionId;
    @Column(nullable = false, length = 120) private String userId;
    @Column(nullable = false, length = 120) private String tenantId;
    @Column(nullable = false, length = 320) private String email;
    @Column(length = 8192, nullable = false) private String refreshTokenCiphertext;
    @Column(length = 255, nullable = false) private String lastHistoryId;
    @Column(length = 255) private String pageToken;
    @Column(nullable = false) private Instant connectedAt;
    @Column(nullable = false) private boolean active;
    protected GmailConnectionEntity() {}
    String getUserId() { return userId; }
    String getTenantId() { return tenantId; }
    GmailConnectionEntity(StoredGmailConnection value) { update(value); }
    void setActive(boolean active) { this.active = active; }
    void update(StoredGmailConnection value) { connectionId=value.connectionId(); userId=value.userId(); tenantId=value.tenantId(); email=value.email(); refreshTokenCiphertext=value.refreshTokenCiphertext(); lastHistoryId=value.lastHistoryId(); pageToken=value.pageToken(); connectedAt=value.connectedAt(); active=value.active(); }
    StoredGmailConnection toModel() { return new StoredGmailConnection(connectionId,userId,tenantId,email,refreshTokenCiphertext,lastHistoryId,pageToken,connectedAt,active); }
}
