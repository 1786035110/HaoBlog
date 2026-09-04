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
import java.util.List;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
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
        jdbc.execute("TRUNCATE article_tag, article_preview_token, article_revision, article, category, tag, media_asset, media_upload, outbox_event CASCADE");
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
        Instant publishedAt = Instant.parse("2026-01-01T00:00:00Z");
        Instant modifiedAt = Instant.parse("2026-01-02T00:00:00Z");
        UUID articleId = articles.saveAndFlush(new Article("snapshot-isolation", "Working title", "Working excerpt", "# working",
                ArticleStatus.PUBLISHED, publishedAt, modifiedAt)).getId();
        UUID revisionId = UUID.randomUUID();
        jdbc.update("INSERT INTO article_revision(id, article_id, source_version, title, slug, excerpt, markdown_source, seo_title, seo_description, cover_media_id, tag_snapshot, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, '[]'::jsonb, ?)",
                revisionId, articleId, 1L, "Published title", "snapshot-isolation", "Published excerpt", "# published", "Published SEO", "Published description", mediaId, java.sql.Timestamp.from(modifiedAt));
        jdbc.update("UPDATE article SET published_revision_id=?, title=?, excerpt=?, markdown_source=?, seo_title=?, seo_description=? WHERE id=?",
                revisionId, "Edited working title", "Edited working excerpt", "# edited", "Edited SEO", "Edited description", articleId);

        mvc.perform(get("/api/v1/public/articles/snapshot-isolation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Published title"))
                .andExpect(jsonPath("$.excerpt").value("Published excerpt"))
                .andExpect(jsonPath("$.publishedAt").value("2026-01-01T00:00:00Z"))
                .andExpect(jsonPath("$.modifiedAt").value("2026-01-02T00:00:00Z"))
                .andExpect(jsonPath("$.markdown").value("# published"))
                .andExpect(jsonPath("$.seoTitle").value("Published SEO"))
                .andExpect(jsonPath("$.seoDescription").value("Published description"))
                .andExpect(jsonPath("$.coverImageUrl").value("https://cdn.example.test/snapshot.png"));
        mvc.perform(get("/api/v1/public/articles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.slug == 'snapshot-isolation')].title").value(org.hamcrest.Matchers.contains("Published title")))
                .andExpect(jsonPath("$.items[?(@.slug == 'snapshot-isolation')].publishedAt").value(org.hamcrest.Matchers.contains("2026-01-01T00:00:00Z")))
                .andExpect(jsonPath("$.items[?(@.slug == 'snapshot-isolation')].coverImageUrl").value(org.hamcrest.Matchers.contains("https://cdn.example.test/snapshot.png")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("\"markdown\""))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("\"seoTitle\""))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("\"seoDescription\""))));
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

    @Test
    void searchNormalizesUnicodeMatchesCaseInsensitivelyAndRanksFields() throws Exception {
        seedPublished("search-title", "SIGNAL title", "no match", "# body", Instant.now().minus(3, ChronoUnit.HOURS), Instant.now());
        seedPublished("search-excerpt", "Older title", "SIGNAL excerpt", "# body", Instant.now().minus(2, ChronoUnit.HOURS), Instant.now());
        seedPublished("search-body", "Oldest title", "no match", "# SIGNAL body", Instant.now().minus(1, ChronoUnit.HOURS), Instant.now());

        mvc.perform(get("/api/v1/public/search/articles").param("q", "  ＳＩＧＮＡＬ  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.items[0].title").value("SIGNAL title"))
                .andExpect(jsonPath("$.items[1].title").value("Older title"))
                .andExpect(jsonPath("$.items[2].title").value("Oldest title"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("markdown"))));
    }

    @Test
    void searchEscapesPercentUnderscoreAndBackslashAndKeepsOnlyPublishedSnapshot() throws Exception {
        seedPublished("search-special", "Literal %_\\ path", "special token", "# special", Instant.now(), Instant.now());
        UUID archivedId = seedPublished("search-archived", "Literal %_\\ archived", "special token", "# special", Instant.now(), Instant.now());
        jdbc.update("UPDATE article SET status='ARCHIVED' WHERE id=?", archivedId);

        mvc.perform(get("/api/v1/public/search/articles").param("q", "%_\\"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].slug").value("search-special"));
        mvc.perform(get("/api/v1/public/search/articles").param("q", "Future"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void searchPaginatesEmptyResultsAndIsolatesUnpublishedWorkingChanges() throws Exception {
        for (int index = 0; index < 21; index++) {
            seedPublished("search-page-" + index, "Page match " + index, "page", "# page", Instant.now().minus(index, ChronoUnit.MINUTES), Instant.now());
        }
        mvc.perform(get("/api/v1/public/search/articles").param("q", "page").param("size", "20"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(21)).andExpect(jsonPath("$.items.length()").value(20));
        mvc.perform(get("/api/v1/public/search/articles").param("q", "page").param("page", "1").param("size", "20"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1));
        mvc.perform(get("/api/v1/public/search/articles").param("q", "no-such-signal"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0)).andExpect(jsonPath("$.items.length()").value(0));

        UUID articleId = seedPublished("search-snapshot", "Published snapshot", "published", "# published", Instant.now(), Instant.now());
        jdbc.update("UPDATE article SET title=?, excerpt=?, markdown_source=? WHERE id=?",
                "Unpublished working copy", "unpublished", "# unpublished", articleId);
        mvc.perform(get("/api/v1/public/search/articles").param("q", "Published"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
        mvc.perform(get("/api/v1/public/search/articles").param("q", "Unpublished"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void searchQueryUsesTheTrigramExpressionIndex() {
        jdbc.execute("SET enable_seqscan = off");
        try {
            List<String> plan = jdbc.queryForList("""
                    EXPLAIN (COSTS OFF)
                    SELECT r.id
                    FROM article_revision r
                    JOIN article a ON a.published_revision_id = r.id
                    WHERE a.status IN ('PUBLISHED', 'SCHEDULED')
                      AND a.published_at IS NOT NULL
                      AND a.published_at <= now()
                      AND (coalesce(r.title, '') || ' ' || coalesce(r.excerpt, '') || ' ' || r.markdown_source)
                            ILIKE '%Vis%' ESCAPE chr(92)
                    """, String.class);
            assertTrue(plan.stream().anyMatch(line -> line.contains("article_revision_search_trgm_idx")), plan.toString());
        } finally {
            jdbc.execute("RESET enable_seqscan");
        }
    }

    @Test void feedsContainOnlyPublishedArticlesAndSupportConditionalCaching() throws Exception {
        seedPublished("newer-feed", "Newer feed", "Newest", "# newer", Instant.now().minus(30, ChronoUnit.SECONDS), Instant.now());
        UUID archivedId = seedPublished("archived-feed", "Archived feed", "No", "# archived", Instant.now().minus(1, ChronoUnit.DAYS), Instant.now());
        jdbc.update("UPDATE article SET status='ARCHIVED' WHERE id=?", archivedId);
        articles.saveAndFlush(new Article("scheduled-feed", "Scheduled feed", "No", "# scheduled", ArticleStatus.SCHEDULED,
                Instant.now().minus(1, ChronoUnit.MINUTES), Instant.now()));

        var rss = mvc.perform(get("/rss.xml"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/rss+xml;charset=UTF-8"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Visible")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Newer feed")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Future"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Archived feed"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Scheduled feed"))))
                .andExpect(header().exists("ETag"))
                .andReturn();
        assertEquals(2, count(rss.getResponse().getContentAsString(), "<item>"));
        assertTrue(rss.getResponse().getContentAsString().indexOf("Newer feed")
                < rss.getResponse().getContentAsString().indexOf("Visible"));
        mvc.perform(get("/rss.xml").header("If-None-Match", rss.getResponse().getHeader("ETag")))
                .andExpect(status().isNotModified()).andExpect(content().string(""));

        var sitemap = mvc.perform(get("/sitemap.xml"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/xml;charset=UTF-8"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("http://localhost:3000")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("archived-feed"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("scheduled-feed"))))
                .andExpect(header().exists("ETag"))
                .andReturn();
        assertEquals(7, count(sitemap.getResponse().getContentAsString(), "<url>"));
        mvc.perform(get("/sitemap.xml").header("If-None-Match", sitemap.getResponse().getHeader("ETag")))
                .andExpect(status().isNotModified()).andExpect(content().string(""));
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
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM pg_indexes WHERE indexname='article_revision_search_trgm_idx'", Integer.class));
        String searchIndex = jdbc.queryForObject("SELECT indexdef FROM pg_indexes WHERE indexname='article_revision_search_trgm_idx'", String.class);
        assertTrue(searchIndex.contains("gin") && searchIndex.contains("gin_trgm_ops"));
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

    private static int count(String value, String needle) {
        return value.split(java.util.regex.Pattern.quote(needle), -1).length - 1;
    }
}
