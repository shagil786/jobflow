package dev.jobflow.ingestion;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** Cheap, explainable pre-filter. It never asserts a company or application identity. */
@Component
final class GmailCandidateFilter {
    private static final List<Signal> POSITIVE = List.of(
            new Signal("application", List.of("application", "applied", "applying"), 2.0),
            new Signal("interview", List.of("interview", "screening", "next steps", "assessment"), 2.0),
            new Signal("recruiter", List.of("recruiter", "recruiting", "talent", "hiring manager"), 1.5),
            new Signal("outcome", List.of("rejected", "regret to inform", "offer", "congratulations"), 2.0),
            new Signal("role", List.of("software engineer", "frontend engineer", "developer", "position", "role"), 1.0));
    private static final List<String> PROMOTIONAL = List.of(
            "unsubscribe", "newsletter", "weekly jobs", "daily jobs", "sale", "discount", "marketing preferences");

    CandidateDecision evaluate(GmailMessageMetadata message) {
        String subject = lower(message.subject());
        String sender = lower(message.sender());
        String haystack = subject + " " + sender;
        List<String> signals = new ArrayList<>();
        double score = 0;
        for (Signal signal : POSITIVE) {
            if (signal.matches(haystack)) {
                signals.add(signal.name());
                score += signal.weight();
            }
        }
        boolean promotional = PROMOTIONAL.stream().anyMatch(haystack::contains);
        if (promotional) signals.add("promotional");
        boolean candidate = !promotional && score >= 2.0;
        return new CandidateDecision(candidate, candidate ? score : 0, List.copyOf(signals),
                candidate ? GmailMessageCandidateEntity.State.PENDING : GmailMessageCandidateEntity.State.EXPIRED,
                candidate ? java.util.Optional.empty() : java.util.Optional.empty());
    }

    record CandidateDecision(boolean candidate, double score, List<String> signals, GmailMessageCandidateEntity.State state,
            java.util.Optional<String> company) {
        String signalsJson() {
            return "[" + signals.stream().map(value -> "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"").reduce((a, b) -> a + "," + b).orElse("") + "]";
        }
    }

    private record Signal(String name, List<String> terms, double weight) {
        boolean matches(String value) { return terms.stream().anyMatch(value::contains); }
    }

    private static String lower(String value) { return value == null ? "" : value.toLowerCase(Locale.ROOT); }
}
