package dev.jobflow.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.flywaydb.core.Flyway;

class FlywayMigrationTest {
    @Test
    void gmailMessageAndThreadSchemaIsVersionedByFlyway() throws IOException {
        ClassPathResource messageMigration = new ClassPathResource("db/migration/V4__expand_gmail_message_metadata.sql");
        ClassPathResource threadMigration = new ClassPathResource("db/migration/V5__create_gmail_threads.sql");
        ClassPathResource suggestionMigration = new ClassPathResource("db/migration/V9__create_classification_suggestions.sql");
        ClassPathResource suggestionOrderingMigration = new ClassPathResource("db/migration/V10__order_classification_suggestions_by_row_id.sql");

        assertThat(messageMigration.exists()).isTrue();
        assertThat(threadMigration.exists()).isTrue();
        assertThat(suggestionMigration.exists()).isTrue();
        assertThat(suggestionOrderingMigration.exists()).isTrue();

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

        String suggestionSql = suggestionMigration.getContentAsString(StandardCharsets.UTF_8);
        assertThat(suggestionSql).containsIgnoringCase("create table if not exists classification_suggestions");
        assertThat(suggestionSql).containsIgnoringCase("classifier_version");
        assertThat(suggestionSql).containsIgnoringCase("content_hash");
        assertThat(suggestionSql).containsIgnoringCase("evidence_json");
        assertThat(suggestionSql).containsIgnoringCase("unique");

        assertThat(suggestionOrderingMigration.getContentAsString(StandardCharsets.UTF_8))
                .containsIgnoringCase("row_id");
    }

    @Test
    void backfillSchemaAndContractsAreVersionedWithoutRawContentOrTokens() throws IOException {
        assertMigrationContains("V12__create_gmail_backfill_runs.sql", "create table if not exists gmail_backfill_runs", "run_id", "idempotency_key", "tenant_id", "user_id", "connection_id");
        assertMigrationContains("V13__create_gmail_backfill_batches.sql", "create table if not exists gmail_backfill_batches", "batch_id", "run_id", "window_from", "window_to", "sequence_no");
        assertMigrationContains("V14__create_gmail_message_candidates.sql", "create table if not exists gmail_message_candidates", "connection_id", "provider_message_id", "deterministic_signals_json");
        assertMigrationContains("V15__link_review_items_to_candidates.sql", "alter table classification_reviews", "candidate_id", "foreign key");

        assertThat(new FileSystemResource("../../contracts/events/gmail-backfill-requested.v1.json").exists()).isTrue();
        assertThat(new FileSystemResource("../../contracts/events/gmail-backfill-batch.v1.json").exists()).isTrue();
        String runs = new ClassPathResource("db/migration/V12__create_gmail_backfill_runs.sql").getContentAsString(StandardCharsets.UTF_8);
        assertThat(runs).doesNotContainIgnoringCase("refresh_token").doesNotContainIgnoringCase("raw_body");
        String candidates = new ClassPathResource("db/migration/V14__create_gmail_message_candidates.sql").getContentAsString(StandardCharsets.UTF_8);
        assertThat(candidates).doesNotContainIgnoringCase("raw_body").doesNotContainIgnoringCase("refresh_token");
    }

    @Test
    void backfillSchemaEnforcesOwnershipIdentityOrderingAndActiveRunUniqueness() throws Exception {
        String url = "jdbc:h2:mem:flyway-backfill-schema;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        try (Connection connection = DriverManager.getConnection(url, "sa", ""); Statement statement = connection.createStatement()) {
            Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration").load().migrate();
            assertThat(count(statement, "select count(*) from information_schema.tables where table_name in ('GMAIL_BACKFILL_RUNS','GMAIL_BACKFILL_BATCHES','GMAIL_MESSAGE_CANDIDATES')")).isEqualTo(3);
            assertThat(count(statement, "select count(*) from information_schema.columns where table_name='CLASSIFICATION_REVIEWS' and column_name='CANDIDATE_ID'")).isEqualTo(1);

            String connectionId = "11111111-1111-1111-1111-111111111111";
            String runId = "22222222-2222-2222-2222-222222222222";
            statement.executeUpdate("insert into gmail_connections (connection_id,user_id,tenant_id,email,refresh_token_ciphertext,last_history_id,connected_at,active) values ('" + connectionId + "','user-1','tenant-1','person@example.com','encrypted','history',current_timestamp,true)");
            statement.executeUpdate("insert into gmail_backfill_runs (run_id,tenant_id,user_id,connection_id,idempotency_key,mode,status,requested_from,requested_to,batch_size_days,correlation_id,created_at,updated_at,active_owner_key) values ('" + runId + "','tenant-1','user-1','" + connectionId + "','idem-1','AUTOMATIC','QUEUED',dateadd('DAY',-1,current_timestamp),current_timestamp,7,'corr-1',current_timestamp,current_timestamp,'ACTIVE')");
            statement.executeUpdate("insert into gmail_message_candidates (candidate_id,connection_id,tenant_id,user_id,provider_message_id,state,deterministic_score,deterministic_signals_json,discovered_at) values ('33333333-3333-3333-3333-333333333333','" + connectionId + "','tenant-1','user-1','provider-1','PENDING',0.7,'[]',current_timestamp)");
            statement.executeUpdate("insert into gmail_backfill_batches (batch_id,run_id,tenant_id,user_id,connection_id,sequence_no,window_from,window_to,priority,status,attempt_count,created_at,updated_at) values ('44444444-4444-4444-4444-444444444444','" + runId + "','tenant-1','user-1','" + connectionId + "',1,dateadd('HOUR',-1,current_timestamp),current_timestamp,'HIGH','QUEUED',0,current_timestamp,current_timestamp)");
            statement.executeUpdate("update classification_reviews set candidate_id='33333333-3333-3333-3333-333333333333' where 1=0");

            assertThatThrownBy(() -> statement.executeUpdate("insert into gmail_backfill_runs (run_id,tenant_id,user_id,connection_id,idempotency_key,mode,status,requested_from,requested_to,batch_size_days,correlation_id,created_at,updated_at,active_owner_key) values ('55555555-5555-5555-5555-555555555555','tenant-1','user-1','" + connectionId + "','idem-2','AUTOMATIC','RUNNING',dateadd('DAY',-1,current_timestamp),current_timestamp,7,'corr-2',current_timestamp,current_timestamp,'ACTIVE')"))
                    .isInstanceOf(Exception.class);
            assertThatThrownBy(() -> statement.executeUpdate("insert into gmail_message_candidates (candidate_id,connection_id,tenant_id,user_id,provider_message_id,state,deterministic_score,deterministic_signals_json,discovered_at) values ('66666666-6666-6666-6666-666666666666','" + connectionId + "','tenant-1','user-1','provider-1','PENDING',0.2,'[]',current_timestamp)"))
                    .isInstanceOf(Exception.class);
        }
    }

    private static void assertMigrationContains(String file, String... fragments) throws IOException {
        String sql = new ClassPathResource("db/migration/" + file).getContentAsString(StandardCharsets.UTF_8);
        for (String fragment : fragments) {
            assertThat(sql).containsIgnoringCase(fragment);
        }
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

    @Test
    void upgradesV6MailboxRowsThroughV7WithDeterministicActiveOwnership() throws Exception {
        String url = "jdbc:h2:mem:flyway-v6-to-v7;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        try (Connection connection = DriverManager.getConnection(url, "sa", ""); Statement statement = connection.createStatement()) {
            Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration").target("5").load().migrate();
            statement.executeUpdate("insert into gmail_connections (connection_id,user_id,tenant_id,email,refresh_token_ciphertext,last_history_id,connected_at) values ('11111111-1111-1111-1111-111111111111','legacy-user','legacy-tenant','LEGACY@EXAMPLE.COM','encrypted:legacy-1','history-1',TIMESTAMP WITH TIME ZONE '2026-08-21 12:00:00+00:00')");

            Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration").target("6").load().migrate();
            statement.executeUpdate("insert into gmail_connections (connection_id,user_id,tenant_id,email,refresh_token_ciphertext,last_history_id,connected_at) values ('22222222-2222-2222-2222-222222222222','legacy-user','legacy-tenant','second@example.com','encrypted:legacy-2','history-2',TIMESTAMP WITH TIME ZONE '2026-08-21 12:00:00+00:00')");

            Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration").target("7").load().migrate();

            assertThat(count(statement, "select count(*) from gmail_connections where tenant_id='legacy-tenant' and user_id='legacy-user'")).isEqualTo(2);
            assertThat(count(statement, "select count(*) from gmail_connections where tenant_id='legacy-tenant' and user_id='legacy-user' and active=true")).isEqualTo(1);
            assertThat(count(statement, "select count(*) from information_schema.indexes where table_name='GMAIL_CONNECTIONS' and index_name='UX_GMAIL_CONNECTION_OWNER'")).isZero();
            assertThat(count(statement, "select count(*) from information_schema.indexes where table_name='GMAIL_CONNECTIONS' and index_name='UX_GMAIL_CONNECTION_MAILBOX'")).isEqualTo(1);
            assertThat(count(statement, "select count(*) from gmail_connections where email='legacy@example.com'")).isEqualTo(1);

            statement.executeUpdate("insert into gmail_connections (connection_id,user_id,tenant_id,email,refresh_token_ciphertext,last_history_id,connected_at,active) values ('33333333-3333-3333-3333-333333333333','other-user','other-tenant','legacy@example.com','encrypted:other','history-3',CURRENT_TIMESTAMP,false)");
            assertThatThrownBy(() -> statement.executeUpdate("insert into gmail_connections (connection_id,user_id,tenant_id,email,refresh_token_ciphertext,last_history_id,connected_at,active) values ('44444444-4444-4444-4444-444444444444','legacy-user','legacy-tenant','legacy@example.com','encrypted:duplicate','history-4',CURRENT_TIMESTAMP,false)"))
                    .isInstanceOf(Exception.class);
        }
    }

    @Test
    void upgradesV8AdditivelyAndPreservesExistingGmailRowsWhileAddingSuggestionSchema() throws Exception {
        String url = "jdbc:h2:mem:flyway-v8-to-v10;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        try (Connection connection = DriverManager.getConnection(url, "sa", ""); Statement statement = connection.createStatement()) {
            Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration").target("8").load().migrate();
            String connectionId = "11111111-1111-1111-1111-111111111111";
            statement.executeUpdate("insert into gmail_connections (connection_id,user_id,tenant_id,email,refresh_token_ciphertext,last_history_id,connected_at,active) values ('"
                    + connectionId + "','legacy-user','legacy-tenant','legacy@example.com','encrypted:legacy','history-1',CURRENT_TIMESTAMP,true)");
            statement.executeUpdate("insert into gmail_messages (message_id,connection_id,thread_id,tenant_id,user_id,sender,reply_to,recipients,subject,received_at,label_ids,normalized_content_hash,captured_at) values ('legacy-message','"
                    + connectionId
                    + "','legacy-thread','legacy-tenant','legacy-user','sender@example.com','reply@example.com','one@example.com','Legacy subject',CURRENT_TIMESTAMP,'Label_JobFlowTrack','hash-legacy',CURRENT_TIMESTAMP)");
            statement.executeUpdate("insert into gmail_threads (thread_id,connection_id,tenant_id,user_id,first_seen_at,last_seen_at) values ('legacy-thread','"
                    + connectionId + "','legacy-tenant','legacy-user',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");

            Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration").load().migrate();

            assertThat(count(statement, "select count(*) from gmail_connections")).isEqualTo(1);
            assertThat(count(statement, "select count(*) from gmail_messages")).isEqualTo(1);
            assertThat(count(statement, "select count(*) from gmail_threads")).isEqualTo(1);
            assertThat(count(statement, "select count(*) from classification_suggestions")).isZero();
            assertThat(count(statement, "select count(*) from information_schema.indexes where table_name='CLASSIFICATION_SUGGESTIONS' and index_name='IX_CLASSIFICATION_SUGGESTIONS_LATEST'"))
                    .isEqualTo(1);

            statement.executeUpdate(
                    "insert into classification_suggestions (suggestion_id,connection_id,tenant_id,user_id,message_id,thread_id,intent,direction,confidence,requires_review,classifier_version,content_hash,company_json,evidence_json,missing_fields_json,contradictions_json,created_at) values ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa','"
                            + connectionId
                            + "','legacy-tenant','legacy-user','legacy-message','legacy-thread','UNKNOWN','UNKNOWN',0.18,true,'rules-2026-08-21-v1','hash-legacy','{\"value\":\"Example Corp\",\"confidence\":0.91,\"evidence\":[],\"source\":\"body\",\"requiresReview\":true,\"conflict\":false}','[]','[\"role\"]','[]',CURRENT_TIMESTAMP)");
            assertThatThrownBy(() -> statement.executeUpdate(
                            "insert into classification_suggestions (suggestion_id,connection_id,tenant_id,user_id,message_id,thread_id,intent,direction,confidence,requires_review,classifier_version,content_hash,evidence_json,missing_fields_json,contradictions_json,created_at) values ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb','"
                                    + connectionId
                                    + "','legacy-tenant','legacy-user','legacy-message','legacy-thread','UNKNOWN','UNKNOWN',0.18,true,'rules-2026-08-21-v1','hash-legacy','[]','[]','[]',CURRENT_TIMESTAMP)"))
                    .isInstanceOf(Exception.class);
        }
    }

    private static int count(Statement statement, String sql) throws Exception {
        try (ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getInt(1);
        }
    }
}
