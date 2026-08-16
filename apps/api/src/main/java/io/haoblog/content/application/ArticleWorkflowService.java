package io.haoblog.content.application;

import io.haoblog.content.domain.Article;
import io.haoblog.content.domain.ArticlePreviewToken;
import io.haoblog.content.domain.ArticleRevision;
import io.haoblog.content.domain.ArticleStatus;
import io.haoblog.content.persistence.ArticlePreviewTokenRepository;
import io.haoblog.content.persistence.ArticleRepository;
import io.haoblog.content.persistence.ArticleRevisionRepository;
import io.haoblog.content.persistence.CategoryRepository;
import io.haoblog.shared.outbox.OutboxEvent;
import io.haoblog.shared.outbox.OutboxEventRepository;
import io.haoblog.shared.web.ProblemException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ArticleWorkflowService {
    private static final Duration PREVIEW_TTL = Duration.ofHours(24);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Map<ArticleStatus, Set<ArticleStatus>> ALLOWED_TRANSITIONS = transitions();

    private final ArticleRepository articles;
    private final ArticleRevisionRepository revisions;
    private final ArticlePreviewTokenRepository previewTokens;
    private final OutboxEventRepository outbox;
    private final CategoryRepository categories;
    private final Clock clock;

    public ArticleWorkflowService(ArticleRepository articles, ArticleRevisionRepository revisions,
                                  ArticlePreviewTokenRepository previewTokens, OutboxEventRepository outbox,
                                  CategoryRepository categories, Clock clock) {
        this.articles = articles;
        this.revisions = revisions;
        this.previewTokens = previewTokens;
        this.outbox = outbox;
        this.categories = categories;
        this.clock = clock;
    }

    public static boolean isAllowedTransition(ArticleStatus from, ArticleStatus to) {
        return ALLOWED_TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    @Transactional
    public Article publish(UUID articleId, long expectedVersion) {
        Article article = getArticle(articleId);
        checkVersion(article, expectedVersion);
        requireTransition(article.getStatus(), ArticleStatus.PUBLISHED);
        validatePublish(article, articleId);

        Instant now = Instant.now(clock);
        ArticleRevision revision = revisions.saveAndFlush(new ArticleRevision(
                article.getId(), article.getVersion(), article.getTitle(), article.getSlug(), article.getExcerpt(),
                article.getMarkdownSource(), article.getSeoTitle(), article.getSeoDescription(), article.getCoverMediaId(),
                categorySnapshot(article), tagSnapshot(article), null, null, now));
        article.publish(revision.getId(), now);
        articles.saveAndFlush(article);

        Map<String, Object> payload = Map.of(
                "articleId", article.getId().toString(),
                "revisionId", revision.getId().toString(),
                "eventType", "ARTICLE_PUBLISHED",
                "occurredAt", now.toString());
        outbox.saveAndFlush(new OutboxEvent(article.getId(), "ARTICLE_PUBLISHED", payload, now, now));
        return article;
    }

    @Transactional
    public Article schedule(UUID articleId, long expectedVersion, Instant scheduledAt) {
        Article article = getArticle(articleId);
        checkVersion(article, expectedVersion);
        requireTransition(article.getStatus(), ArticleStatus.SCHEDULED);
        Instant now = Instant.now(clock);
        if (scheduledAt == null || !scheduledAt.isAfter(now)) {
            throw new ProblemException("ARTICLE_SCHEDULE_INVALID", "Invalid schedule time",
                    "The scheduled time must be in the future");
        }
        validatePublish(article, articleId);
        article.schedule(scheduledAt, now);
        return articles.saveAndFlush(article);
    }

    @Transactional
    public Article archive(UUID articleId, long expectedVersion) {
        Article article = getArticle(articleId);
        checkVersion(article, expectedVersion);
        requireTransition(article.getStatus(), ArticleStatus.ARCHIVED);
        article.archive(Instant.now(clock));
        return articles.saveAndFlush(article);
    }

    @Transactional
    public Article returnToDraft(UUID articleId, long expectedVersion) {
        Article article = getArticle(articleId);
        checkVersion(article, expectedVersion);
        requireTransition(article.getStatus(), ArticleStatus.DRAFT);
        article.returnToDraft(Instant.now(clock));
        return articles.saveAndFlush(article);
    }

    @Transactional
    public PreviewTokenResult createPreviewToken(UUID articleId, long expectedVersion) {
        Article article = getArticle(articleId);
        checkVersion(article, expectedVersion);
        if (article.getStatus() == ArticleStatus.ARCHIVED) {
            throw stateConflict(article.getStatus(), ArticleStatus.DRAFT);
        }

        byte[] randomBytes = new byte[32];
        RANDOM.nextBytes(randomBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        Instant now = Instant.now(clock);
        Instant expiresAt = now.plus(PREVIEW_TTL);
        ArticlePreviewToken entity = previewTokens.saveAndFlush(new ArticlePreviewToken(
                articleId, article.getVersion(), sha256(token), expiresAt, now));
        return new PreviewTokenResult(entity.getId(), token, article.getVersion(), expiresAt);
    }

    @Transactional
    public void revokePreviewToken(UUID articleId, UUID tokenId) {
        ArticlePreviewToken token = previewTokens.findByIdAndArticleId(tokenId, articleId)
                .orElseThrow(() -> notFound("ARTICLE_PREVIEW_TOKEN_NOT_FOUND", "Preview token not found"));
        token.revoke(Instant.now(clock));
        previewTokens.saveAndFlush(token);
    }

    @Transactional(readOnly = true)
    public Article preview(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw notFound("ARTICLE_PREVIEW_NOT_FOUND", "Preview is not available");
        }
        ArticlePreviewToken token = previewTokens.findByTokenDigest(sha256(rawToken))
                .orElseThrow(() -> notFound("ARTICLE_PREVIEW_NOT_FOUND", "Preview is not available"));
        Instant now = Instant.now(clock);
        if (token.getRevokedAt() != null || !token.getExpiresAt().isAfter(now)) {
            throw notFound("ARTICLE_PREVIEW_NOT_FOUND", "Preview is not available");
        }
        Article article = getArticle(token.getArticleId());
        if (article.getVersion() != token.getSourceVersion()) {
            throw new ProblemException("ARTICLE_PREVIEW_GONE", "Preview is no longer current",
                    "The article changed after this preview token was issued");
        }
        return article;
    }

    private Article getArticle(UUID articleId) {
        return articles.findWithTagsById(articleId)
                .orElseThrow(() -> notFound("ARTICLE_NOT_FOUND", "Article not found"));
    }

    private void validatePublish(Article article, UUID articleId) {
        if (article.getTitle() == null || article.getTitle().isBlank()
                || article.getSlug() == null || article.getSlug().isBlank()
                || article.getMarkdownSource() == null || article.getMarkdownSource().isBlank()) {
            throw new ProblemException("ARTICLE_PUBLISH_INVALID", "Article cannot be published",
                    "Title, slug and Markdown are required");
        }
        if (articles.existsBySlugAndIdNot(article.getSlug(), articleId)
                || revisions.existsVisibleSlug(article.getSlug(), articleId,
                ArticleStatus.PUBLISHED, ArticleStatus.SCHEDULED, Instant.now(clock))) {
            throw new ProblemException("ARTICLE_SLUG_CONFLICT", "Article slug conflict",
                    "The article slug is already in use");
        }
        if (article.getCategoryId() != null && !categories.existsById(article.getCategoryId())) {
            throw new ProblemException("ARTICLE_PUBLISH_INVALID", "Article cannot be published",
                    "The selected category is not available");
        }
    }

    private Map<String, String> categorySnapshot(Article article) {
        if (article.getCategoryId() == null) return null;
        return categories.findById(article.getCategoryId())
                .map(category -> Map.of("id", category.getId().toString(), "name", category.getName(), "slug", category.getSlug()))
                .orElseThrow(() -> new ProblemException("ARTICLE_PUBLISH_INVALID", "Article cannot be published",
                        "The selected category is not available"));
    }

    private static List<Map<String, String>> tagSnapshot(Article article) {
        return article.getTags().stream()
                .sorted((left, right) -> left.getId().compareTo(right.getId()))
                .map(tag -> {
                    Map<String, String> snapshot = new LinkedHashMap<>();
                    snapshot.put("id", tag.getId().toString());
                    snapshot.put("name", tag.getName());
                    snapshot.put("slug", tag.getSlug());
                    return Map.copyOf(snapshot);
                })
                .toList();
    }

    private static void checkVersion(Article article, long expectedVersion) {
        if (article.getVersion() != expectedVersion) {
            throw new ProblemException("ARTICLE_VERSION_CONFLICT", "Article version conflict",
                    "Reload the latest article before changing its publication state", article.getVersion());
        }
    }

    private static void requireTransition(ArticleStatus from, ArticleStatus to) {
        if (!isAllowedTransition(from, to)) throw stateConflict(from, to);
    }

    private static ProblemException stateConflict(ArticleStatus from, ArticleStatus to) {
        return new ProblemException("ARTICLE_STATE_CONFLICT", "Invalid article state transition",
                "Cannot transition article from " + from + " to " + to);
    }

    private static ProblemException notFound(String code, String detail) {
        return new ProblemException(code, "Resource not found", detail);
    }

    private static byte[] sha256(String token) {
        return sha256(token.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to hash preview token", exception);
        }
    }

    private static Map<ArticleStatus, Set<ArticleStatus>> transitions() {
        EnumMap<ArticleStatus, Set<ArticleStatus>> transitions = new EnumMap<>(ArticleStatus.class);
        transitions.put(ArticleStatus.DRAFT, EnumSet.of(ArticleStatus.SCHEDULED, ArticleStatus.PUBLISHED));
        transitions.put(ArticleStatus.SCHEDULED, EnumSet.of(ArticleStatus.DRAFT, ArticleStatus.PUBLISHED));
        transitions.put(ArticleStatus.PUBLISHED, EnumSet.of(ArticleStatus.PUBLISHED, ArticleStatus.SCHEDULED, ArticleStatus.ARCHIVED));
        transitions.put(ArticleStatus.ARCHIVED, EnumSet.of(ArticleStatus.DRAFT));
        return Map.copyOf(transitions);
    }

    public record PreviewTokenResult(UUID id, String token, long articleVersion, Instant expiresAt) {}
}
