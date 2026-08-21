package dev.jobflow.ingestion;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GmailConnectionConfiguration {
    @Bean Clock ingestionClock() { return Clock.systemUTC(); }
    @Bean GmailConnectionService gmailConnectionService(GmailConnectionStore store, GmailTokenCipher cipher, Clock clock) { return new GmailConnectionService(store, cipher, clock); }
}
