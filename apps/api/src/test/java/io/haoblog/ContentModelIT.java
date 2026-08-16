package io.haoblog;

import io.haoblog.content.domain.Article;
import io.haoblog.content.domain.ArticleStatus;
import io.haoblog.content.persistence.ArticleRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.RollbackException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testcontainers
@SpringBootTest
class ContentModelIT {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:0.8.6-pg17");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.placeholders.admin_username", () -> "admin");
        registry.add("spring.flyway.placeholders.admin_password_hash", () -> "$2a$10$0V.Xs7CLOUYSekm7RKq3Z.iY76KUan/Xbeu5vjmLpX.sVd4pcFpIu");
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired ArticleRepository articles;
    @Autowired EntityManagerFactory entityManagerFactory;

    @Test
    void migratesAllVersionsAndCreatesContentTables() {
        assertEquals(8, jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history", Integer.class));
        for (String table : List.of("article", "category", "tag", "article_tag", "article_revision",
                "article_preview_token", "media_asset", "outbox_event")) {
            assertEquals(1, jdbc.queryForObject(
                    "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name=?",
                    Integer.class, table));
        }
        assertEquals("YES", jdbc.queryForObject(
                "SELECT is_nullable FROM information_schema.columns WHERE table_name='article' AND column_name='slug'", String.class));
        assertEquals("bigint", jdbc.queryForObject(
                "SELECT data_type FROM information_schema.columns WHERE table_name='article' AND column_name='version'", String.class));
    }

    @Test
    void databaseRejectsDuplicateSlugsAndTagAssociations() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        Instant now = Instant.now();
        Timestamp timestamp = Timestamp.from(now);
        jdbc.update("INSERT INTO article(id, slug, title, markdown_source, status, created_at, updated_at) VALUES (?, ?, ?, ?, 'DRAFT', ?, ?)",
                first, "duplicate-slug", "One", "# one", timestamp, timestamp);
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "INSERT INTO article(id, slug, title, markdown_source, status, created_at, updated_at) VALUES (?, ?, ?, ?, 'DRAFT', ?, ?)",
                second, "duplicate-slug", "Two", "# two", timestamp, timestamp));

        UUID tagId = UUID.randomUUID();
        jdbc.update("INSERT INTO tag(id, name, slug, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
                tagId, "Java", "java", timestamp, timestamp);
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "INSERT INTO tag(id, name, slug, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(), "Java", "java-2", timestamp, timestamp));
        jdbc.update("INSERT INTO article_tag(article_id, tag_id) VALUES (?, ?)", first, tagId);
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "INSERT INTO article_tag(article_id, tag_id) VALUES (?, ?)", first, tagId));

        UUID categoryId = UUID.randomUUID();
        jdbc.update("INSERT INTO category(id, name, slug, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
                categoryId, "Java", "java-category", timestamp, timestamp);
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "INSERT INTO category(id, name, slug, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(), "Java 2", "not valid", timestamp, timestamp));
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "INSERT INTO category(id, name, slug, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(), "Java", "java-category-2", timestamp, timestamp));

        assertThrows(DataAccessException.class, () -> jdbc.update(
                "INSERT INTO article(id, slug, title, markdown_source, status, created_at, updated_at) VALUES (?, ?, ?, ?, 'DRAFT', ?, ?)",
                UUID.randomUUID(), "not valid", "Invalid", "# invalid", timestamp, timestamp));
    }

    @Test
    void previewDigestAndMediaHashAreFixedLengthAndUnique() throws Exception {
        UUID articleId = UUID.randomUUID();
        Instant now = Instant.now();
        Timestamp timestamp = Timestamp.from(now);
        jdbc.update("INSERT INTO article(id, slug, title, markdown_source, status, created_at, updated_at) VALUES (?, ?, ?, ?, 'DRAFT', ?, ?)",
                articleId, "digest-test", "Digest", "# digest", timestamp, timestamp);
        byte[] digest = MessageDigest.getInstance("SHA-256").digest("secret-preview".getBytes(StandardCharsets.UTF_8));
        jdbc.update("INSERT INTO article_preview_token(id, article_id, token_digest, source_version, expires_at, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), articleId, digest, 0L, Timestamp.from(now.plus(1, ChronoUnit.DAYS)), timestamp);
        assertEquals(32, jdbc.queryForObject("SELECT octet_length(token_digest) FROM article_preview_token", Integer.class));
        assertEquals(0L, jdbc.queryForObject("SELECT source_version FROM article_preview_token", Long.class));
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "INSERT INTO article_preview_token(id, article_id, token_digest, source_version, expires_at, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), articleId, digest, 0L, Timestamp.from(now.plus(1, ChronoUnit.DAYS)), timestamp));

        byte[] mediaHash = MessageDigest.getInstance("SHA-256").digest("image".getBytes(StandardCharsets.UTF_8));
        jdbc.update("INSERT INTO media_asset(id, object_key, mime_type, size_bytes, sha256, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, 'AVAILABLE', ?, ?)",
                UUID.randomUUID(), "images/digest-test", "image/png", 5L, mediaHash, timestamp, timestamp);
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "INSERT INTO media_asset(id, object_key, mime_type, size_bytes, sha256, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, 'AVAILABLE', ?, ?)",
                UUID.randomUUID(), "images/digest-test-2", "image/png", 5L, mediaHash, timestamp, timestamp));
    }

    @Test
    void articleVersionIncrementsAndConflictingContextsFail() {
        Instant now = Instant.now();
        Article saved = articles.saveAndFlush(new Article("optimistic-lock", "Lock", null, "# lock", ArticleStatus.DRAFT, null, now));
        assertEquals(0, saved.getVersion());
        EntityManager firstManager = entityManagerFactory.createEntityManager();
        EntityManager secondManager = entityManagerFactory.createEntityManager();
        var firstTransaction = firstManager.getTransaction();
        var secondTransaction = secondManager.getTransaction();
        try {
            firstTransaction.begin();
            secondTransaction.begin();
            Article first = firstManager.find(Article.class, saved.getId());
            Article second = secondManager.find(Article.class, saved.getId());
            first.updateWorkingCopy("optimistic-lock", "First", null, "# first", null, null, null, null, null, now.plusSeconds(1));
            firstTransaction.commit();
            assertEquals(1, first.getVersion());
            second.updateWorkingCopy("optimistic-lock", "Second", null, "# second", null, null, null, null, null, now.plusSeconds(2));
            assertThrows(RollbackException.class, secondTransaction::commit);
        } finally {
            if (firstTransaction.isActive()) firstTransaction.rollback();
            if (secondTransaction.isActive()) secondTransaction.rollback();
            firstManager.close();
            secondManager.close();
        }
    }

    @Test
    void articleRevisionCannotBeUpdatedOrDeleted() {
        Instant now = Instant.now();
        Timestamp timestamp = Timestamp.from(now);
        Article article = articles.saveAndFlush(new Article("immutable-revision", "Revision", null, "# revision", ArticleStatus.DRAFT, null, now));
        UUID revisionId = UUID.randomUUID();
        jdbc.update("INSERT INTO article_revision(id, article_id, source_version, title, slug, markdown_source, tag_snapshot, created_at) VALUES (?, ?, 0, ?, ?, ?, '[]'::jsonb, ?)",
                revisionId, article.getId(), "Revision", "immutable-revision", "# revision", timestamp);
        assertThrows(DataAccessException.class, () -> jdbc.update("UPDATE article_revision SET title='changed' WHERE id=?", revisionId));
        assertThrows(DataAccessException.class, () -> jdbc.update("DELETE FROM article_revision WHERE id=?", revisionId));
        assertEquals("Revision", jdbc.queryForObject("SELECT title FROM article_revision WHERE id=?", String.class, revisionId));
    }

    @Test
    void outboxPayloadIsRestrictedToPublicationEnvelope() {
        Instant now = Instant.now();
        Timestamp timestamp = Timestamp.from(now);
        UUID eventId = UUID.randomUUID();
        UUID aggregateId = UUID.randomUUID();
        UUID revisionId = UUID.randomUUID();
        Map<String, Object> valid = Map.of(
                "articleId", aggregateId.toString(),
                "revisionId", revisionId.toString(),
                "eventType", "ARTICLE_PUBLISHED",
                "occurredAt", now.toString());
        jdbc.update("INSERT INTO outbox_event(id, aggregate_id, event_type, payload, available_at, created_at) VALUES (?, ?, ?, ?::jsonb, ?, ?)",
                eventId, aggregateId, "ARTICLE_PUBLISHED", toJson(valid), timestamp, timestamp);
        assertNotNull(jdbc.queryForObject("SELECT payload FROM outbox_event WHERE id=?", String.class, eventId));
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "INSERT INTO outbox_event(id, aggregate_id, event_type, payload, available_at, created_at) VALUES (?, ?, ?, ?::jsonb, ?, ?)",
                UUID.randomUUID(), aggregateId, "ARTICLE_PUBLISHED", toJson(valid), timestamp, timestamp));
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "INSERT INTO outbox_event(id, aggregate_id, event_type, payload, available_at, created_at) VALUES (?, ?, ?, ?::jsonb, ?, ?)",
                UUID.randomUUID(), UUID.randomUUID(), "ARTICLE_PUBLISHED", "{\"articleId\":\"x\",\"revisionId\":\"y\",\"eventType\":\"x\",\"occurredAt\":\"z\",\"markdown\":\"secret\"}", timestamp, timestamp));
    }

    private static String toJson(Map<String, Object> values) {
        return "{\"articleId\":\"" + values.get("articleId") + "\",\"revisionId\":\"" + values.get("revisionId")
                + "\",\"eventType\":\"" + values.get("eventType") + "\",\"occurredAt\":\"" + values.get("occurredAt") + "\"}";
    }
}
