package dev.jobflow.ingestion;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class GmailEvidenceService {
    private final GmailConnectionStore connections;
    private final GmailTokenCipher cipher;
    private final GmailApiClient gmail;
    private final GmailMessageStore messages;
    private final EmailNormalizer normalizer;
    private final IdentityCandidateExtractor extractor;

    public GmailEvidenceService(
            GmailConnectionStore connections,
            GmailTokenCipher cipher,
            GmailApiClient gmail,
            GmailMessageStore messages,
            EmailNormalizer normalizer,
            IdentityCandidateExtractor extractor) {
        this.connections = connections;
        this.cipher = cipher;
        this.gmail = gmail;
        this.messages = messages;
        this.normalizer = normalizer;
        this.extractor = extractor;
    }

    public PreparedEvidence prepare(UUID connectionId, String messageId) {
        return prepareMessage(connectionId, messageId).evidence();
    }

    public PreparedMessage prepareMessage(UUID connectionId, String messageId) {
        StoredGmailConnection connection = connections.find(connectionId)
                .orElseThrow(UnknownGmailConnectionException::new);
        GmailMessageMetadata metadata = messages.findByProviderIdentity(connection.tenantId(), connection.userId(), connectionId, messageId)
                .orElseThrow(UnknownGmailConnectionException::new);

        String accessToken = gmail.refreshAccessToken(cipher.decrypt(connection.refreshTokenCiphertext())).value();
        SafeGmailMessage fetchedBody = gmail.fetchMessageBodyForProcessing(accessToken, messageId);
        EmailNormalizer.NormalizedEmail normalized = normalizer.normalize(
                fetchedBody == null ? null : fetchedBody.normalizedContent());

        String extractionText = normalized.extractionText();

        SafeGmailMessage message = new SafeGmailMessage(
                metadata.messageId(),
                metadata.threadId(),
                metadata.sender(),
                metadata.replyTo(),
                csvValues(metadata.recipients()),
                metadata.subject(),
                metadata.receivedAt(),
                csvValues(metadata.labelIds()),
                extractionText,
                normalized.contentHash());

        IdentityCandidateExtractor.ExtractionResult extracted = extractor.extract(message);
        PreparedEvidence evidence = new PreparedEvidence(
                connection.tenantId(),
                connection.userId(),
                connectionId,
                metadata.messageId(),
                metadata.threadId(),
                "UNKNOWN",
                normalized.contentHash(),
                true,
                extracted.company(),
                extracted.role(),
                extracted.applicationDate(),
                extracted.contact(),
                extracted.allEvidence(),
                missingFields(extracted));
        return new PreparedMessage(message, evidence);
    }

    private static List<String> csvValues(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (String part : value.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                values.add(trimmed);
            }
        }
        return List.copyOf(values);
    }

    private static List<String> missingFields(IdentityCandidateExtractor.ExtractionResult extracted) {
        List<String> missing = new ArrayList<>();
        if (extracted.company().value() == null) {
            missing.add("company");
        }
        if (extracted.role().value() == null) {
            missing.add("role");
        }
        if (extracted.applicationDate().value() == null) {
            missing.add("applicationDate");
        }
        if (extracted.contact().value() == null) {
            missing.add("contact");
        }
        return List.copyOf(missing);
    }

    public record PreparedEvidence(
            String tenantId,
            String userId,
            UUID connectionId,
            String messageId,
            String threadId,
            String intent,
            String contentHash,
            boolean requiresReview,
            IdentityCandidateExtractor.ExtractedFieldCandidate<String> company,
            IdentityCandidateExtractor.ExtractedFieldCandidate<String> role,
            IdentityCandidateExtractor.ExtractedFieldCandidate<String> applicationDate,
            IdentityCandidateExtractor.ExtractedFieldCandidate<String> contact,
            List<IdentityCandidateExtractor.EvidenceSpan> evidence,
            List<String> missingFields) {
        public PreparedEvidence {
            evidence = evidence == null ? List.of() : List.copyOf(evidence);
            missingFields = missingFields == null ? List.of() : List.copyOf(missingFields);
        }
    }

    public record PreparedMessage(SafeGmailMessage message, PreparedEvidence evidence) {}
}
