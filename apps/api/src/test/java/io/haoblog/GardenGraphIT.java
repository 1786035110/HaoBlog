package io.haoblog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class GardenGraphIT {
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

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.execute("TRUNCATE article_tag, article_preview_token, article_revision, article, category, tag, tool, tool_category CASCADE");
    }

    @Test
    void exposesOnlyPublicSnapshotsActiveToolsAndStableConditionalResponse() throws Exception {
        Instant now = Instant.parse("2026-08-23T00:00:00Z");
        UUID categoryId = UUID.randomUUID();
        jdbc.update("INSERT INTO category(id, name, slug, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
                categoryId, "Backend", "backend", Timestamp.from(now), Timestamp.from(now));
        seedArticle("public-signal", "Public signal", "Published excerpt", "# secret markdown", "PUBLISHED", now.minusSeconds(60),
                categoryId, "[{\"name\":\"Java\",\"slug\":\"java\"},{\"name\":\"Spring\",\"slug\":\"spring\"}]", now);
        seedArticle("future-signal", "Future signal", "Future", "future markdown", "SCHEDULED", now.plusSeconds(60),
                null, "[]", now);
        seedArticle("draft-signal", "Draft signal", "Draft", "draft markdown", "DRAFT", null,
                null, "[]", now);
        seedArticle("archived-signal", "Archived signal", "Archived", "archived markdown", "ARCHIVED", now.minusSeconds(120),
                null, "[]", now);

        UUID toolCategoryId = UUID.randomUUID();
        UUID toolId = UUID.randomUUID();
        jdbc.update("INSERT INTO tool_category(id, name, slug, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
                toolCategoryId, "Utilities", "utilities", Timestamp.from(now), Timestamp.from(now));
        jdbc.update("INSERT INTO tool(id, category_id, type, status, title, slug, description, url, tags, created_at, updated_at) "
                        + "VALUES (?, ?, 'LINK', 'ACTIVE', ?, ?, ?, ?, ?::jsonb, ?, ?)",
                toolId, toolCategoryId, "JSON Tool", "json-tool", "Format JSON", "https://external.example/tool",
                "[\"Java\",\"Spring\"]", Timestamp.from(now), Timestamp.from(now));

        var first = mvc.perform(get("/api/v1/public/garden"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodes.length()").value(6))
                .andExpect(jsonPath("$.edges[?(@.kind == 'CO_OCCURRENCE')].weight").value(org.hamcrest.Matchers.contains(2)))
                .andExpect(jsonPath("$.truncated").value(false))
                .andExpect(header().exists("ETag"))
                .andReturn();
        String body = first.getResponse().getContentAsString();
        assertTrue(body.contains("/tools?tool=json-tool"));
        assertFalse(body.contains("secret markdown"));
        assertFalse(body.contains("https://external.example/tool"));
        assertFalse(body.contains("future-signal"));
        assertFalse(body.contains("draft-signal"));
        assertFalse(body.contains("archived-signal"));

        mvc.perform(get("/api/v1/public/garden").header("If-None-Match", first.getResponse().getHeader("ETag")))
                .andExpect(status().isNotModified()).andExpect(header().exists("ETag"));
    }

    private void seedArticle(String slug, String title, String excerpt, String markdown, String status,
                             Instant publishedAt, UUID categoryId, String tags, Instant now) {
        UUID articleId = UUID.randomUUID();
        UUID revisionId = UUID.randomUUID();
        jdbc.update("INSERT INTO article(id, slug, title, excerpt, markdown_source, status, published_at, created_at, updated_at, version, category_id) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?)",
                articleId, slug, title, excerpt, markdown, status, publishedAt == null ? null : Timestamp.from(publishedAt),
                Timestamp.from(now), Timestamp.from(now), categoryId);
        if ("PUBLISHED".equals(status)) {
            jdbc.update("INSERT INTO article_revision(id, article_id, source_version, title, slug, excerpt, markdown_source, "
                            + "category_snapshot, tag_snapshot, created_at) VALUES (?, ?, 0, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?)",
                    revisionId, articleId, title, slug, excerpt, markdown,
                    categoryId == null ? null : "{\"name\":\"Backend\",\"slug\":\"backend\"}", tags, Timestamp.from(now));
            jdbc.update("UPDATE article SET published_revision_id = ? WHERE id = ?", revisionId, articleId);
        }
    }
}
