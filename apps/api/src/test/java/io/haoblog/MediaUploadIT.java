package io.haoblog;

import io.haoblog.media.application.ObjectStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class MediaUploadIT {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:0.8.6-pg17");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.placeholders.admin_username", () -> "admin");
        registry.add("spring.flyway.placeholders.admin_password_hash", () -> "$2a$10$0V.Xs7CLOUYSekm7RKq3Z.iY76KUan/Xbeu5vjmLpX.sVd4pcFpIu");
        registry.add("haoblog.media.oss.enabled", () -> true);
        registry.add("haoblog.media.oss.bucket", () -> "test-bucket");
        registry.add("haoblog.media.oss.region", () -> "cn-guangzhou");
        registry.add("haoblog.media.oss.endpoint", () -> "https://oss-cn-guangzhou.aliyuncs.com");
        registry.add("haoblog.media.oss.access-key-id", () -> "test-id");
        registry.add("haoblog.media.oss.access-key-secret", () -> "test-key");
        registry.add("haoblog.media.oss.public-base-url", () -> "https://cdn.test");
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean ObjectStorage storage;

    @BeforeEach
    void clean() {
        jdbc.execute("TRUNCATE article_tag, article_preview_token, article_revision, article, category, tag, media_asset, media_upload, outbox_event CASCADE");
    }

    @Test
    void completesUploadThroughHttpDatabaseAndStorageBoundaryIdempotently() throws Exception {
        String sha256 = "a".repeat(64);
        UUID uploadId = UUID.randomUUID();
        when(storage.createUploadGrant(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new ObjectStorage.UploadGrant("https://upload.test", Map.of("key", "ignored"), Instant.now().plusSeconds(300)));

        var started = mvc.perform(post("/api/v1/admin/media/uploads").with(user("admin").roles("ADMIN")).with(csrf())
                        .contentType("application/json")
                        .content("{\"mimeType\":\"image/png\",\"sizeBytes\":12,\"width\":640,\"height\":480,\"sha256\":\"" + sha256 + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.uploadId").isString())
                .andReturn();
        uploadId = UUID.fromString(new tools.jackson.databind.ObjectMapper().readTree(started.getResponse().getContentAsString()).get("uploadId").asText());
        String objectKey = jdbc.queryForObject("SELECT object_key FROM media_upload WHERE id=?", String.class, uploadId);
        when(storage.head(objectKey)).thenReturn(new ObjectStorage.StoredObject(objectKey, "image/png", 12, 640, 480, sha256));
        when(storage.publicUrl(objectKey)).thenReturn("https://cdn.test/" + objectKey);

        var completed = mvc.perform(post("/api/v1/admin/media/uploads/" + uploadId + "/complete").with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andReturn();
        mvc.perform(post("/api/v1/admin/media/uploads/" + uploadId + "/complete").with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(new tools.jackson.databind.ObjectMapper().readTree(completed.getResponse().getContentAsString()).get("id").asText()));
        org.junit.jupiter.api.Assertions.assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM media_asset", Integer.class));
    }
}
