package io.haoblog.comment.application;

import io.haoblog.comment.domain.Comment;
import io.haoblog.comment.domain.CommentStatus;
import io.haoblog.comment.persistence.CommentRepository;
import io.haoblog.content.application.ArticleCommentLookup;
import io.haoblog.shared.outbox.OutboxEventRepository;
import io.haoblog.shared.web.ProblemException;
import io.haoblog.site.application.SiteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {
    private static final UUID ARTICLE_ID = UUID.randomUUID();

    @Mock CommentRepository comments;
    @Mock OutboxEventRepository outbox;
    @Mock ArticleCommentLookup articles;
    @Mock SiteService site;
    private MutableClock clock;
    private CommentSecurityService security;
    private CommentChallengeService challenges;
    private CommentRateLimiter limiter;
    private CommentService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-08-22T00:00:00Z"));
        security = new CommentSecurityService(Base64.getEncoder().encodeToString(
                "01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8)));
        challenges = new CommentChallengeService(clock, security);
        limiter = new CommentRateLimiter(clock);
        service = new CommentService(comments, outbox, articles, site, security, challenges, limiter, clock);
        lenient().when(articles.findPublicCommentTarget("post"))
                .thenReturn(Optional.of(new ArticleCommentLookup.Target(ARTICLE_ID, true)));
        lenient().when(site.get()).thenReturn(new SiteService.SiteResult("HaoBlog", "", "https://example.test", "Hao", true));
    }

    @Test
    void publishesCommentAndOutboxInOneApplicationFlow() {
        var challenge = challenges.issue(ARTICLE_ID);
        clock.advance(Duration.ofSeconds(3));
        var result = service.create("post", command(challenge.token(), "hello"), "A".repeat(43), "192.0.2.1");

        assertEquals(CommentStatus.APPROVED, result.status());
        assertNotNull(result.id());
        assertNotNull(result.deleteToken());
        verify(comments).save(any(Comment.class));
        verify(outbox).save(any());
    }

    @Test
    void rejectsUnsafeContentAndFakeParent() {
        var htmlChallenge = challenges.issue(ARTICLE_ID);
        assertThrows(ProblemException.class, () -> service.create("post", command(htmlChallenge.token(), "<b>x</b>"), null, "192.0.2.1"));

        var protocolChallenge = challenges.issue(ARTICLE_ID);
        var protocol = assertThrows(ProblemException.class, () -> service.create("post",
                command(protocolChallenge.token(), "see javascript:alert(1)"), null, "192.0.2.1"));
        assertEquals("COMMENT_LINK_PROTOCOL_INVALID", protocol.getCode());

        var longChallenge = challenges.issue(ARTICLE_ID);
        var longContent = "x".repeat(2001);
        var tooLong = assertThrows(ProblemException.class, () -> service.create("post",
                command(longChallenge.token(), longContent), null, "192.0.2.1"));
        assertEquals("COMMENT_CONTENT_INVALID", tooLong.getCode());

        var parentChallenge = challenges.issue(ARTICLE_ID);
        clock.advance(Duration.ofSeconds(3));
        var exception = assertThrows(ProblemException.class, () -> service.create("post",
                new CommentService.CreateCommand("Hao", null, "reply", UUID.randomUUID(), parentChallenge.token(), null, null),
                null, "192.0.2.1"));
        assertEquals("COMMENT_PARENT_INVALID", exception.getCode());
    }

    @Test
    void honeypotIsAcceptedWithoutPersistenceAndDuplicateIsAConflict() {
        var honeypotChallenge = challenges.issue(ARTICLE_ID);
        clock.advance(Duration.ofSeconds(3));
        var accepted = service.create("post", new CommentService.CreateCommand(
                "Hao", null, "normal text", null, honeypotChallenge.token(), "filled", null), null, "192.0.2.1");
        assertEquals(CommentStatus.APPROVED, accepted.status());
        assertNull(accepted.id());
        verifyNoInteractions(comments, outbox);

        var duplicateChallenge = challenges.issue(ARTICLE_ID);
        clock.advance(Duration.ofSeconds(3));
        when(comments.existsByArticleIdAndContentFingerprint(eq(ARTICLE_ID), any(byte[].class))).thenReturn(true);
        var duplicate = assertThrows(ProblemException.class, () -> service.create("post",
                command(duplicateChallenge.token(), "duplicate"), null, "192.0.2.1"));
        assertEquals("COMMENT_DUPLICATE", duplicate.getCode());
    }

    @Test
    void globalAndArticleSwitchesHideOrRejectComments() {
        when(site.get()).thenReturn(new SiteService.SiteResult("HaoBlog", "", "https://example.test", "Hao", false));
        var page = service.list("post", 0, 20);
        assertTrue(page.items().isEmpty());
        verifyNoInteractions(comments);

        var challenge = challenges.issue(ARTICLE_ID);
        clock.advance(Duration.ofSeconds(3));
        var disabled = assertThrows(ProblemException.class, () -> service.create("post", command(challenge.token(), "nope"), null, "192.0.2.1"));
        assertEquals("COMMENTING_DISABLED", disabled.getCode());
    }

    @Test
    void listsTopLevelCommentsAndLoadsRepliesWithOneBatchQuery() {
        Comment top = comment("top", null);
        top.moderate(CommentStatus.APPROVED, null, null, clock.instant());
        Comment reply = comment("reply", top.getId());
        reply.moderate(CommentStatus.APPROVED, null, null, clock.instant());
        when(comments.findByArticleIdAndStatusAndParentIdIsNullOrderByCreatedAtAscIdAsc(
                eq(ARTICLE_ID), eq(CommentStatus.APPROVED), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(top), PageRequest.of(0, 20), 1));
        when(comments.findByArticleIdAndStatusAndParentIdInOrderByCreatedAtAscIdAsc(
                eq(ARTICLE_ID), eq(CommentStatus.APPROVED), eq(List.of(top.getId()))))
                .thenReturn(List.of(reply));

        var page = service.list("post", 0, 20);
        assertEquals(1, page.items().size());
        assertEquals(1, page.items().getFirst().replies().size());
        verify(comments, times(1)).findByArticleIdAndStatusAndParentIdInOrderByCreatedAtAscIdAsc(
                eq(ARTICLE_ID), eq(CommentStatus.APPROVED), eq(List.of(top.getId())));
    }

    @Test
    void deletesWithTokenAndClearsUserContentButKeepsAuditStatus() {
        String token = security.newDeleteToken();
        Instant now = clock.instant();
        Comment comment = new Comment(ARTICLE_ID, null, "Hao", security.encryptEmail(UUID.randomUUID(), "a@b.test").ciphertext(),
                null, null, "body", new byte[32], now.atZone(ZoneId.of("UTC")).toLocalDate(),
                new byte[32], security.deleteTokenDigest(token), now);
        // 仅验证删除路径的擦除；实体保存时邮箱 nonce/key version 由创建路径提供。
        when(comments.findById(comment.getId())).thenReturn(Optional.of(comment));
        assertThrows(ProblemException.class, () -> service.delete(comment.getId(), "wrong-token"));
        service.delete(comment.getId(), token);
        assertEquals(CommentStatus.USER_DELETED, comment.getStatus());
        assertEquals("", comment.getNickname());
        assertEquals("", comment.getContent());
        assertNull(comment.getEmailCiphertext());
        assertThrows(ProblemException.class, () -> service.delete(comment.getId(), token));
    }

    private CommentService.CreateCommand command(String challenge, String content) {
        return new CommentService.CreateCommand("Hao", null, content, null, challenge, null, null);
    }

    private Comment comment(String content, UUID parentId) {
        return new Comment(ARTICLE_ID, parentId, "Hao", null, null, null, content, new byte[32],
                clock.instant().atZone(ZoneId.of("UTC")).toLocalDate(), new byte[32], new byte[32], clock.instant());
    }

    private static final class MutableClock extends Clock {
        private Instant current;
        private MutableClock(Instant current) { this.current = current; }
        private void advance(Duration duration) { current = current.plus(duration); }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return current; }
    }
}
