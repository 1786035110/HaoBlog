package io.haoblog.comment.application;

import io.haoblog.comment.domain.Comment;
import io.haoblog.comment.persistence.CommentRepository;
import io.haoblog.content.application.ArticleCommentLookup;
import io.haoblog.site.application.SiteService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentNotificationMailerTest {
    private static final UUID COMMENT_ID = UUID.fromString("018f0f1e-7b5c-7000-8000-000000000001");
    private static final UUID ARTICLE_ID = UUID.fromString("018f0f1e-7b5c-7000-8000-000000000002");

    @Mock CommentRepository comments;
    @Mock ArticleCommentLookup articles;
    @Mock SiteService site;
    @Mock JavaMailSender mailSender;

    @Test
    void sendsOnlyRequiredCommentSummaryFields() {
        CommentNotificationProperties properties = properties();
        Comment comment = comment("Hao", "第一行\n第二行");
        when(comments.findById(COMMENT_ID)).thenReturn(Optional.of(comment));
        when(articles.findCommentNotificationArticle(ARTICLE_ID))
                .thenReturn(Optional.of(new ArticleCommentLookup.NotificationArticle("文章标题")));
        when(site.get()).thenReturn(new SiteService.SiteResult("HaoBlog", "desc", "https://blog.example.invalid", "Hao"));

        new CommentNotificationMailer(comments, articles, site, properties, mailSender).send(COMMENT_ID);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage message = captor.getValue();
        assertEquals("owner@example.invalid", message.getTo()[0]);
        assertEquals("notify@example.invalid", message.getFrom());
        assertFalse(message.getText().contains("email"));
        assertEquals(true, message.getText().contains("昵称：Hao"));
        assertEquals(true, message.getText().contains("文章：文章标题"));
        assertEquals(true, message.getText().contains("Studio 管理链接：https://blog.example.invalid/studio/comments?commentId=" + COMMENT_ID));
    }

    @Test
    void hidesMailSenderExceptionDetails() {
        CommentNotificationProperties properties = properties();
        when(comments.findById(COMMENT_ID)).thenReturn(Optional.of(comment("Hao", "正文")));
        when(articles.findCommentNotificationArticle(ARTICLE_ID))
                .thenReturn(Optional.of(new ArticleCommentLookup.NotificationArticle("文章标题")));
        when(site.get()).thenReturn(new SiteService.SiteResult("HaoBlog", "desc", "https://blog.example.invalid", "Hao"));
        doThrow(new MailSendException("SMTP timeout smtp-password owner@example.invalid 正文"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        CommentNotificationException exception = assertThrows(CommentNotificationException.class,
                () -> new CommentNotificationMailer(comments, articles, site, properties, mailSender).send(COMMENT_ID));

        assertEquals("Comment notification could not be sent", exception.getMessage());
    }

    private static CommentNotificationProperties properties() {
        CommentNotificationProperties properties = new CommentNotificationProperties();
        properties.setRecipient("owner@example.invalid");
        properties.setFrom("notify@example.invalid");
        return properties;
    }

    private static Comment comment(String nickname, String content) {
        return new Comment(COMMENT_ID, ARTICLE_ID, null, nickname, null, null, null, content,
                new byte[32], LocalDate.of(2026, 8, 23), new byte[32], new byte[32],
                Instant.parse("2026-08-23T00:00:00Z"));
    }
}
