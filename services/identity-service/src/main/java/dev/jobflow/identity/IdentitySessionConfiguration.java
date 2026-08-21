package dev.jobflow.identity;

import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class IdentitySessionConfiguration {
    @Bean
    Clock sessionClock() { return Clock.systemUTC(); }

    @Bean
    TokenCipher tokenCipher(@Value("${JOBFLOW_SESSION_ENCRYPTION_KEY}") String key) {
        return new AesGcmTokenCipher(key);
    }

    @Bean
    TokenRefresher tokenRefresher(RestClient.Builder builder,
            @Value("${JOBFLOW_OIDC_TOKEN_ENDPOINT}") String tokenEndpoint,
            @Value("${JOBFLOW_OIDC_CLIENT_ID}") String clientId,
            @Value("${JOBFLOW_OIDC_CLIENT_SECRET}") String clientSecret) {
        return new OAuthTokenRefresher(builder.build(), tokenEndpoint, clientId, clientSecret);
    }

    @Bean
    SessionService sessionService(SessionStore store, TokenCipher cipher, TokenRefresher refresher, Clock clock) {
        return new SessionService(store, cipher, refresher, clock);
    }
}
