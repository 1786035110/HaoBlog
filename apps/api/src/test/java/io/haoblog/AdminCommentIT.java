package io.haoblog;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import io.haoblog.comment.application.CommentSecurityService;
import io.haoblog.content.domain.Article;
import io.haoblog.content.domain.ArticleStatus;
import io.haoblog.content.persistence.ArticleRepository;
import io.haoblog.shared.id.UuidV7;
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

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class AdminCommentIT {
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
    @Autowired CommentSecurityService security;
    private UUID articleId;

    @BeforeEach
    void seed() {
        jdbc.execute("TRUNCATE comment, article_revision, article CASCADE");
        jdbc.update("UPDATE site_setting SET comments_enabled=true, version=0 WHERE site_key='default'");
        Instant now = Instant.parse("2020-01-01T00:00:00Z");
        Article article = articles.saveAndFlush(new Article("moderation-signal", "Moderation signal", "Excerpt", "# body",
                ArticleStatus.PUBLISHED, now.minus(1, ChronoUnit.DAYS), now));
        articleId = article.getId();
        UUID revisionId = UuidV7.generate();
        jdbc.update("INSERT INTO article_revision(id, article_id, source_version, title, slug, excerpt, markdown_source, tag_snapshot, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, '[]'::jsonb, ?)",
                revisionId, articleId, 0L, "Moderation signal", "moderation-signal", "Excerpt", "# body", Timestamp.from(now));
        jdbc.update("UPDATE article SET published_revision_id=? WHERE id=?", revisionId, articleId);
        insertComment("Alice", "first signal", "alice@example.com", "PENDING", now.minusSeconds(30));
        insertComment("Bob", "second signal", null, "APPROVED", now.minusSeconds(10));
        insertComment("Alice", "third signal", null, "REJECTED", now);
    }

    @Test
    void protectsAdminEndpointsWithAuthenticationRoleAndCsrf() throws Exception {
        mvc.perform(get("/api/v1/admin/comments")).andExpect(status().isUnauthorized())
                .andExpect(content().contentType("application/problem+json"));
        mvc.perform(get("/api/v1/admin/comments").with(user("reader").roles("USER")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/comments/{id}/moderation", firstComment()).with(user("admin").roles("ADMIN"))
                        .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }

    @Test
    void filtersAndPagesModerationLog() throws Exception {
        mvc.perform(get("/api/v1/admin/comments").with(admin())
                        .param("status", "PENDING").param("articleId", articleId.toString())
                        .param("keyword", "Alice").param("page", "0").param("size", "1").param("direction", "asc"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].nickname").value("Alice"))
                .andExpect(jsonPath("$.items[0].emailMasked").value("a***@example.com"))
                .andExpect(jsonPath("$.items[0].ipHmac").doesNotExist())
                .andExpect(jsonPath("$.items[0].deleteTokenDigest").doesNotExist());
    }

    @Test
    void onlyDetailReturnsDecryptedEmailAndModerationUsesVersionAndAudit() throws Exception {
        UUID id = firstComment();
        var detail = mvc.perform(get("/api/v1/admin/comments/{id}", id).with(admin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.ipHmac").doesNotExist()).andReturn();
        JsonNode body = objectMapper.readTree(detail.getResponse().getContentAsString());
        long version = body.get("version").asLong();

        mvc.perform(post("/api/v1/admin/comments/{id}/moderation", id).with(admin()).with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("version", version, "status", "SPAM", "reason", "链接密度过高"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SPAM"))
                .andExpect(jsonPath("$.moderationReason").value("链接密度过高"))
                .andExpect(jsonPath("$.moderatorId").isNotEmpty()).andExpect(jsonPath("$.version").value(1));

        mvc.perform(post("/api/v1/admin/comments/{id}/moderation", id).with(admin()).with(csrf())
                        .contentType("application/json")
                        .content("{\"version\":0,\"status\":\"APPROVED\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("COMMENT_VERSION_CONFLICT"));

        mvc.perform(post("/api/v1/admin/comments/{id}/moderation", id).with(admin()).with(csrf())
                        .contentType("application/json")
                        .content("{\"version\":1,\"status\":\"PENDING\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("COMMENT_MODERATION_STATE_CONFLICT"));
    }

    @Test
    void globalSiteSwitchChangesPublicSiteAndArticleEtags() throws Exception {
        var site = mvc.perform(get("/api/v1/public/site")).andExpect(status().isOk()).andReturn();
        var article = mvc.perform(get("/api/v1/public/articles/moderation-signal")).andExpect(status().isOk()).andReturn();
        String siteEtag = site.getResponse().getHeader("ETag");
        String articleEtag = article.getResponse().getHeader("ETag");

        mvc.perform(put("/api/v1/admin/site").with(admin()).with(csrf()).contentType("application/json")
                        .content("{\"version\":0,\"commentsEnabled\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.commentsEnabled").value(false));

        mvc.perform(get("/api/v1/public/site").header("If-None-Match", siteEtag))
                .andExpect(status().isOk()).andExpect(header().string("ETag", not(siteEtag)))
                .andExpect(jsonPath("$.commentsEnabled").value(false));
        mvc.perform(get("/api/v1/public/articles/moderation-signal").header("If-None-Match", articleEtag))
                .andExpect(status().isOk()).andExpect(header().string("ETag", not(articleEtag)))
                .andExpect(jsonPath("$.commentsEnabled").value(false));
        mvc.perform(put("/api/v1/admin/site").with(admin()).with(csrf()).contentType("application/json")
                        .content("{\"version\":0,\"commentsEnabled\":true}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SITE_VERSION_CONFLICT"));
    }

    @Test
    void articleSwitchChangesPublicArticleEtag() throws Exception {
        var before = mvc.perform(get("/api/v1/public/articles/moderation-signal")).andExpect(status().isOk()).andReturn();
        var adminArticle = mvc.perform(get("/api/v1/admin/articles/{id}", articleId).with(admin()))
                .andExpect(status().isOk()).andReturn();
        long version = objectMapper.readTree(adminArticle.getResponse().getContentAsString()).get("version").asLong();
        mvc.perform(put("/api/v1/admin/articles/{id}", articleId).with(admin()).with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("version", version, "title", "Moderation signal",
                                "markdown", "# body", "commentsEnabled", false))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.commentsEnabled").value(false));
        mvc.perform(get("/api/v1/public/articles/moderation-signal")
                        .header("If-None-Match", before.getResponse().getHeader("ETag")))
                .andExpect(status().isOk()).andExpect(header().string("ETag", not(before.getResponse().getHeader("ETag"))))
                .andExpect(jsonPath("$.commentsEnabled").value(false));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor admin() {
        return user("admin").roles("ADMIN");
    }

    private UUID firstComment() {
        return jdbc.queryForObject("SELECT id FROM comment WHERE nickname='Alice' AND content='first signal'", UUID.class);
    }

    private void insertComment(String nickname, String content, String email, String status, Instant createdAt) {
        UUID id = UuidV7.generate();
        var encrypted = security.encryptEmail(id, email);
        jdbc.update("""
                INSERT INTO comment(id, article_id, nickname, email_ciphertext, email_nonce, email_key_version,
                    content, status, ip_hmac, ip_hmac_date, content_fingerprint, delete_token_digest, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, articleId, nickname,
                encrypted == null ? null : encrypted.ciphertext(), encrypted == null ? null : encrypted.nonce(),
                encrypted == null ? null : encrypted.keyVersion(), content, status, new byte[32],
                Date.valueOf(LocalDate.ofInstant(createdAt, java.time.ZoneOffset.UTC)),
                security.contentFingerprint(articleId, nickname + content), security.deleteTokenDigest(security.newDeleteToken()),
                Timestamp.from(createdAt), Timestamp.from(createdAt));
    }
}
