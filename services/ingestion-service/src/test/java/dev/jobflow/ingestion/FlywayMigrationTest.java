package dev.jobflow.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.flywaydb.core.Flyway;

class FlywayMigrationTest {
    @Test
    void gmailMessageAndThreadSchemaIsVersionedByFlyway() throws IOException {
        ClassPathResource messageMigration = new ClassPathResource("db/migration/V4__expand_gmail_message_metadata.sql");
        ClassPathResource threadMigration = new ClassPathResource("db/migration/V5__create_gmail_threads.sql");

        assertThat(messageMigration.exists()).isTrue();
        assertThat(threadMigration.exists()).isTrue();

        String messageSql = messageMigration.getContentAsString(StandardCharsets.UTF_8);
        assertThat(messageSql).containsIgnoringCase("alter table gmail_messages add column if not exists");
        assertThat(messageSql).containsIgnoringCase("tenant_id");
        assertThat(messageSql).containsIgnoringCase("user_id");
        assertThat(messageSql).containsIgnoringCase("normalized_content_hash");

        String threadSql = threadMigration.getContentAsString(StandardCharsets.UTF_8);
        assertThat(threadSql).containsIgnoringCase("create table");
        assertThat(threadSql).containsIgnoringCase("gmail_threads");
        assertThat(threadSql).containsIgnoringCase("tenant_id");
        assertThat(threadSql).containsIgnoringCase("user_id");
    }

    @Test
    void upgradesLegacyMessagesAdditivelyAndPreservesRowsAndOwnership() throws Exception {
        String url = "jdbc:h2:mem:flyway-upgrade;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        try (Connection connection = DriverManager.getConnection(url, "sa", ""); Statement statement = connection.createStatement()) {
            Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration").target("3").load().migrate();
            String connectionId = "11111111-1111-1111-1111-111111111111";
            statement.executeUpdate("insert into gmail_connections (connection_id,user_id,tenant_id,email,refresh_token_ciphertext,last_history_id,connected_at) values ('"
                    + connectionId + "','legacy-user','legacy-tenant','legacy@example.com','encrypted:legacy','history-1',CURRENT_TIMESTAMP)");
            statement.executeUpdate("insert into gmail_messages (message_id,connection_id,thread_id,internal_date,label_ids,captured_at) values ('legacy-message','"
                    + connectionId + "','legacy-thread',TIMESTAMP WITH TIME ZONE '2026-08-21 12:00:00+00:00','Label_JobFlowTrack',CURRENT_TIMESTAMP)");
            Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration").load().migrate();

            try (ResultSet rows = statement.executeQuery("select message_id,tenant_id,user_id,received_at from gmail_messages where message_id='legacy-message'")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("tenant_id")).isEqualTo("legacy-tenant");
                assertThat(rows.getString("user_id")).isEqualTo("legacy-user");
                assertThat(rows.getTimestamp("received_at")).isNotNull();
                assertThat(rows.next()).isFalse();
            }
            try (ResultSet columns = statement.executeQuery("select is_nullable from information_schema.columns where table_name='GMAIL_MESSAGES' and column_name in ('TENANT_ID','USER_ID')")) {
                int ownerColumnCount = 0;
                while (columns.next()) {
                    ownerColumnCount++;
                    assertThat(columns.getString("is_nullable")).isEqualTo("NO");
                }
                assertThat(ownerColumnCount).isEqualTo(2);
            }
        }
    }
}
