package dev.jobflow.ingestion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "classification_reviews")
class ClassificationReviewEntity {
    @Id private UUID reviewId;
    @Column(nullable = false, length = 36, unique = true) private String suggestionId;
    @Column(nullable = false, length = 120) private String tenantId;
    @Column(nullable = false, length = 120) private String userId;
    @Column(nullable = false, length = 16) private String decision;
    @Column(length = 240) private String company;
    @Column(length = 240) private String role;
    @Column(length = 64) private String applicationDate;
    @Column(length = 320) private String contact;
    @Column(nullable = false) private Instant reviewedAt;

    protected ClassificationReviewEntity() {}

    ClassificationReviewEntity(String suggestionId, String tenantId, String userId, ClassificationReviewRequest request) {
        this.reviewId = UUID.randomUUID();
        this.suggestionId = suggestionId;
        this.tenantId = tenantId;
        this.userId = userId;
        this.decision = request.decision().name();
        this.company = request.company();
        this.role = request.role();
        this.applicationDate = request.applicationDate();
        this.contact = request.contact();
        this.reviewedAt = Instant.now();
    }

    ClassificationReviewResponse response(ClassificationSuggestionRecord suggestion) {
        return new ClassificationReviewResponse(
                suggestion,
                reviewId,
                ClassificationReviewDecision.valueOf(decision),
                company,
                role,
                applicationDate,
                contact,
                reviewedAt);
    }

    String suggestionId() { return suggestionId; }
}
