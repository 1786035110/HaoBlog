package io.haoblog;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.security.test.context.support.WithMockUser;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
class AdminContentIT {
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

    @Test
    void rejectsAnonymousAndMissingCsrfAdminWrites() throws Exception {
        mvc.perform(get("/api/v1/admin/articles"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(post("/api/v1/admin/articles").with(admin()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType("application/problem+json"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createsUpdatesListsAndDeletesDraftWithVersion() throws Exception {
        var created = mvc.perform(post("/api/v1/admin/articles").with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.title").value("未命名草稿"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.version").value(0))
                .andReturn();
        JsonNode body = objectMapper.readTree(created.getResponse().getContentAsString());
        String id = body.get("id").asText();

        mvc.perform(put("/api/v1/admin/articles/" + id).with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0,\"title\":\"Unique Draft " + id + "\",\"markdown\":\"# body\",\"slug\":\"unique-" + id.substring(0, 8) + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));

        mvc.perform(put("/api/v1/admin/articles/" + id).with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0,\"title\":\"stale\",\"markdown\":\"stale\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ARTICLE_VERSION_CONFLICT"));

        mvc.perform(get("/api/v1/admin/articles?keyword=unique-" + id.substring(0, 8) + "&direction=asc").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].version").value(1));

        mvc.perform(delete("/api/v1/admin/articles/" + id).with(admin()).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/admin/articles/" + id).with(admin())).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void taxonomyCrudAndReferencedDeleteConflict() throws Exception {
        var category = mvc.perform(post("/api/v1/admin/categories").with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Category " + UUID.randomUUID() + "\",\"slug\":\"category-" + UUID.randomUUID().toString().substring(0, 8) + "\",\"sortOrder\":1}"))
                .andExpect(status().isCreated()).andReturn();
        JsonNode categoryBody = objectMapper.readTree(category.getResponse().getContentAsString());
        String categoryId = categoryBody.get("id").asText();
        mvc.perform(put("/api/v1/admin/categories/" + categoryId).with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Renamed category\",\"slug\":\"renamed-category\",\"sortOrder\":2}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.slug").value("renamed-category"));
        String slug = "tag-" + UUID.randomUUID().toString().substring(0, 8);
        var tag = mvc.perform(post("/api/v1/admin/tags").with(admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Tag " + UUID.randomUUID() + "\",\"slug\":\"" + slug + "\"}"))
                .andExpect(status().isCreated()).andReturn();
        String tagId = objectMapper.readTree(tag.getResponse().getContentAsString()).get("id").asText();
        mvc.perform(put("/api/v1/admin/tags/" + tagId).with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Renamed tag\",\"slug\":\"renamed-tag\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.slug").value("renamed-tag"));
        var article = mvc.perform(post("/api/v1/admin/articles").with(admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"taxonomy article\",\"markdown\":\"x\",\"categoryId\":\"" + categoryId + "\",\"tagIds\":[\"" + tagId + "\"]}"))
                .andExpect(status().isCreated()).andReturn();
        String articleId = objectMapper.readTree(article.getResponse().getContentAsString()).get("id").asText();

        mvc.perform(delete("/api/v1/admin/categories/" + categoryId).with(admin()).with(csrf()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CATEGORY_IN_USE"));
        mvc.perform(delete("/api/v1/admin/tags/" + tagId).with(admin()).with(csrf()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TAG_IN_USE"));

        mvc.perform(delete("/api/v1/admin/articles/" + articleId).with(admin()).with(csrf())).andExpect(status().isNoContent());
    }

    @Test
    void revisionBearingDraftIsArchivedInsteadOfDeleted() throws Exception {
        var article = mvc.perform(post("/api/v1/admin/articles").with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"revision boundary\",\"markdown\":\"body\"}"))
                .andExpect(status().isCreated()).andReturn();
        String articleId = objectMapper.readTree(article.getResponse().getContentAsString()).get("id").asText();
        String slug = "revision-boundary-" + articleId.substring(0, 8);
        jdbc.update("UPDATE article SET slug=? WHERE id=?", slug, UUID.fromString(articleId));
        jdbc.update("INSERT INTO article_revision(id, article_id, source_version, title, slug, markdown_source, tag_snapshot, created_at) VALUES (?, ?, 0, ?, ?, ?, '[]'::jsonb, now())",
                UUID.randomUUID(), UUID.fromString(articleId), "revision boundary", slug, "body");

        mvc.perform(delete("/api/v1/admin/articles/" + articleId).with(admin()).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ARCHIVED"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void validatesMarkdownByteLimit() throws Exception {
        String markdown = "x".repeat(1024 * 1024 + 1);
        mvc.perform(post("/api/v1/admin/articles").with(admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("markdown", markdown))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor admin() {
        return user("admin").roles("ADMIN");
    }
}
