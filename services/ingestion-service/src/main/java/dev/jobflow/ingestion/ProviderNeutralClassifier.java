package dev.jobflow.ingestion;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/** Selects a configured provider without exposing provider-specific types to the pipeline. */
@Component
@Primary
public class ProviderNeutralClassifier implements MessageIntentClassifier {
    private final Map<String, ClassifierProvider> providers;
    private final String configuredProvider;

    public ProviderNeutralClassifier(
            List<ClassifierProvider> providers,
            @Value("${JOBFLOW_CLASSIFIER_PROVIDER:deterministic}") String configuredProvider) {
        this.providers = providers.stream().collect(Collectors.toUnmodifiableMap(
                provider -> provider.name().toLowerCase(Locale.ROOT), Function.identity()));
        this.configuredProvider = normalize(configuredProvider);
        if (this.providers.isEmpty()) {
            throw new IllegalArgumentException("at least one classifier provider is required");
        }
    }

    @Override
    public ClassificationSuggestionRecord classify(
            SafeGmailMessage message, GmailEvidenceService.PreparedEvidence evidence) {
        ClassifierProvider.ClassificationInput input = new ClassifierProvider.ClassificationInput(message, evidence, null);
        ClassifierProvider provider = providers.get(configuredProvider);
        if (provider == null) {
            throw new ClassifierProviderException("classifier provider is not configured: " + configuredProvider);
        }
        ClassificationSuggestionV1 suggestion = provider.classify(input);
        validate(suggestion, evidence);
        return new ClassificationSuggestionRecord(evidence.connectionId(), suggestion);
    }

    ClassificationSuggestionV1 classify(ClassifierProvider.ClassificationInput input) {
        ClassifierProvider provider = providers.get(configuredProvider);
        if (provider == null) {
            throw new ClassifierProviderException("classifier provider is not configured: " + configuredProvider);
        }
        ClassificationSuggestionV1 suggestion = provider.classify(input);
        validate(suggestion, input.evidence());
        return suggestion;
    }

    String configuredProvider() {
        return configuredProvider;
    }

    private static void validate(ClassificationSuggestionV1 suggestion, GmailEvidenceService.PreparedEvidence evidence) {
        if (suggestion == null) {
            throw new ClassifierProviderException("classifier returned no suggestion");
        }
        if (!evidence.tenantId().equals(suggestion.tenantId())
                || !evidence.userId().equals(suggestion.userId())
                || !evidence.messageId().equals(suggestion.messageId())
                || !evidence.threadId().equals(suggestion.threadId())) {
            throw new ClassifierProviderException("classifier output ownership or message identity mismatch");
        }
        if (suggestion.confidence() < 0 || suggestion.confidence() > 1) {
            throw new ClassifierProviderException("classifier confidence is outside [0,1]");
        }
        if (suggestion.evidence().stream().anyMatch(span -> !evidence.tenantId().equals(span.tenantId())
                || !evidence.userId().equals(span.userId())
                || !suggestion.messageId().equals(span.messageId()))) {
            throw new ClassifierProviderException("classifier evidence is not scoped to the source message");
        }
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "deterministic";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

}
