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
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
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
        registry.add("HAOBLOG_COMMENT_NOTIFICATION_INITIAL_DELAY_MS", () -> "3600000");
        registry.add("HAOBLOG_CONTENT_SCHEDULING_INITIAL_DELAY_MS", () -> "3600000");
        registry.add("spring.mail.host", () -> "127.0.0.1");
        registry.add("spring.mail.properties.mail.smtp.auth", () -> "false");
        registry.add("haoblog.comment.notification.recipient", () -> "owner@example.invalid");
        registry.add("haoblog.comment.notification.from", () -> "notify@example.invalid");
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean ObjectStorage storage;
    @Autowired io.haoblog.media.application.MediaUploadService uploads;
    @Autowired com.zaxxer.hikari.HikariDataSource pool;
    @Autowired io.haoblog.shared.outbox.OutboxEventStateService state;
    @Autowired io.haoblog.shared.outbox.OutboxEventRepository events;
    @Autowired io.haoblog.comment.application.CommentNotificationMailer mailer;
    @Autowired org.springframework.mail.javamail.JavaMailSenderImpl smtp;
    @Autowired io.haoblog.content.persistence.ArticleRepository articles;
    @Autowired io.haoblog.comment.persistence.CommentRepository comments;
    @Autowired io.haoblog.content.application.ScheduledArticlePublisher publisher;
    @Autowired io.haoblog.content.application.ArticleWorkflowService workflow;
    @Autowired org.springframework.scheduling.TaskScheduler scheduler;
    @Autowired org.flywaydb.core.Flyway flyway;
    @Autowired org.springframework.session.jdbc.JdbcIndexedSessionRepository sessions;

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

    private UUID intent() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO media_upload(id,object_key,mime_type,size_bytes,width,height,sha256,expires_at,created_at) VALUES (?,?,'image/png',12,640,480,?,now()+interval '5 minutes',now())",
                id, "media/" + id + ".png", "a".repeat(64));
        when(storage.publicUrl(anyString())).thenAnswer(i -> "https://cdn.test/" + i.getArgument(0));
        return id;
    }

    private ObjectStorage.StoredObject object(String key) {
        return new ObjectStorage.StoredObject(key, "image/png", 12, 640, 480, "a".repeat(64));
    }

    @Test
    void slowOssReleasesConnectionsAndRowLocksAndConcurrentCompletionIsIdempotent() throws Exception {
        UUID id = intent();
        CountDownLatch entered = new CountDownLatch(2), release = new CountDownLatch(1);
        when(storage.head(anyString())).thenAnswer(i -> {
            assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
            entered.countDown();
            assertTrue(release.await(10, TimeUnit.SECONDS));
            return object(i.getArgument(0));
        });
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> uploads.complete(id));
            var second = executor.submit(() -> uploads.complete(id));
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                assertEquals(0, pool.getHikariPoolMXBean().getActiveConnections());
                // 实际 UPDATE 必须立即获得同一行锁，不以源码注解代替释放证明。
                assertEquals(1, jdbc.update("UPDATE media_upload SET expires_at=expires_at WHERE id=?", id));
            } finally { release.countDown(); }
            assertEquals(first.get(10, TimeUnit.SECONDS).getId(), second.get(10, TimeUnit.SECONDS).getId());
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM media_asset", Integer.class));
        }
    }

    @Test
    void rechecksExpiryAndAllMetadataAfterHead() {
        for (String change : java.util.List.of("expires_at=now()-interval '1 second'", "object_key=object_key||'changed'",
                "mime_type='image/jpeg'", "size_bytes=13", "width=641", "height=481", "sha256=repeat('b',64)")) {
            UUID id = intent();
            when(storage.head(anyString())).thenAnswer(i -> {
                jdbc.update("UPDATE media_upload SET " + change + " WHERE id=?", id);
                return object(i.getArgument(0));
            });
            var failure = assertThrows(io.haoblog.shared.web.ProblemException.class, () -> uploads.complete(id));
            assertEquals(change.startsWith("expires") ? "MEDIA_UPLOAD_EXPIRED" : "MEDIA_METADATA_MISMATCH", failure.getCode());
        }
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM media_asset", Integer.class));
    }

    @Test
    void poolQueryAndLockWaitsAreBoundedAndRecover() throws Exception {
        assertEquals(6, pool.getMaximumPoolSize());
        assertEquals(1, pool.getMinimumIdle());
        assertEquals("3s", jdbc.queryForObject("SHOW statement_timeout", String.class));
        assertEquals("1s", jdbc.queryForObject("SHOW lock_timeout", String.class));
        try (var migration = flyway.getConfiguration().getDataSource().getConnection();
             var statement = migration.createStatement(); var result = statement.executeQuery("SHOW statement_timeout")) {
            assertTrue(result.next()); assertEquals("0", result.getString(1));
        }
        long start = System.nanoTime();
        assertThrows(org.springframework.dao.QueryTimeoutException.class, () -> jdbc.execute("SELECT pg_sleep(8)"));
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start) < 5000);
        try (var raw = java.sql.DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            try (var statement = raw.createStatement(); var result = statement.executeQuery("SHOW statement_timeout")) {
                assertTrue(result.next()); assertEquals("0", result.getString(1));
            }
            UUID id = intent();
            raw.setAutoCommit(false);
            raw.createStatement().execute("SELECT id FROM media_upload WHERE id='" + id + "' FOR UPDATE");
            mvc.perform(post("/api/v1/admin/media/uploads/" + id + "/complete").with(user("admin").roles("ADMIN")).with(csrf()))
                    .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("DATABASE_BUSY"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control", "no-store"));
            raw.rollback();
        }
        var connections = new java.util.ArrayList<java.sql.Connection>();
        try {
            for (int i=0; i<6; i++) connections.add(pool.getConnection());
            start = System.nanoTime();
            assertThrows(java.sql.SQLTransientConnectionException.class, () -> pool.getConnection());
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start) < 3500);
        } finally { for (var c : connections) c.close(); }
        assertEquals(1, jdbc.queryForObject("SELECT 1", Integer.class));
    }

    @Test
    void expiredLeasesStopAtFiveAndStaleCompletionCannotOverwriteNewClaim() {
        UUID aggregate = UUID.randomUUID();
        Instant now = Instant.now();
        var event = events.saveAndFlush(new io.haoblog.shared.outbox.OutboxEvent(aggregate, "COMMENT_CREATED",
                Map.of("commentId", aggregate.toString(), "articleId", aggregate.toString(), "eventType", "COMMENT_CREATED", "occurredAt", now.toString()), now.minusSeconds(5), now));
        for (int attempt=1; attempt<=5; attempt++) {
            var claimed = state.claimCommentCreatedBatch();
            assertEquals(1, claimed.size());
            assertEquals(attempt, claimed.getFirst().getAttemptCount());
            if (attempt>1) {
                state.markProcessed(event.getId(), attempt-1);
                state.markFailedOrRetry(event.getId(), attempt-1);
                assertEquals(io.haoblog.shared.outbox.OutboxStatus.PROCESSING, events.findById(event.getId()).orElseThrow().getStatus());
            }
            jdbc.update("UPDATE outbox_event SET available_at=now()-interval '1 second' WHERE id=?", event.getId());
        }
        assertTrue(state.claimCommentCreatedBatch().isEmpty());
        assertEquals(io.haoblog.shared.outbox.OutboxStatus.FAILED, events.findById(event.getId()).orElseThrow().getStatus());
        assertEquals(5, events.findById(event.getId()).orElseThrow().getAttemptCount());
    }

    @Test
    void retryWaitsUntilAvailableAndCompletedEventsAreNotClaimed() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        var event = events.saveAndFlush(new io.haoblog.shared.outbox.OutboxEvent(id, "COMMENT_CREATED",
                Map.of("commentId", id.toString(), "articleId", id.toString(), "eventType", "COMMENT_CREATED", "occurredAt", now.toString()), now.minusSeconds(1), now));
        assertEquals(1, state.claimCommentCreatedBatch().size());
        state.markFailedOrRetry(event.getId(), 1);
        var retry = events.findById(event.getId()).orElseThrow();
        assertEquals(io.haoblog.shared.outbox.OutboxStatus.PENDING, retry.getStatus());
        assertTrue(retry.getAvailableAt().isAfter(now.plusSeconds(8)));
        assertTrue(state.claimCommentCreatedBatch().isEmpty());
        jdbc.update("UPDATE outbox_event SET available_at=now()-interval '1 second' WHERE id=?", event.getId());
        assertEquals(2, state.claimCommentCreatedBatch().getFirst().getAttemptCount());
        state.markProcessed(event.getId(), 2);
        assertTrue(state.claimCommentCreatedBatch().isEmpty());
        assertNotNull(events.findById(event.getId()).orElseThrow().getProcessedAt());
    }

    @Test
    void tricklingSmtpIsActuallyClosedWithoutHoldingConnectionAndPublisherRunsNext() throws Exception {
        Instant now = Instant.now();
        var article = articles.saveAndFlush(new io.haoblog.content.domain.Article("smtp-source", "SMTP source", "excerpt", "# body",
                io.haoblog.content.domain.ArticleStatus.DRAFT, null, now));
        workflow.publish(article.getId(), article.getVersion());
        var comment = comments.saveAndFlush(new io.haoblog.comment.domain.Comment(article.getId(), null, "Synthetic", null, null, null,
                "Synthetic body", new byte[32], java.time.LocalDate.now(), new byte[32], new byte[32], now));
        var due = new io.haoblog.content.domain.Article("smtp-due", "SMTP due", "excerpt", "# body",
                io.haoblog.content.domain.ArticleStatus.DRAFT, null, now);
        due.schedule(now.minusSeconds(5), now);
        articles.saveAndFlush(due);
        try (var server = new java.net.ServerSocket(0); var executor = Executors.newSingleThreadExecutor()) {
            server.setSoTimeout(5000);
            smtp.setPort(server.getLocalPort());
            CountDownLatch connected = new CountDownLatch(1), published = new CountDownLatch(1), failed = new CountDownLatch(1);
            var fake = executor.submit(() -> {
                try (var socket = server.accept()) {
                    connected.countDown();
                    // 每 200ms 一字节：读取超时不会触发，必须由 15s 总期限关闭套接字。
                    for (int i=0; i<100; i++) { socket.getOutputStream().write('2'); socket.getOutputStream().flush(); Thread.sleep(200); }
                } catch (Exception expected) { }
            });
            long start = System.nanoTime();
            scheduler.schedule(() -> { try { mailer.send(comment.getId()); } catch (io.haoblog.comment.application.CommentNotificationException expected) { failed.countDown(); } }, now);
            assertTrue(connected.await(5, TimeUnit.SECONDS));
            assertEquals(0, pool.getHikariPoolMXBean().getActiveConnections());
            assertEquals(1, jdbc.update("UPDATE comment SET nickname=nickname WHERE id=?", comment.getId()));
            var expiredSession = sessions.createSession();
            org.springframework.session.Session session = expiredSession;
            session.setMaxInactiveInterval(java.time.Duration.ofSeconds(1));
            session.setLastAccessedTime(now.minusSeconds(120));
            sessions.save(expiredSession);
            scheduler.schedule(() -> { sessions.cleanUpExpiredSessions(); publisher.publishDueBatch(); published.countDown(); }, Instant.now());
            assertTrue(failed.await(20, TimeUnit.SECONDS));
            assertTrue(published.await(5, TimeUnit.SECONDS));
            assertTrue(TimeUnit.NANOSECONDS.toSeconds(System.nanoTime()-start) < 22);
            publisher.publishDueBatch();
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM article_revision WHERE article_id=?", Integer.class, due.getId()));
            assertEquals("APPROVED", jdbc.queryForObject("SELECT status FROM comment WHERE id=?", String.class, comment.getId()));
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM spring_session WHERE session_id=?", Integer.class, session.getId()));
            fake.get(5, TimeUnit.SECONDS);
        }
    }
}
