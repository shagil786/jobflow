package dev.jobflow.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class FlywayMigrationTest {
    @Test
    void identitySessionSchemaIsVersionedByFlyway() throws IOException {
        ClassPathResource migration = new ClassPathResource("db/migration/V1__create_sessions.sql");

        assertThat(migration.exists()).isTrue();
        String sql = migration.getContentAsString(StandardCharsets.UTF_8);
        assertThat(sql).containsIgnoringCase("create table").containsIgnoringCase("sessions");
        assertThat(sql).containsIgnoringCase("access_token_ciphertext");
        assertThat(sql).containsIgnoringCase("refresh_token_ciphertext");
    }
}
