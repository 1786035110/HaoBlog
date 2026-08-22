package io.haoblog;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class LegacyContentMigrationIT {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:0.8.6-pg17");

    @Test
    void v5BackfillsExistingPublishedArticlesToAnImmutableSnapshot() throws Exception {
        Flyway flyway = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .placeholders(Map.of(
                        "admin_username", "admin",
                        "admin_password_hash", "$2a$10$0V.Xs7CLOUYSekm7RKq3Z.iY76KUan/Xbeu5vjmLpX.sVd4pcFpIu"))
                .locations("classpath:db/migration")
                .target("4")
                .load();
        flyway.migrate();

        UUID articleId = UUID.fromString("0198a4f0-0000-7000-8000-000000000101");
        Instant publishedAt = Instant.parse("2026-01-01T00:00:00Z");
        Timestamp timestamp = Timestamp.from(publishedAt);
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var statement = connection.prepareStatement("INSERT INTO article(id, slug, title, excerpt, markdown_source, status, published_at, created_at, updated_at) VALUES (?, ?, ?, ?, ?, 'PUBLISHED', ?, ?, ?)")) {
            statement.setObject(1, articleId);
            statement.setString(2, "legacy-article");
            statement.setString(3, "Legacy article");
            statement.setString(4, "Legacy excerpt");
            statement.setString(5, "# Legacy article");
            statement.setObject(6, timestamp);
            statement.setObject(7, timestamp);
            statement.setObject(8, timestamp);
            statement.executeUpdate();
        }

        flyway = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .placeholders(Map.of(
                        "admin_username", "admin",
                        "admin_password_hash", "$2a$10$0V.Xs7CLOUYSekm7RKq3Z.iY76KUan/Xbeu5vjmLpX.sVd4pcFpIu"))
                .locations("classpath:db/migration")
                .load();
        flyway.migrate();

        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var statement = connection.prepareStatement("SELECT a.published_revision_id, r.id, r.markdown_source FROM article a JOIN article_revision r ON r.id = a.published_revision_id WHERE a.id = ?")) {
            statement.setObject(1, articleId);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals(articleId, result.getObject(1, UUID.class));
                assertEquals(articleId, result.getObject(2, UUID.class));
                assertEquals("# Legacy article", result.getString(3));
            }
        }
    }
}
