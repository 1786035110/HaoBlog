package io.haoblog;

import io.haoblog.content.domain.Article;
import io.haoblog.content.domain.ArticleStatus;
import io.haoblog.content.persistence.ArticleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class PublicApiIT {
    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:0.8.6-pg17");
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.placeholders.admin_username", () -> "admin");
        registry.add("spring.flyway.placeholders.admin_password_hash", () -> "$2a$10$0V.Xs7CLOUYSekm7RKq3Z.iY76KUan/Xbeu5vjmLpX.sVd4pcFpIu");
    }

    @Autowired MockMvc mvc;
    @Autowired ArticleRepository articles;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void seed() {
        jdbc.execute("TRUNCATE article_tag, article_preview_token, article_revision, article, category, tag, media_asset, outbox_event CASCADE");
        Instant now = Instant.now();
        seedPublished("visible", "Visible", "Now", "# now", now.minus(1, ChronoUnit.MINUTES), now);
        seedPublished("future", "Future", "Later", "# later", now.plus(1, ChronoUnit.DAYS), now);
        articles.saveAndFlush(new Article("draft", "Draft", "No", "# draft", ArticleStatus.DRAFT, null, now));
    }

    @Test void migrationAndPublicArticleQueryExcludeFutureAndUnpublishedRows() throws Exception {
        mvc.perform(get("/api/v1/public/articles")).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].slug").value("visible"));
    }

    @Test void publicResponsesReadPublishedSnapshotMetadataAndCover() throws Exception {
        UUID mediaId = UUID.randomUUID();
        jdbc.update("INSERT INTO media_asset(id, object_key, public_url, mime_type, size_bytes, sha256, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, decode(repeat('00', 32), 'hex'), 'AVAILABLE', now(), now())",
                mediaId, "covers/snapshot.png", "https://cdn.example.test/snapshot.png", "image/png", 12L);
        UUID articleId = seedPublished("snapshot-isolation", "Working title", "Working excerpt", "# working", Instant.now().minus(1, ChronoUnit.DAYS), Instant.now());
        UUID revisionId = UUID.randomUUID();
        jdbc.update("INSERT INTO article_revision(id, article_id, source_version, title, slug, excerpt, markdown_source, seo_title, seo_description, cover_media_id, tag_snapshot, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, '[]'::jsonb, ?)",
                revisionId, articleId, 1L, "Published title", "snapshot-isolation", "Published excerpt", "# published", "Published SEO", "Published description", mediaId, java.sql.Timestamp.from(Instant.now().minus(1, ChronoUnit.DAYS)));
        jdbc.update("UPDATE article SET published_revision_id=?, title=?, excerpt=?, markdown_source=?, seo_title=?, seo_description=? WHERE id=?",
                revisionId, "Edited working title", "Edited working excerpt", "# edited", "Edited SEO", "Edited description", articleId);

        mvc.perform(get("/api/v1/public/articles/snapshot-isolation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Published title"))
                .andExpect(jsonPath("$.excerpt").value("Published excerpt"))
                .andExpect(jsonPath("$.markdown").value("# published"))
                .andExpect(jsonPath("$.seoTitle").value("Published SEO"))
                .andExpect(jsonPath("$.seoDescription").value("Published description"))
                .andExpect(jsonPath("$.coverImageUrl").value("https://cdn.example.test/snapshot.png"));
        mvc.perform(get("/api/v1/public/articles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.slug == 'snapshot-isolation')].title").value(org.hamcrest.Matchers.contains("Published title")))
                .andExpect(jsonPath("$.items[?(@.slug == 'snapshot-isolation')].markdown").value(org.hamcrest.Matchers.contains("# published")))
                .andExpect(jsonPath("$.items[?(@.slug == 'snapshot-isolation')].seoTitle").value(org.hamcrest.Matchers.contains("Published SEO")));
    }

    @Test void draftFutureScheduledWithoutSnapshotAndArchivedArticlesAre404() throws Exception {
        UUID archivedId = seedPublished("archived", "Archived", "No", "# archived", Instant.now().minus(1, ChronoUnit.DAYS), Instant.now());
        jdbc.update("UPDATE article SET status='ARCHIVED' WHERE id=?", archivedId);
        articles.saveAndFlush(new Article("scheduled-without-snapshot", "Scheduled", "No", "# scheduled", ArticleStatus.SCHEDULED, null, Instant.now()));

        mvc.perform(get("/api/v1/public/articles/draft"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/public/articles/future"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/public/articles/scheduled-without-snapshot"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/public/articles/archived"))
                .andExpect(status().isNotFound());
    }

    @Test void siteEndpointReadsMigratedDefaultSetting() throws Exception {
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM site_setting WHERE site_key = 'default'", Integer.class));
        mvc.perform(get("/api/v1/public/site")).andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("HaoBlog"));
    }

    @Test void migrationHasExpectedTypesConstraintsAndPublicIndex() {
        assertEquals("timestamp with time zone", jdbc.queryForObject(
                "SELECT data_type FROM information_schema.columns WHERE table_name='article' AND column_name='published_at'", String.class));
        assertEquals("timestamp with time zone", jdbc.queryForObject(
                "SELECT data_type FROM information_schema.columns WHERE table_name='article' AND column_name='created_at'", String.class));
        assertEquals("NO", jdbc.queryForObject(
                "SELECT is_nullable FROM information_schema.columns WHERE table_name='article' AND column_name='markdown_source'", String.class));
        assertEquals("NO", jdbc.queryForObject(
                "SELECT is_nullable FROM information_schema.columns WHERE table_name='article' AND column_name='status'", String.class));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM pg_constraint WHERE conrelid='article'::regclass AND contype='c' AND pg_get_constraintdef(oid) LIKE '%DRAFT%' AND pg_get_constraintdef(oid) LIKE '%PUBLISHED%'", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM pg_indexes WHERE tablename='article' AND indexname='article_slug_key'", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM pg_index WHERE indexrelid='article_public_published_idx'::regclass AND indpred IS NOT NULL", Integer.class));
        String predicate = jdbc.queryForObject("SELECT pg_get_expr(indpred, indrelid) FROM pg_index WHERE indexrelid='article_public_published_idx'::regclass", String.class);
        assertTrue(predicate.contains("status") && predicate.contains("PUBLISHED") && predicate.contains("published_at"));
    }

    @Test void applicationPersistsUuidV7WithoutDatabaseUuidDefault() {
        Article saved = articles.findBySlug("visible").orElseThrow();
        assertEquals(7, saved.getId().version());
        assertNull(jdbc.queryForObject("SELECT column_default FROM information_schema.columns WHERE table_name='article' AND column_name='id'", String.class));
    }

    private UUID seedPublished(String slug, String title, String excerpt, String markdown,
                               Instant publishedAt, Instant now) {
        Article article = articles.saveAndFlush(new Article(slug, title, excerpt, markdown,
                ArticleStatus.PUBLISHED, publishedAt, now));
        UUID revisionId = UUID.randomUUID();
        jdbc.update("INSERT INTO article_revision(id, article_id, source_version, title, slug, excerpt, markdown_source, tag_snapshot, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, '[]'::jsonb, ?)",
                revisionId, article.getId(), article.getVersion(), title, slug, excerpt, markdown, java.sql.Timestamp.from(publishedAt));
        jdbc.update("UPDATE article SET published_revision_id=? WHERE id=?", revisionId, article.getId());
        return article.getId();
    }
}
