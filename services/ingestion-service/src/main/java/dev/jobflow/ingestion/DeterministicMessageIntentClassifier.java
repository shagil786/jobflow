package dev.jobflow.ingestion;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DeterministicMessageIntentClassifier implements MessageIntentClassifier {
    static final String CLASSIFIER_VERSION = "rules-2026-08-21-v1";

    private static final double MIN_CLASSIFIED_CONFIDENCE = 0.60;
    private static final double CONFLICT_CONFIDENCE = 0.2;
    private static final double UNKNOWN_CONFIDENCE = 0.18;
    private static final double STRONG_CONFLICT_THRESHOLD = 0.85;

    private static final List<Rule> RULES = List.of(
            rule(MessageIntent.APPLICATION_CONFIRMATION, 0.94, "\\bapplication (?:has been|was)?\\s*submitted\\b", DirectionPolicy.INBOUND_ONLY),
            rule(MessageIntent.APPLICATION_CONFIRMATION, 0.82, "\\bthank you for applying\\b", DirectionPolicy.INBOUND_ONLY),
            rule(MessageIntent.APPLICATION_CONFIRMATION, 0.78, "\\bapplication received\\b", DirectionPolicy.INBOUND_ONLY),
            rule(MessageIntent.RECRUITER_OUTREACH, 0.9, "\\bcame across your profile\\b", DirectionPolicy.INBOUND_ONLY),
            rule(MessageIntent.RECRUITER_OUTREACH, 0.78, "\\binterested in your background\\b", DirectionPolicy.INBOUND_ONLY),
            rule(MessageIntent.RECRUITER_OUTREACH, 0.76, "\\blove to chat\\b", DirectionPolicy.INBOUND_ONLY),
            rule(MessageIntent.RECRUITER_REPLY, 0.88, "\\bthanks for reaching out\\b", DirectionPolicy.INBOUND_ONLY),
            rule(MessageIntent.RECRUITER_REPLY, 0.74, "\\bhappy to talk\\b", DirectionPolicy.INBOUND_ONLY),
            rule(MessageIntent.INTERVIEW_INVITATION, 0.93, "\\bschedule an interview\\b", DirectionPolicy.INBOUND_ONLY),
            rule(MessageIntent.INTERVIEW_INVITATION, 0.87, "\\binterview invitation\\b", DirectionPolicy.INBOUND_ONLY),
            rule(MessageIntent.INTERVIEW_INVITATION, 0.84, "\\bselect a time\\b", DirectionPolicy.INBOUND_ONLY),
            rule(MessageIntent.INTERVIEW_RESCHEDULE, 0.96, "\\breschedule(?: your)? interview\\b", DirectionPolicy.INBOUND_ONLY),
            rule(MessageIntent.INTERVIEW_RESCHEDULE, 0.9, "\\bneed to reschedule\\b", DirectionPolicy.INBOUND_ONLY),
            rule(MessageIntent.INTERVIEW_FEEDBACK, 0.92, "\\binterview feedback\\b", DirectionPolicy.INBOUND_ONLY),
            rule(MessageIntent.INTERVIEW_FEEDBACK, 0.88, "\\bfeedback from your interview\\b", DirectionPolicy.INBOUND_ONLY),
            rule(MessageIntent.REJECTION, 0.95, "\\bnot be moving forward\\b", DirectionPolicy.INBOUND_ONLY),
            rule(MessageIntent.REJECTION, 0.91, "\\bregret to inform you\\b", DirectionPolicy.INBOUND_ONLY),
            rule(MessageIntent.REJECTION, 0.88, "\\bmove forward with other candidates\\b", DirectionPolicy.INBOUND_ONLY),
            rule(MessageIntent.OFFER, 0.97, "\\bpleased to offer you\\b", DirectionPolicy.INBOUND_ONLY),
            rule(MessageIntent.OFFER, 0.94, "\\boffer of employment\\b", DirectionPolicy.INBOUND_ONLY),
            rule(MessageIntent.WITHDRAWAL, 0.96, "\\bwithdraw(?:ing)? my application\\b", DirectionPolicy.OUTBOUND_ONLY),
            rule(MessageIntent.WITHDRAWAL, 0.9, "\\bwithdraw from consideration\\b", DirectionPolicy.OUTBOUND_ONLY),
            rule(MessageIntent.FOLLOW_UP_REQUEST, 0.86, "\\bsend your availability\\b", DirectionPolicy.INBOUND_ONLY),
            rule(MessageIntent.FOLLOW_UP_REQUEST, 0.83, "\\blet us know your availability\\b", DirectionPolicy.INBOUND_ONLY),
            rule(MessageIntent.FOLLOW_UP_REQUEST, 0.72, "\\bfollow-up conversation\\b", DirectionPolicy.INBOUND_ONLY),
            rule(MessageIntent.EMPLOYER_UPDATE, 0.84, "\\bstill reviewing applications\\b", DirectionPolicy.INBOUND_ONLY),
            rule(MessageIntent.EMPLOYER_UPDATE, 0.74, "\\bwill be in touch soon\\b", DirectionPolicy.INBOUND_ONLY),
            rule(MessageIntent.EMPLOYER_UPDATE, 0.68, "\\bstatus update\\b", DirectionPolicy.INBOUND_ONLY),
            rule(MessageIntent.UNRELATED, 0.88, "\\bunsubscribe\\b", DirectionPolicy.ANY),
            rule(MessageIntent.UNRELATED, 0.84, "\\bweekly newsletter\\b", DirectionPolicy.ANY),
            rule(MessageIntent.UNRELATED, 0.8, "\\bproduct updates\\b", DirectionPolicy.ANY));

    @Override
    public ClassificationSuggestionRecord classify(SafeGmailMessage message, GmailEvidenceService.PreparedEvidence evidence) {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(evidence, "evidence");

        MessageDirection direction = directionFor(message);
        String contentHash = firstNonBlank(evidence.contentHash(), message.normalizedContentHash(), EmailNormalizer.sha256(""));

        List<EvidenceSpanV1> suggestionEvidence = new ArrayList<>(mapEvidence(evidence.evidence(), evidence.tenantId(), evidence.userId(), contentHash));
        List<IntentMatch> matches = collectMatches(message, evidence, direction, contentHash);
        List<String> contradictions = new ArrayList<>();

        MessageIntent intent = MessageIntent.UNKNOWN;
        double confidence = UNKNOWN_CONFIDENCE;

        IntentMatch best = matches.stream().max(Comparator.comparingDouble(IntentMatch::confidence)).orElse(null);
        IntentMatch second = matches.stream()
                .sorted(Comparator.comparingDouble(IntentMatch::confidence).reversed())
                .skip(1)
                .findFirst()
                .orElse(null);

        if (best != null && second != null
                && best.intent() != second.intent()
                && best.confidence() >= STRONG_CONFLICT_THRESHOLD
                && second.confidence() >= STRONG_CONFLICT_THRESHOLD) {
            contradictions.add("conflict between " + best.intent().name() + " and " + second.intent().name());
            suggestionEvidence.addAll(best.evidence());
            suggestionEvidence.addAll(second.evidence());
            confidence = CONFLICT_CONFIDENCE;
        } else if (best != null) {
            suggestionEvidence.addAll(best.evidence());
            confidence = best.confidence();
            if (best.confidence() >= MIN_CLASSIFIED_CONFIDENCE) {
                intent = best.intent();
            }
        }

        ClassificationSuggestionV1 suggestion = new ClassificationSuggestionV1(
                evidence.tenantId(),
                evidence.userId(),
                UUID.nameUUIDFromBytes((message.messageId() + CLASSIFIER_VERSION + contentHash).getBytes(StandardCharsets.UTF_8)).toString(),
                message.messageId(),
                message.threadId(),
                intent,
                direction,
                mapCandidate(evidence.company(), evidence.tenantId(), evidence.userId(), contentHash),
                mapCandidate(evidence.role(), evidence.tenantId(), evidence.userId(), contentHash),
                mapCandidate(evidence.applicationDate(), evidence.tenantId(), evidence.userId(), contentHash),
                mapCandidate(evidence.contact(), evidence.tenantId(), evidence.userId(), contentHash),
                confidence,
                dedupeEvidence(suggestionEvidence),
                List.copyOf(evidence.missingFields()),
                List.copyOf(contradictions),
                true,
                CLASSIFIER_VERSION,
                contentHash);

        return new ClassificationSuggestionRecord(evidence.connectionId(), suggestion);
    }

    private static List<IntentMatch> collectMatches(
            SafeGmailMessage message,
            GmailEvidenceService.PreparedEvidence evidence,
            MessageDirection direction,
            String contentHash) {
        String subject = safe(message.subject());
        String body = safe(message.normalizedContent());
        List<IntentMatch> matches = new ArrayList<>();

        for (Rule rule : RULES) {
            if (!rule.directionPolicy().supports(direction)) {
                continue;
            }

            MatchResult subjectMatch = match(rule.pattern(), subject, "subject");
            MatchResult bodyMatch = match(rule.pattern(), body, "body");
            MatchResult selected = selectBetter(subjectMatch, bodyMatch);

            if (selected == null) {
                continue;
            }

            matches.add(new IntentMatch(
                    rule.intent(),
                    rule.confidence(),
                    List.of(new EvidenceSpanV1(
                            evidence.tenantId(),
                            evidence.userId(),
                            message.messageId() + ":" + selected.source() + ":" + EmailNormalizer.sha256(selected.quotedText()).substring(0, 12),
                            message.messageId(),
                            message.threadId(),
                            selected.source(),
                            compact(selected.quotedText()),
                            contentHash,
                            true))));
        }

        return matches;
    }

    private static MatchResult selectBetter(MatchResult first, MatchResult second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        return first.quotedText().length() >= second.quotedText().length() ? first : second;
    }

    private static MatchResult match(Pattern pattern, String text, String source) {
        if (text.isBlank()) {
            return null;
        }
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        return new MatchResult(matcher.group().trim(), source);
    }

    private static MessageDirection directionFor(SafeGmailMessage message) {
        if (message.labelIds().stream().map(value -> value.toUpperCase(Locale.ROOT)).anyMatch("SENT"::equals)) {
            return MessageDirection.OUTBOUND;
        }
        if ((message.sender() == null || message.sender().isBlank()) && message.recipients().isEmpty()) {
            return MessageDirection.UNKNOWN;
        }
        return MessageDirection.INBOUND;
    }

    private static ExtractedFieldCandidateV1<String> mapCandidate(
            IdentityCandidateExtractor.ExtractedFieldCandidate<String> candidate,
            String tenantId,
            String userId,
            String contentHash) {
        if (candidate == null || candidate.value() == null) {
            return null;
        }
        return new ExtractedFieldCandidateV1<>(
                candidate.value(),
                candidate.confidence(),
                mapEvidence(candidate.evidence(), tenantId, userId, contentHash),
                candidate.source(),
                true,
                candidate.conflict());
    }

    private static List<EvidenceSpanV1> mapEvidence(
            List<IdentityCandidateExtractor.EvidenceSpan> spans,
            String tenantId,
            String userId,
            String contentHash) {
        if (spans == null || spans.isEmpty()) {
            return List.of();
        }
        List<EvidenceSpanV1> mapped = new ArrayList<>(spans.size());
        for (IdentityCandidateExtractor.EvidenceSpan span : spans) {
            mapped.add(new EvidenceSpanV1(
                    tenantId,
                    userId,
                    span.evidenceId(),
                    span.messageId(),
                    span.threadId(),
                    span.source(),
                    span.quotedText(),
                    firstNonBlank(span.normalizedTextHash(), contentHash, ""),
                    span.sourceAvailable()));
        }
        return List.copyOf(mapped);
    }

    private static List<EvidenceSpanV1> dedupeEvidence(List<EvidenceSpanV1> evidence) {
        Map<String, EvidenceSpanV1> deduped = new LinkedHashMap<>();
        for (EvidenceSpanV1 span : evidence) {
            deduped.putIfAbsent(span.evidenceId(), span);
        }
        return List.copyOf(deduped.values());
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String compact(String value) {
        String collapsed = safe(value).replaceAll("\\s+", " ").trim();
        return collapsed.length() <= 160 ? collapsed : collapsed.substring(0, 157).trim() + "...";
    }

    private static String firstNonBlank(String first, String second, String fallback) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return fallback;
    }

    private static Rule rule(MessageIntent intent, double confidence, String regex, DirectionPolicy directionPolicy) {
        return new Rule(intent, confidence, Pattern.compile(regex, Pattern.CASE_INSENSITIVE), directionPolicy);
    }

    private record Rule(MessageIntent intent, double confidence, Pattern pattern, DirectionPolicy directionPolicy) {}

    private record IntentMatch(MessageIntent intent, double confidence, List<EvidenceSpanV1> evidence) {}

    private record MatchResult(String quotedText, String source) {}

    private enum DirectionPolicy {
        ANY,
        INBOUND_ONLY,
        OUTBOUND_ONLY;

        boolean supports(MessageDirection direction) {
            return switch (this) {
                case ANY -> true;
                case INBOUND_ONLY -> direction == MessageDirection.INBOUND;
                case OUTBOUND_ONLY -> direction == MessageDirection.OUTBOUND;
            };
        }
    }
}
