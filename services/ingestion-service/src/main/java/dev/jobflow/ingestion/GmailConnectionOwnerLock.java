package dev.jobflow.ingestion;

import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class GmailConnectionOwnerLock {
    private static final String POSTGRES_INSERT = "insert into gmail_connection_owner_locks (tenant_id, user_id) values (?, ?) on conflict (tenant_id, user_id) do nothing";
    private static final String H2_INSERT = "merge into gmail_connection_owner_locks (tenant_id, user_id) key (tenant_id, user_id) values (?, ?)";

    private final JdbcTemplate jdbc;
    private final String ensureOwnerSql;

    GmailConnectionOwnerLock(JdbcTemplate jdbc, DataSource dataSource) {
        this.jdbc = jdbc;
        this.ensureOwnerSql = databaseProduct(dataSource).toLowerCase().contains("postgres") ? POSTGRES_INSERT : H2_INSERT;
    }

    void acquire(String tenantId, String userId) {
        jdbc.update(ensureOwnerSql, tenantId, userId);
        jdbc.queryForObject(
                "select tenant_id from gmail_connection_owner_locks where tenant_id = ? and user_id = ? for update",
                String.class,
                tenantId,
                userId);
    }

    private static String databaseProduct(DataSource dataSource) {
        try (var connection = dataSource.getConnection()) {
            return connection.getMetaData().getDatabaseProductName();
        } catch (SQLException error) {
            throw new IllegalStateException("Unable to determine database product", error);
        }
    }
}
