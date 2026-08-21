package dev.jobflow.ingestion;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GmailConnectionConfiguration {
    @Bean Clock ingestionClock() { return Clock.systemUTC(); }
    @Bean GmailBackfillWindowPlanner gmailBackfillWindowPlanner() { return new GmailBackfillWindowPlanner(); }
    @Bean GmailConnectionService gmailConnectionService(GmailConnectionStore store, GmailTokenCipher cipher, Clock clock, GmailConnectionOwnerLock ownerLock) { return new GmailConnectionService(store, cipher, clock, ownerLock); }
}
