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
    }

    @Autowired MockMvc mvc;
    @Autowired ArticleRepository articles;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void seed() {
        articles.deleteAll();
        Instant now = Instant.now();
        articles.save(new Article("visible", "Visible", "Now", "# now", ArticleStatus.PUBLISHED, now.minus(1, ChronoUnit.MINUTES), now));
        articles.save(new Article("future", "Future", "Later", "# later", ArticleStatus.PUBLISHED, now.plus(1, ChronoUnit.DAYS), now));
        articles.save(new Article("draft", "Draft", "No", "# draft", ArticleStatus.DRAFT, null, now));
    }

    @Test void migrationAndPublicArticleQueryExcludeFutureAndUnpublishedRows() throws Exception {
        mvc.perform(get("/api/v1/public/articles")).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].slug").value("visible"));
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
}
