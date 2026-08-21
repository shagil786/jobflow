package dev.jobflow.ingestion;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class RestGmailApiClient implements GmailApiClient {
    private final RestClient client = RestClient.builder().build();
    private static final String TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";
    private static final String GMAIL_ENDPOINT = "https://gmail.googleapis.com/gmail/v1/users/me";
    private final String clientId;
    private final String clientSecret;

    public RestGmailApiClient(@org.springframework.beans.factory.annotation.Value("${GMAIL_CLIENT_ID}") String clientId,
                              @org.springframework.beans.factory.annotation.Value("${GMAIL_CLIENT_SECRET}") String clientSecret) {
        this.clientId = clientId; this.clientSecret = clientSecret;
    }

    @Override public AccessToken refreshAccessToken(String refreshToken) {
        var body = new org.springframework.util.LinkedMultiValueMap<String, String>();
        body.add("client_id", clientId); body.add("client_secret", clientSecret); body.add("refresh_token", refreshToken); body.add("grant_type", "refresh_token");
        TokenResponse response = client.post().uri(TOKEN_ENDPOINT).contentType(MediaType.APPLICATION_FORM_URLENCODED).body(body).retrieve().body(TokenResponse.class);
        if (response == null || response.access_token == null || response.access_token.isBlank()) throw new IllegalStateException("Gmail access token was not returned");
        return new AccessToken(response.access_token, Instant.now().plusSeconds(response.expires_in == null ? 3600 : response.expires_in));
    }

    @Override public String currentHistoryId(String accessToken) {
        ProfileResponse response = client.get().uri(GMAIL_ENDPOINT + "/profile").header("Authorization", "Bearer " + accessToken).retrieve().body(ProfileResponse.class);
        if (response == null || response.historyId == null || response.historyId.isBlank()) throw new IllegalStateException("Gmail profile history cursor was not returned");
        return response.historyId;
    }

    @Override public String trackLabelId(String accessToken) {
        LabelListResponse response = client.get().uri(GMAIL_ENDPOINT + "/labels").header("Authorization", "Bearer " + accessToken).retrieve().body(LabelListResponse.class);
        if (response != null && response.labels != null) for (LabelDto label : response.labels) if (GmailSyncScope.TRACK_LABEL.equals(label.name)) return label.id;
        throw new IllegalStateException("Gmail label JobFlow/Track was not found");
    }

    @Override public MessagePage listMessages(String accessToken, String query, String pageToken) {
        try {
            MessageListResponse response = client.get().uri(uri -> uri.scheme("https").host("gmail.googleapis.com").path("/gmail/v1/users/me/messages").queryParam("q", query).queryParamIfPresent("pageToken", java.util.Optional.ofNullable(pageToken)).build()).header("Authorization", "Bearer " + accessToken).retrieve().body(MessageListResponse.class);
            List<MessageRef> refs = response == null || response.messages == null ? List.of() : response.messages.stream().map(m -> new MessageRef(m.id, m.threadId)).toList();
            return new MessagePage(refs, response == null ? null : response.nextPageToken, null);
        } catch (RestClientResponseException e) { throw new IllegalStateException("Gmail message listing failed", e); }
    }

    @Override public HistoryPage listHistory(String accessToken, String startHistoryId, String labelId, String pageToken) {
        try {
            HistoryResponse response = client.get().uri(uri -> uri.scheme("https").host("gmail.googleapis.com").path("/gmail/v1/users/me/history").queryParam("startHistoryId", startHistoryId).queryParam("historyTypes", "messageAdded").queryParam("labelId", labelId).queryParamIfPresent("pageToken", java.util.Optional.ofNullable(pageToken)).build()).header("Authorization", "Bearer " + accessToken).retrieve().body(HistoryResponse.class);
            List<MessageRef> refs = new ArrayList<>();
            if (response != null && response.history != null) for (HistoryItem item : response.history) if (item.messagesAdded != null) for (AddedMessage added : item.messagesAdded) if (added.message != null) refs.add(new MessageRef(added.message.id, added.message.threadId));
            return new HistoryPage(refs, response == null ? null : response.nextPageToken, response == null ? startHistoryId : response.historyId);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) throw new GmailHistoryExpiredException();
            throw new IllegalStateException("Gmail history listing failed", e);
        }
    }

    private static final class TokenResponse { public String access_token; public Long expires_in; }
    private static final class ProfileResponse { public String historyId; }
    private static final class LabelListResponse { public List<LabelDto> labels; }
    private static final class LabelDto { public String id; public String name; }
    private static final class MessageListResponse { public List<MessageDto> messages; public String nextPageToken; public Integer resultSizeEstimate; }
    private static final class MessageDto { public String id; public String threadId; }
    private static final class HistoryResponse { public List<HistoryItem> history; public String nextPageToken; public String historyId; }
    private static final class HistoryItem { public List<AddedMessage> messagesAdded; }
    private static final class AddedMessage { public MessageDto message; }
}
