package dev.jobflow.ingestion;

import org.springframework.stereotype.Component;

@Component
class DeterministicClassifierProvider implements ClassifierProvider {
    static final String NAME = "deterministic";
    private final DeterministicMessageIntentClassifier classifier;

    DeterministicClassifierProvider(DeterministicMessageIntentClassifier classifier) {
        this.classifier = classifier;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public ClassificationSuggestionV1 classify(ClassificationInput input) {
        ClassificationSuggestionRecord result = classifier.classify(input.message(), input.evidence());
        return result.suggestion();
    }
}
