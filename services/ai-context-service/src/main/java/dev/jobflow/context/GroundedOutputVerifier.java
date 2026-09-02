package dev.jobflow.context;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class GroundedOutputVerifier {
    public ContextModels.VerificationResult verify(String output, ContextModels.ContextBundle context, List<String> citations) {
        List<String> invalid = new ArrayList<>();
        if (output == null || output.isBlank()) invalid.add("empty model output");
        if (context.state() == ContextModels.ReviewState.UNKNOWN || context.evidence().isEmpty()) invalid.add("no supporting evidence");
        List<String> validCitations = citations == null ? List.of() : citations.stream()
                .filter(citation -> context.evidence().stream().anyMatch(evidence -> evidence.citation().equals(citation))).toList();
        if (citations != null && validCitations.size() != citations.size()) invalid.add("one or more citations are not in the context bundle");
        ContextModels.ReviewState state = invalid.isEmpty() ? ContextModels.ReviewState.NEEDS_REVIEW : ContextModels.ReviewState.UNKNOWN;
        return new ContextModels.VerificationResult(state, List.of(), List.copyOf(invalid), java.util.Map.of("verifiedCitations", Integer.toString(validCitations.size())));
    }
}
