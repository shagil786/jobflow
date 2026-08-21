package dev.jobflow.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class FlywayMigrationTest {
    @Test
    void gmailMessageAndThreadSchemaIsVersionedByFlyway() throws IOException {
        ClassPathResource messageMigration = new ClassPathResource("db/migration/V4__expand_gmail_message_metadata.sql");
        ClassPathResource threadMigration = new ClassPathResource("db/migration/V5__create_gmail_threads.sql");

        assertThat(messageMigration.exists()).isTrue();
        assertThat(threadMigration.exists()).isTrue();

        String messageSql = messageMigration.getContentAsString(StandardCharsets.UTF_8);
        assertThat(messageSql).containsIgnoringCase("gmail_messages");
        assertThat(messageSql).containsIgnoringCase("tenant_id");
        assertThat(messageSql).containsIgnoringCase("user_id");
        assertThat(messageSql).containsIgnoringCase("normalized_content_hash");

        String threadSql = threadMigration.getContentAsString(StandardCharsets.UTF_8);
        assertThat(threadSql).containsIgnoringCase("create table");
        assertThat(threadSql).containsIgnoringCase("gmail_threads");
        assertThat(threadSql).containsIgnoringCase("tenant_id");
        assertThat(threadSql).containsIgnoringCase("user_id");
    }
}
