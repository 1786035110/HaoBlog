package io.haoblog.comment.application;

import io.haoblog.comment.domain.Comment;
import io.haoblog.comment.persistence.CommentRepository;
import io.haoblog.content.application.ArticleCommentLookup;
import io.haoblog.site.application.SiteService;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CommentNotificationMailer {
    private static final int SUMMARY_LENGTH = 240;

    private final CommentRepository comments;
    private final ArticleCommentLookup articles;
    private final SiteService site;
    private final CommentNotificationProperties properties;
    private final JavaMailSender mailSender;

    public CommentNotificationMailer(CommentRepository comments, ArticleCommentLookup articles,
                                     SiteService site, CommentNotificationProperties properties,
                                     JavaMailSender mailSender) {
        this.comments = comments;
        this.articles = articles;
        this.site = site;
        this.properties = properties;
        this.mailSender = mailSender;
    }

    @Transactional(readOnly = true)
    public void send(UUID commentId) {
        Comment comment = comments.findById(commentId).orElseThrow(CommentNotificationException::new);
        ArticleCommentLookup.NotificationArticle article = articles
                .findCommentNotificationArticle(comment.getArticleId())
                .orElseThrow(CommentNotificationException::new);
        String recipient = required(properties.getRecipient());
        String from = required(properties.getFrom());
        String studioUrl = site.get().siteUrl() + "/studio/comments?commentId=" + commentId;

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(recipient);
        message.setFrom(from);
        message.setSubject("HaoBlog 新评论已发布");
        message.setText("有一条评论已即时发布，请按需管理\n\n"
                + "昵称：" + comment.getNickname() + "\n"
                + "文章：" + article.title() + "\n"
                + "时间：" + comment.getCreatedAt() + "\n"
                + "正文摘要：" + summarize(comment.getContent()) + "\n"
                + "Studio 管理链接：" + studioUrl + "\n");
        try {
            mailSender.send(message);
        } catch (Exception ignored) {
            throw new CommentNotificationException();
        }
    }

    private static String required(String value) {
        if (value == null || value.isBlank()) throw new CommentNotificationException();
        return value.trim();
    }

    private static String summarize(String content) {
        String normalized = content == null ? "" : content.replaceAll("\\s+", " ").strip();
        if (normalized.codePointCount(0, normalized.length()) <= SUMMARY_LENGTH) return normalized;
        int end = normalized.offsetByCodePoints(0, SUMMARY_LENGTH);
        return normalized.substring(0, end) + "…";
    }
}
