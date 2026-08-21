package dev.jobflow.identity;

import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

public class OAuthTokenRefresher implements TokenRefresher {
    private final RestClient client;
    private final String tokenEndpoint;
    private final String clientId;
    private final String clientSecret;

    OAuthTokenRefresher(RestClient client, String tokenEndpoint, String clientId, String clientSecret) {
        this.client = client;
        this.tokenEndpoint = tokenEndpoint;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    @Override
    public RefreshedTokens refresh(String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", refreshToken);
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        Map<?, ?> response = client.post().uri(tokenEndpoint).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form).retrieve().body(Map.class);
        if (response == null || !(response.get("access_token") instanceof String accessToken)
                || accessToken.isBlank() || !(response.get("expires_in") instanceof Number expiresIn)
                || expiresIn.longValue() <= 0) {
            throw new IllegalStateException("provider returned an invalid refresh response");
        }
        String rotatedRefreshToken = response.get("refresh_token") instanceof String value ? value : null;
        if (rotatedRefreshToken != null && rotatedRefreshToken.isBlank()) {
            throw new IllegalStateException("provider returned a blank refresh token");
        }
        return new RefreshedTokens(accessToken, rotatedRefreshToken, expiresIn.longValue());
    }
}
