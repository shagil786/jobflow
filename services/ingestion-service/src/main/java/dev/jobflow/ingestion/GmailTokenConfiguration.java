package dev.jobflow.ingestion;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GmailTokenConfiguration {
    @Bean
    GmailTokenCipher gmailTokenCipher(@Value("${JOBFLOW_GMAIL_TOKEN_ENCRYPTION_KEY}") String key) {
        return new GmailAesGcmTokenCipher(key);
    }
}
