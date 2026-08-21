package dev.jobflow.ingestion;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestClientException;

@Component
public class RestGmailApiClient implements GmailApiClient {
    private static final String DEFAULT_TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";
    private static final String DEFAULT_GMAIL_ENDPOINT = "https://gmail.googleapis.com/gmail/v1/users/me";
    private static final List<String> METADATA_HEADERS = List.of(
            "From",
            "Reply-To",
            "To",
            "Cc",
            "Subject",
            "Date",
            "Message-ID",
            "In-Reply-To",
            "References");
    private static final int MAX_DECODED_BYTES = 262_144;

    private final RestClient client;
    private final String tokenEndpoint;
    private final String gmailEndpoint;
    private final String clientId;
    private final String clientSecret;

    public RestGmailApiClient(@org.springframework.beans.factory.annotation.Value("${GMAIL_CLIENT_ID}") String clientId,
                              @org.springframework.beans.factory.annotation.Value("${GMAIL_CLIENT_SECRET}") String clientSecret) {
        this(RestClient.builder().build(), DEFAULT_TOKEN_ENDPOINT, DEFAULT_GMAIL_ENDPOINT, clientId, clientSecret);
    }

    RestGmailApiClient(RestClient client, String tokenEndpoint, String gmailEndpoint, String clientId, String clientSecret) {
        this.client = client;
        this.tokenEndpoint = tokenEndpoint;
        this.gmailEndpoint = gmailEndpoint;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    @Override public AccessToken refreshAccessToken(String refreshToken) {
        try {
            var body = new org.springframework.util.LinkedMultiValueMap<String, String>();
            body.add("client_id", clientId); body.add("client_secret", clientSecret); body.add("refresh_token", refreshToken); body.add("grant_type", "refresh_token");
            TokenResponse response = client.post().uri(tokenEndpoint).contentType(MediaType.APPLICATION_FORM_URLENCODED).body(body).retrieve().body(TokenResponse.class);
            if (response == null || response.access_token == null || response.access_token.isBlank()) throw fetchFailure("Gmail access token was not returned");
            return new AccessToken(response.access_token, Instant.now().plusSeconds(response.expires_in == null ? 3600 : response.expires_in));
        } catch (RestClientException e) {
            throw new GmailFetchException(e);
        }
    }

    @Override public String currentHistoryId(String accessToken) {
        try {
            ProfileResponse response = client.get().uri(gmailEndpoint + "/profile").header("Authorization", "Bearer " + accessToken).retrieve().body(ProfileResponse.class);
            if (response == null || response.historyId == null || response.historyId.isBlank()) throw fetchFailure("Gmail profile history cursor was not returned");
            return response.historyId;
        } catch (RestClientException e) {
            throw new GmailFetchException(e);
        }
    }

    @Override public String trackLabelId(String accessToken) {
        try {
            LabelListResponse response = client.get().uri(gmailEndpoint + "/labels").header("Authorization", "Bearer " + accessToken).retrieve().body(LabelListResponse.class);
            if (response != null && response.labels != null) for (LabelDto label : response.labels) if (GmailSyncScope.TRACK_LABEL.equals(label.name)) return label.id;
            throw fetchFailure("Gmail label JobFlow/Track was not found");
        } catch (RestClientException e) {
            throw new GmailFetchException(e);
        }
    }

    @Override public MessagePage listMessages(String accessToken, String query, String pageToken) {
        try {
            MessageListResponse response = client.get().uri(uri -> uri.scheme("https").host("gmail.googleapis.com").path("/gmail/v1/users/me/messages").queryParam("q", query).queryParamIfPresent("pageToken", java.util.Optional.ofNullable(pageToken)).build()).header("Authorization", "Bearer " + accessToken).retrieve().body(MessageListResponse.class);
            List<MessageRef> refs = response == null || response.messages == null ? List.of() : response.messages.stream().map(m -> new MessageRef(m.id, m.threadId)).toList();
            return new MessagePage(refs, response == null ? null : response.nextPageToken, null);
        } catch (RestClientResponseException e) { throw new GmailFetchException(e); }
        catch (RestClientException e) { throw new GmailFetchException(e); }
    }

    @Override public HistoryPage listHistory(String accessToken, String startHistoryId, String labelId, String pageToken) {
        try {
            HistoryResponse response = client.get().uri(uri -> uri.scheme("https").host("gmail.googleapis.com").path("/gmail/v1/users/me/history").queryParam("startHistoryId", startHistoryId).queryParam("historyTypes", "messageAdded").queryParam("labelId", labelId).queryParamIfPresent("pageToken", java.util.Optional.ofNullable(pageToken)).build()).header("Authorization", "Bearer " + accessToken).retrieve().body(HistoryResponse.class);
            List<MessageRef> refs = new ArrayList<>();
            if (response != null && response.history != null) for (HistoryItem item : response.history) if (item.messagesAdded != null) for (AddedMessage added : item.messagesAdded) if (added.message != null) refs.add(new MessageRef(added.message.id, added.message.threadId));
            return new HistoryPage(refs, response == null ? null : response.nextPageToken, response == null ? startHistoryId : response.historyId);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) throw new GmailHistoryExpiredException();
            throw new GmailFetchException(e);
        } catch (RestClientException e) {
            throw new GmailFetchException(e);
        }
    }

    @Override
    public SafeGmailMessage fetchMessageMetadata(String accessToken, String messageId) {
        try {
            MessageDetailResponse response = client.get()
                    .uri(messageUri(messageId, "metadata", true))
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(MessageDetailResponse.class);
            return toSafeMetadata(response);
        } catch (RestClientResponseException e) {
            throw new GmailFetchException(e);
        } catch (RestClientException e) {
            throw new GmailFetchException(e);
        }
    }

    @Override
    public SafeGmailMessage fetchMessageBodyForProcessing(String accessToken, String messageId) {
        try {
            MessageDetailResponse response = client.get()
                    .uri(messageUri(messageId, "full", false))
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(MessageDetailResponse.class);
            return toSafeBody(response);
        } catch (RestClientResponseException e) {
            throw new GmailFetchException(e);
        } catch (RestClientException e) {
            throw new GmailFetchException(e);
        }
    }

    private URI messageUri(String messageId, String format, boolean metadataOnly) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(gmailEndpoint + "/messages/" + messageId)
                .queryParam("format", format);
        if (metadataOnly) {
            for (String header : METADATA_HEADERS) {
                builder.queryParam("metadataHeaders", header);
            }
        }
        return builder.build(true).toUri();
    }

    private static GmailFetchException fetchFailure(String message) {
        return new GmailFetchException(new IllegalStateException(message));
    }

    private SafeGmailMessage toSafeMetadata(MessageDetailResponse response) {
        if (response == null || response.id == null || response.id.isBlank()) {
            throw new GmailFetchException(new IllegalStateException("Gmail metadata was not returned"));
        }
        Map<String, String> headers = extractHeaders(response.payload);
        return new SafeGmailMessage(
                response.id,
                response.threadId,
                headers.get("from"),
                headers.get("reply-to"),
                parseRecipients(headers.get("to"), headers.get("cc")),
                headers.get("subject"),
                parseInstant(headers.get("date")),
                response.labelIds == null ? List.of() : response.labelIds,
                null,
                null);
    }

    private SafeGmailMessage toSafeBody(MessageDetailResponse response) {
        if (response == null || response.id == null || response.id.isBlank()) {
            throw new GmailFetchException(new IllegalStateException("Gmail message body was not returned"));
        }
        CollectedContent content = new CollectedContent();
        try {
            collectTextParts(response.payload, content);
        } catch (IllegalStateException e) {
            throw new GmailFetchException(e);
        }
        String normalized = content.normalizedText();
        return new SafeGmailMessage(
                response.id,
                response.threadId,
                null,
                null,
                List.of(),
                null,
                null,
                response.labelIds == null ? List.of() : response.labelIds,
                normalized,
                sha256(normalized));
    }

    private static Map<String, String> extractHeaders(PayloadDto payload) {
        Map<String, String> values = new HashMap<>();
        if (payload == null || payload.headers == null) {
            return values;
        }
        for (HeaderDto header : payload.headers) {
            if (header != null && header.name != null && header.value != null) {
                values.putIfAbsent(header.name.toLowerCase(Locale.ROOT), header.value.trim());
            }
        }
        return values;
    }

    private static List<String> parseRecipients(String toValue, String ccValue) {
        List<String> recipients = new ArrayList<>();
        appendRecipients(recipients, toValue);
        appendRecipients(recipients, ccValue);
        return recipients;
    }

    private static void appendRecipients(List<String> recipients, String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return;
        }
        for (String value : headerValue.split(",")) {
            String trimmed = value.trim();
            if (!trimmed.isEmpty()) {
                recipients.add(trimmed);
            }
        }
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return ZonedDateTime.parse(value, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static void collectTextParts(PayloadDto payload, CollectedContent content) {
        if (payload == null) {
            return;
        }
        if (isAttachmentPart(payload)) {
            return;
        }
        if (isTextPayload(payload)) {
            content.add(payload.mimeType, decode(payload.body.data));
        }
        if (payload.parts != null) {
            for (PayloadDto part : payload.parts) {
                collectTextParts(part, content);
            }
        }
    }

    private static boolean isTextPayload(PayloadDto payload) {
        if (payload.body == null || payload.body.data == null || payload.body.data.isBlank()) {
            return false;
        }
        if (payload.filename != null && !payload.filename.isBlank()) {
            return false;
        }
        String mimeType = payload.mimeType == null ? "" : payload.mimeType.toLowerCase(Locale.ROOT);
        return mimeType.startsWith("text/plain") || mimeType.startsWith("text/html");
    }

    private static boolean isAttachmentPart(PayloadDto payload) {
        return hasText(payload.filename) || payload.body != null && hasText(payload.body.attachmentId);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String decode(String encoded) {
        return new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
    }

    private static String sanitizeHtml(String html) {
        String withoutScripts = html.replaceAll("(?is)<script.*?>.*?</script>", " ")
                .replaceAll("(?is)<style.*?>.*?</style>", " ")
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</(p|div|li|tr|td|th|h1|h2|h3|h4|h5|h6)>", "\n");
        return normalizeWhitespace(HtmlUtils.htmlUnescape(withoutScripts.replaceAll("(?s)<[^>]+>", " ")));
    }

    private static String normalizeWhitespace(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private static String sha256(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte part : hash) {
                builder.append(String.format("%02x", part));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static final class CollectedContent {
        private final StringBuilder plainText = new StringBuilder();
        private final StringBuilder htmlText = new StringBuilder();
        private int decodedBytes;

        void add(String mimeType, String decoded) {
            decodedBytes += decoded.getBytes(StandardCharsets.UTF_8).length;
            if (decodedBytes > MAX_DECODED_BYTES) {
                throw new IllegalStateException("Gmail decoded content exceeds 256 KiB");
            }
            if (mimeType != null && mimeType.toLowerCase(Locale.ROOT).startsWith("text/plain")) {
                append(plainText, normalizeWhitespace(decoded));
            } else {
                append(htmlText, sanitizeHtml(decoded));
            }
        }

        String normalizedText() {
            String value = plainText.length() > 0 ? plainText.toString() : htmlText.toString();
            return normalizeWhitespace(value);
        }

        private static void append(StringBuilder target, String value) {
            if (value == null || value.isBlank()) {
                return;
            }
            if (target.length() > 0) {
                target.append(' ');
            }
            target.append(value);
        }
    }

    private static final class TokenResponse { public String access_token; public Long expires_in; }
    private static final class ProfileResponse { public String historyId; }
    private static final class LabelListResponse { public List<LabelDto> labels; }
    private static final class LabelDto { public String id; public String name; }
    private static final class MessageListResponse { public List<MessageDto> messages; public String nextPageToken; public Integer resultSizeEstimate; }
    private static final class MessageDto { public String id; public String threadId; }
    private static final class MessageDetailResponse { public String id; public String threadId; public List<String> labelIds; public PayloadDto payload; }
    private static final class PayloadDto { public String mimeType; public String filename; public BodyDto body; public List<HeaderDto> headers; public List<PayloadDto> parts; }
    private static final class BodyDto { public String data; public String attachmentId; }
    private static final class HeaderDto { public String name; public String value; }
    private static final class HistoryResponse { public List<HistoryItem> history; public String nextPageToken; public String historyId; }
    private static final class HistoryItem { public List<AddedMessage> messagesAdded; }
    private static final class AddedMessage { public MessageDto message; }
}
