package dev.jobflow.ingestion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class IdentityCandidateExtractor {
    private static final Pattern EMAIL_PATTERN = Pattern.compile("([A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,})", Pattern.CASE_INSENSITIVE);
    private static final Pattern APPLICATION_DATE_PATTERN =
            Pattern.compile("(?i)\\bapplication submitted on\\s+([A-Za-z]+\\s+\\d{1,2}(?:,\\s*\\d{4})?)\\b");
    private static final Pattern COMPANY_TEXT_PATTERN =
            Pattern.compile("(?i)\\b(?:application|interview|role|position|opportunity)\\s+(?:with|at|from)\\s+([A-Z][A-Za-z0-9&'./-]*(?:\\s+[A-Z][A-Za-z0-9&'./-]*){0,5})");
    private static final Pattern ROLE_PATTERN =
            Pattern.compile("(?i)\\b(?:for the|for|role|position)\\s+([A-Z][A-Za-z0-9/,&+\\-]*(?:\\s+[A-Z][A-Za-z0-9/,&+\\-]*){0,6})");
    private static final Pattern FORWARDED_BOUNDARY =
            Pattern.compile("(?im)^-+\\s*Forwarded message\\s*-+$|^From:\\s.*$");
    private static final Pattern QUOTED_BOUNDARY =
            Pattern.compile("(?im)^On .+ wrote:\\s*$|^>.*$");
    private static final Set<String> GENERIC_DOMAINS = Set.of("greenhouse.io", "lever.co", "workday.com", "ashbyhq.com");
    private static final Set<String> FREE_MAIL_DOMAINS = Set.of(
            "gmail.com", "googlemail.com", "yahoo.com", "outlook.com", "hotmail.com", "icloud.com", "proton.me", "protonmail.com");
    private static final int MAX_EVIDENCE_QUOTE_LENGTH = 160;

    public ExtractionResult extract(SafeGmailMessage message) {
        String normalizedContent = message == null || message.normalizedContent() == null ? "" : message.normalizedContent();
        String normalizedHash = message == null || message.normalizedContentHash() == null
                ? EmailNormalizer.sha256(normalizedContent)
                : message.normalizedContentHash();
        ContentSections sections = splitContent(normalizedContent);

        ExtractedFieldCandidate<String> company = extractCompany(message, sections, normalizedHash);
        ExtractedFieldCandidate<String> role = extractRole(message, sections, normalizedHash);
        ExtractedFieldCandidate<String> applicationDate = extractApplicationDate(message, sections, normalizedHash);
        ExtractedFieldCandidate<String> contact = extractContact(message, normalizedHash);
        return new ExtractionResult(company, role, applicationDate, contact);
    }

    private ExtractedFieldCandidate<String> extractCompany(
            SafeGmailMessage message,
            ContentSections sections,
            String normalizedHash) {
        String subject = safe(message.subject());
        CompanyEvidence bodyCompany = companyFromText(sections.freshContent(), "body", message, normalizedHash, false);
        if (bodyCompany != null) {
            return bodyCompany.candidate();
        }
        CompanyEvidence subjectCompany = companyFromText(subject, "subject", message, normalizedHash, false);
        if (subjectCompany != null) {
            return subjectCompany.candidate();
        }
        CompanyEvidence quotedCompany = companyFromText(sections.quotedContent(), "body", message, normalizedHash, true);
        if (quotedCompany != null) {
            return quotedCompany.candidate();
        }

        String senderAddress = extractEmailAddress(message.sender());
        if (senderAddress == null) {
            return ExtractedFieldCandidate.empty("header");
        }
        String domain = senderAddress.substring(senderAddress.indexOf('@') + 1).toLowerCase(Locale.ROOT);
        if (GENERIC_DOMAINS.contains(domain) || FREE_MAIL_DOMAINS.contains(domain)) {
            return ExtractedFieldCandidate.empty("header");
        }
        String companyName = companyFromDomain(domain);
        if (companyName == null) {
            return ExtractedFieldCandidate.empty("header");
        }
        return new ExtractedFieldCandidate<>(
                companyName,
                0.42,
                List.of(evidence(message, "sender", senderAddress, normalizedHash)),
                "header",
                true,
                false);
    }

    private ExtractedFieldCandidate<String> extractRole(
            SafeGmailMessage message,
            ContentSections sections,
            String normalizedHash) {
        String role = firstMatch(ROLE_PATTERN, safe(message.subject()));
        if (role != null) {
            return new ExtractedFieldCandidate<>(
                    role.trim(),
                    0.56,
                    List.of(evidence(message, "subject", role, normalizedHash)),
                    "header",
                    true,
                    false);
        }
        role = firstMatch(ROLE_PATTERN, sections.freshContent());
        if (role != null) {
            return new ExtractedFieldCandidate<>(
                    role.trim(),
                    0.52,
                    List.of(evidence(message, "body", role, normalizedHash)),
                    "body",
                    true,
                    false);
        }
        return ExtractedFieldCandidate.empty("body");
    }

    private ExtractedFieldCandidate<String> extractApplicationDate(
            SafeGmailMessage message,
            ContentSections sections,
            String normalizedHash) {
        Matcher freshDate = APPLICATION_DATE_PATTERN.matcher(sections.freshContent());
        if (freshDate.find()) {
            String value = freshDate.group(1).trim();
            String phrase = freshDate.group(0).trim();
            return new ExtractedFieldCandidate<>(
                    value,
                    0.74,
                    List.of(evidence(message, "body", phrase, normalizedHash)),
                    "body",
                    true,
                    false);
        }

        Matcher quotedDate = APPLICATION_DATE_PATTERN.matcher(sections.quotedContent());
        if (quotedDate.find()) {
            List<EvidenceSpan> evidence = new ArrayList<>();
            String forwardedMarker = firstMatch(FORWARDED_BOUNDARY, sections.quotedContent());
            if (forwardedMarker != null) {
                evidence.add(evidence(message, "body", forwardedMarker, normalizedHash));
            }
            evidence.add(evidence(message, "body", quotedDate.group(0).trim(), normalizedHash));
            return new ExtractedFieldCandidate<>(
                    quotedDate.group(1).trim(),
                    0.38,
                    evidence,
                    "body",
                    true,
                    false);
        }

        return ExtractedFieldCandidate.empty("body");
    }

    private ExtractedFieldCandidate<String> extractContact(SafeGmailMessage message, String normalizedHash) {
        String replyTo = extractEmailAddress(message.replyTo());
        if (replyTo != null) {
            return new ExtractedFieldCandidate<>(
                    replyTo,
                    0.8,
                    List.of(evidence(message, "header", replyTo, normalizedHash)),
                    "header",
                    true,
                    false);
        }
        String sender = extractEmailAddress(message.sender());
        if (sender != null) {
            return new ExtractedFieldCandidate<>(
                    sender,
                    0.7,
                    List.of(evidence(message, "header", sender, normalizedHash)),
                    "header",
                    true,
                    false);
        }
        return ExtractedFieldCandidate.empty("header");
    }

    private CompanyEvidence companyFromText(
            String value,
            String evidenceSource,
            SafeGmailMessage message,
            String normalizedHash,
            boolean quoted) {
        Matcher matcher = COMPANY_TEXT_PATTERN.matcher(value);
        if (!matcher.find()) {
            return null;
        }
        String company = matcher.group(1).trim();
        List<EvidenceSpan> evidence = new ArrayList<>();
        if (quoted) {
            String forwardedMarker = firstMatch(FORWARDED_BOUNDARY, value);
            if (forwardedMarker != null) {
                evidence.add(evidence(message, evidenceSource, forwardedMarker, normalizedHash));
            }
        }
        evidence.add(evidence(message, evidenceSource, matcher.group(0).trim(), normalizedHash));
        return new CompanyEvidence(new ExtractedFieldCandidate<>(
                company,
                quoted ? 0.45 : 0.68,
                evidence,
                evidenceSource,
                true,
                false));
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String firstMatch(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(0).trim() : null;
    }

    private static String extractEmailAddress(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        Matcher matcher = EMAIL_PATTERN.matcher(value);
        return matcher.find() ? matcher.group(1).toLowerCase(Locale.ROOT) : null;
    }

    private static EvidenceSpan evidence(SafeGmailMessage message, String source, String snippet, String normalizedHash) {
        String compactSnippet = compact(snippet);
        return new EvidenceSpan(
                message.messageId() + ":" + source + ":" + EmailNormalizer.sha256(compactSnippet).substring(0, 12),
                message.messageId(),
                message.threadId(),
                source,
                compactSnippet,
                normalizedHash,
                true);
    }

    private static String compact(String snippet) {
        String collapsed = snippet == null ? "" : snippet.replaceAll("\\s+", " ").trim();
        if (collapsed.length() <= MAX_EVIDENCE_QUOTE_LENGTH) {
            return collapsed;
        }
        return collapsed.substring(0, MAX_EVIDENCE_QUOTE_LENGTH - 3).trim() + "...";
    }

    private static String companyFromDomain(String domain) {
        String[] parts = domain.split("\\.");
        if (parts.length < 2) {
            return null;
        }
        int index = parts.length - 2;
        if (parts.length >= 3 && parts[parts.length - 1].length() == 2 && parts[parts.length - 2].length() <= 3) {
            index = parts.length - 3;
        }
        String token = parts[index].replace('-', ' ').replace('_', ' ');
        if (token.isBlank()) {
            return null;
        }
        String[] words = token.split("\\s+");
        List<String> titleCased = new ArrayList<>(words.length);
        for (String word : words) {
            titleCased.add(word.substring(0, 1).toUpperCase(Locale.ROOT) + word.substring(1).toLowerCase(Locale.ROOT));
        }
        return String.join(" ", titleCased);
    }

    private static ContentSections splitContent(String normalizedContent) {
        Matcher forwarded = FORWARDED_BOUNDARY.matcher(normalizedContent);
        if (forwarded.find()) {
            return new ContentSections(
                    normalizedContent.substring(0, forwarded.start()).trim(),
                    normalizedContent.substring(forwarded.start()).trim());
        }
        Matcher quoted = QUOTED_BOUNDARY.matcher(normalizedContent);
        if (quoted.find()) {
            return new ContentSections(
                    normalizedContent.substring(0, quoted.start()).trim(),
                    normalizedContent.substring(quoted.start()).trim());
        }
        return new ContentSections(normalizedContent.trim(), "");
    }

    private record CompanyEvidence(ExtractedFieldCandidate<String> candidate) {}

    private record ContentSections(String freshContent, String quotedContent) {}

    public record EvidenceSpan(
            String evidenceId,
            String messageId,
            String threadId,
            String source,
            String quotedText,
            String normalizedTextHash,
            boolean sourceAvailable) {}

    public record ExtractedFieldCandidate<T>(
            T value,
            double confidence,
            List<EvidenceSpan> evidence,
            String source,
            boolean requiresReview,
            boolean conflict) {
        public ExtractedFieldCandidate {
            confidence = Math.max(0.0, Math.min(1.0, confidence));
            evidence = evidence == null ? List.of() : List.copyOf(evidence);
            if (value != null && evidence.isEmpty()) {
                throw new IllegalArgumentException("non-empty candidates must include evidence");
            }
        }

        static <T> ExtractedFieldCandidate<T> empty(String source) {
            return new ExtractedFieldCandidate<>(null, 0.0, List.of(), source, true, false);
        }
    }

    public record ExtractionResult(
            ExtractedFieldCandidate<String> company,
            ExtractedFieldCandidate<String> role,
            ExtractedFieldCandidate<String> applicationDate,
            ExtractedFieldCandidate<String> contact) {
        public ExtractionResult {
            company = company == null ? ExtractedFieldCandidate.empty("header") : company;
            role = role == null ? ExtractedFieldCandidate.empty("body") : role;
            applicationDate = applicationDate == null ? ExtractedFieldCandidate.empty("body") : applicationDate;
            contact = contact == null ? ExtractedFieldCandidate.empty("header") : contact;
        }

        public boolean requiresReview() {
            return company.requiresReview() || role.requiresReview() || applicationDate.requiresReview() || contact.requiresReview();
        }

        public List<EvidenceSpan> allEvidence() {
            Map<String, EvidenceSpan> deduped = new LinkedHashMap<>();
            for (EvidenceSpan span : company.evidence()) {
                deduped.putIfAbsent(span.evidenceId(), span);
            }
            for (EvidenceSpan span : role.evidence()) {
                deduped.putIfAbsent(span.evidenceId(), span);
            }
            for (EvidenceSpan span : applicationDate.evidence()) {
                deduped.putIfAbsent(span.evidenceId(), span);
            }
            for (EvidenceSpan span : contact.evidence()) {
                deduped.putIfAbsent(span.evidenceId(), span);
            }
            return List.copyOf(deduped.values());
        }
    }
}
