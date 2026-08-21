package dev.jobflow.ingestion;

import java.util.List;
import java.util.Objects;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ClassificationReviewService {
    private final ClassificationSuggestionRepository suggestions;
    private final ClassificationReviewRepository reviews;

    ClassificationReviewService(ClassificationSuggestionRepository suggestions, ClassificationReviewRepository reviews) {
        this.suggestions = suggestions;
        this.reviews = reviews;
    }

    @Transactional(readOnly = true)
    List<ClassificationSuggestionRecord> queue(String tenantId, String userId) {
        List<String> reviewed = reviews.findAllByTenantIdAndUserId(tenantId, userId).stream()
                .map(ClassificationReviewEntity::suggestionId)
                .toList();
        return suggestions.findAllByTenantIdAndUserIdAndRequiresReviewTrueOrderByRowIdDesc(tenantId, userId).stream()
                .filter(suggestion -> !reviewed.contains(suggestion.suggestionId()))
                .map(ClassificationSuggestionEntity::toRecord)
                .toList();
    }

    @Transactional
    ClassificationReviewResponse review(String tenantId, String userId, String suggestionId, ClassificationReviewRequest request) {
        requireValue(tenantId, "tenantId");
        requireValue(userId, "userId");
        requireValue(suggestionId, "suggestionId");
        Objects.requireNonNull(request, "request");
        ClassificationSuggestionRecord suggestion = suggestions.findByTenantIdAndUserIdAndSuggestionId(tenantId, userId, suggestionId)
                .map(ClassificationSuggestionEntity::toRecord)
                .orElseThrow(UnknownClassificationSuggestionException::new);
        if (!suggestion.suggestion().requiresReview()) throw new ClassificationReviewStateException("suggestion does not require review");
        if (reviews.findByTenantIdAndUserIdAndSuggestionId(tenantId, userId, suggestionId).isPresent())
            throw new ClassificationReviewStateException("suggestion has already been reviewed");
        try {
            return reviews.saveAndFlush(new ClassificationReviewEntity(suggestionId, tenantId, userId, request)).response(suggestion);
        } catch (DataIntegrityViolationException error) {
            throw new ClassificationReviewStateException("suggestion has already been reviewed");
        }
    }

    private static void requireValue(String value, String field) { if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank"); }
}
