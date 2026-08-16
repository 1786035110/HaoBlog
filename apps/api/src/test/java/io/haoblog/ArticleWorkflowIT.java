package io.haoblog;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import io.haoblog.content.persistence.ArticleRepository;
import org.junit.jupiter.api.AfterEach;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class ArticleWorkflowIT {
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
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired ArticleRepository articles;

    @BeforeEach
    void clean() {
        jdbc.execute("TRUNCATE article_tag, article_preview_token, article_revision, article, category, tag, media_asset, outbox_event CASCADE");
    }

    @AfterEach
    void removeRollbackTrigger() {
        jdbc.execute("DROP TRIGGER IF EXISTS reject_publication_outbox ON outbox_event");
        jdbc.execute("DROP FUNCTION IF EXISTS reject_publication_outbox()");
    }

    @Test
    void followsLegalTransitionsAndRejectsIllegalArchive() throws Exception {
        String id = create("workflow-" + UUID.randomUUID().toString().substring(0, 8), "Workflow", "# draft");
        mvc.perform(post("/api/v1/admin/articles/" + id + "/schedule").with(admin()).with(csrf())
                        .contentType("application/json").content("{\"version\":0,\"scheduledAt\":\"2099-01-01T00:00:00Z\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.version").value(1));
        mvc.perform(post("/api/v1/admin/articles/" + id + "/draft").with(admin()).with(csrf())
                        .contentType("application/json").content("{\"version\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DRAFT"));
        mvc.perform(post("/api/v1/admin/articles/" + id + "/publish").with(admin()).with(csrf())
                        .contentType("application/json").content("{\"version\":2}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PUBLISHED"));
        mvc.perform(post("/api/v1/admin/articles/" + id + "/archive").with(admin()).with(csrf())
                        .contentType("application/json").content("{\"version\":3}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ARCHIVED"));
        mvc.perform(post("/api/v1/admin/articles/" + id + "/archive").with(admin()).with(csrf())
                        .contentType("application/json").content("{\"version\":4}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ARTICLE_STATE_CONFLICT"));
    }

    @Test
    void publicationFailureRollsBackRevisionArticleAndOutbox() throws Exception {
        String id = create("rollback-" + UUID.randomUUID().toString().substring(0, 8), "Rollback", "# body");
        jdbc.execute("CREATE OR REPLACE FUNCTION reject_publication_outbox() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'test publication failure'; END; $$");
        jdbc.execute("CREATE TRIGGER reject_publication_outbox BEFORE INSERT ON outbox_event FOR EACH ROW WHEN (NEW.event_type = 'ARTICLE_PUBLISHED') EXECUTE FUNCTION reject_publication_outbox()");

        mvc.perform(post("/api/v1/admin/articles/" + id + "/publish").with(admin()).with(csrf())
                        .contentType("application/json").content("{\"version\":0}"))
                .andExpect(status().isInternalServerError());

        UUID articleId = UUID.fromString(id);
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM article_revision WHERE article_id=?", Integer.class, articleId));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM outbox_event WHERE aggregate_id=?", Integer.class, articleId));
        assertEquals("DRAFT", jdbc.queryForObject("SELECT status FROM article WHERE id=?", String.class, articleId));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM article WHERE id=? AND published_revision_id IS NOT NULL", Integer.class, articleId));
    }

    @Test
    void concurrentPublishHasOneWinnerAndOneOutboxEvent() throws Exception {
        String id = create("concurrent-publish-" + UUID.randomUUID().toString().substring(0, 8), "Concurrent", "# body");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = executor.submit(() -> publishConcurrently(start, id));
            Future<Integer> second = executor.submit(() -> publishConcurrently(start, id));
            start.countDown();
            List<Integer> statuses = List.of(first.get(), second.get());
            assertTrue(statuses.contains(200));
            assertTrue(statuses.contains(409));
            UUID articleId = UUID.fromString(id);
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM article_revision WHERE article_id=?", Integer.class, articleId));
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM outbox_event WHERE aggregate_id=? AND event_type='ARTICLE_PUBLISHED'", Integer.class, articleId));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void secondEditDoesNotLeakIntoPublicRevision() throws Exception {
        String slug = "stable-public-" + UUID.randomUUID().toString().substring(0, 8);
        String id = create(slug, "Published title", "# old body");
        mvc.perform(post("/api/v1/admin/articles/" + id + "/publish").with(admin()).with(csrf())
                        .contentType("application/json").content("{\"version\":0}"))
                .andExpect(status().isOk());
        mvc.perform(put("/api/v1/admin/articles/" + id).with(admin()).with(csrf())
                        .contentType("application/json")
                        .content("{\"version\":1,\"slug\":\"" + slug + "\",\"title\":\"Edited title\",\"markdown\":\"# new body\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/public/articles/" + slug))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Published title"))
                .andExpect(jsonPath("$.markdown").value("# old body"));
    }

    @Test
    void previewTokenIsHashedRevocableAndVersionBound() throws Exception {
        String id = create("preview-" + UUID.randomUUID().toString().substring(0, 8), "Preview", "# preview");
        var created = mvc.perform(post("/api/v1/admin/articles/" + id + "/preview-tokens").with(admin()).with(csrf())
                        .contentType("application/json").content("{\"version\":0}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.token").isString()).andReturn();
        JsonNode body = objectMapper.readTree(created.getResponse().getContentAsString());
        String token = body.get("token").asText();
        String tokenId = body.get("id").asText();
        assertEquals(43, token.length());
        String digest = jdbc.queryForObject("SELECT encode(token_digest, 'hex') FROM article_preview_token WHERE id=?", String.class, UUID.fromString(tokenId));
        assertNotEquals(token, digest);

        mvc.perform(get("/api/v1/public/article-previews/" + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Preview"));
        mvc.perform(put("/api/v1/admin/articles/" + id).with(admin()).with(csrf())
                        .contentType("application/json")
                        .content("{\"version\":0,\"slug\":\"preview-change\",\"title\":\"Changed\",\"markdown\":\"# changed\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/public/article-previews/" + token))
                .andExpect(status().isGone()).andExpect(jsonPath("$.code").value("ARTICLE_PREVIEW_GONE"));

        var current = mvc.perform(post("/api/v1/admin/articles/" + id + "/preview-tokens").with(admin()).with(csrf())
                        .contentType("application/json").content("{\"version\":1}"))
                .andExpect(status().isCreated()).andReturn();
        JsonNode currentBody = objectMapper.readTree(current.getResponse().getContentAsString());
        String currentToken = currentBody.get("token").asText();
        String currentTokenId = currentBody.get("id").asText();
        jdbc.update("UPDATE article_preview_token SET expires_at=now() - interval '1 minute' WHERE id=?",
                UUID.fromString(currentTokenId));
        mvc.perform(get("/api/v1/public/article-previews/" + currentToken))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ARTICLE_PREVIEW_NOT_FOUND"));

        var revocable = mvc.perform(post("/api/v1/admin/articles/" + id + "/preview-tokens").with(admin()).with(csrf())
                        .contentType("application/json").content("{\"version\":1}"))
                .andExpect(status().isCreated()).andReturn();
        JsonNode revocableBody = objectMapper.readTree(revocable.getResponse().getContentAsString());
        String revocableToken = revocableBody.get("token").asText();
        String revocableTokenId = revocableBody.get("id").asText();
        mvc.perform(delete("/api/v1/admin/articles/" + id + "/preview-tokens/" + revocableTokenId).with(admin()).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/public/article-previews/" + revocableToken))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ARTICLE_PREVIEW_NOT_FOUND"));
    }

    private int publishConcurrently(CountDownLatch start, String id) throws Exception {
        start.await();
        return mvc.perform(post("/api/v1/admin/articles/" + id + "/publish").with(admin()).with(csrf())
                        .contentType("application/json").content("{\"version\":0}"))
                .andReturn().getResponse().getStatus();
    }

    private String create(String slug, String title, String markdown) throws Exception {
        var result = mvc.perform(post("/api/v1/admin/articles").with(admin()).with(csrf())
                        .contentType("application/json")
                        .content("{\"slug\":\"" + slug + "\",\"title\":\"" + title + "\",\"markdown\":\"" + markdown + "\"}"))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor admin() {
        return user("admin").roles("ADMIN");
    }
}
