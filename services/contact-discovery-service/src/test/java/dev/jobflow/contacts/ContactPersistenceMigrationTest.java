package dev.jobflow.contacts;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:contact-migrations;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.flyway.enabled=true"
})
class ContactPersistenceMigrationTest {
  @Autowired EnrichmentRunRepository runs;

  @Test void flywayCreatesTenantScopedEnrichmentStorage() {
    UUID applicationId = UUID.randomUUID();
    EnrichmentRunEntity run = runs.save(new EnrichmentRunEntity(applicationId, "tenant-a", "user-a", "key-1", "hash-1"));
    assertThat(runs.findByTenantIdAndUserIdAndApplicationIdAndIdempotencyKey("tenant-a", "user-a", applicationId, "key-1")).contains(run);
  }
}
