package dev.jobflow.jobs;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class FlywayMigrationTest {
    @Test
    void applicationSchemaIsVersionedByFlyway() throws IOException {
        ClassPathResource migration = new ClassPathResource("db/migration/V1__create_applications.sql");

        assertThat(migration.exists()).isTrue();
        String sql = migration.getContentAsString(StandardCharsets.UTF_8);
        assertThat(sql).containsIgnoringCase("create table").containsIgnoringCase("applications").containsIgnoringCase("timeline_events");
        assertThat(sql).containsIgnoringCase("ux_capture_idempotency");
    }
}
