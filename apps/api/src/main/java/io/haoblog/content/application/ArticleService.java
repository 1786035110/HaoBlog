package io.haoblog.content.application;

import io.haoblog.content.domain.ArticleRevision;
import io.haoblog.content.domain.ArticleStatus;
import io.haoblog.content.persistence.ArticleRevisionRepository;
import io.haoblog.media.domain.MediaAsset;
import io.haoblog.media.domain.MediaAssetStatus;
import io.haoblog.media.persistence.MediaAssetRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.text.Normalizer;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ArticleService implements ArticleCommentLookup {
    private final ArticleRevisionRepository repository;
    private final MediaAssetRepository mediaRepository;
    private final Clock clock;
    public ArticleService(ArticleRevisionRepository repository, MediaAssetRepository mediaRepository, Clock clock) {
        this.repository = repository;
        this.mediaRepository = mediaRepository;
        this.clock = clock;
    }
    public PageResult list(int page, int size) {
        if (page < 0 || size < 1 || size > 50) throw new IllegalArgumentException("page/size out of range");
        var pageable = PageRequest.of(page, size);
        Page<ArticleRevisionRepository.PublicArticleProjection> result = repository.findVisible(
                ArticleStatus.PUBLISHED, ArticleStatus.SCHEDULED, java.time.Instant.now(clock), pageable);
        return new PageResult(result.map(this::toPublicArticle));
    }
    public Optional<PublicArticle> findPublicBySlug(String slug) {
        if (slug == null || slug.isBlank()) return Optional.empty();
        return repository.findVisibleBySlug(slug, ArticleStatus.PUBLISHED, ArticleStatus.SCHEDULED,
                        java.time.Instant.now(clock))
                .map(this::toPublicArticle);
    }

    @Override
    public Optional<ArticleCommentLookup.Target> findPublicCommentTarget(String slug) {
        return findPublicBySlug(slug)
                .map(article -> new ArticleCommentLookup.Target(article.articleId(), article.commentsEnabled()));
    }

    @Override
    public Optional<ArticleCommentLookup.NotificationArticle> findCommentNotificationArticle(UUID articleId) {
        return repository.findNotificationArticle(articleId)
                .map(article -> new ArticleCommentLookup.NotificationArticle(article.getTitle()));
    }

    public PublishedBatch listPublishedBatch(int page, int size) {
        if (page < 0 || size < 1 || size > 500) throw new IllegalArgumentException("page/size out of range");
        var result = repository.findPublished(ArticleStatus.PUBLISHED, java.time.Instant.now(clock), PageRequest.of(page, size));
        return new PublishedBatch(result.getContent().stream().map(this::toPublicFeedArticle).toList(), result.hasNext());
    }

    public SearchPage search(String query, int page, int size) {
        String normalizedQuery = normalizeSearchQuery(query);
        if (page < 0 || size < 1 || size > 20) throw new IllegalArgumentException("page/size out of range");
        var result = repository.searchVisible(toLikePattern(normalizedQuery), java.time.Instant.now(clock),
                PageRequest.of(page, size));
        return new SearchPage(normalizedQuery, result.map(projection -> new PublicSearchArticle(
                projection.getId(), projection.getSlug(), projection.getTitle(), projection.getExcerpt(),
                projection.getPublishedAt(), projection.getCoverMediaId(), projection.getCommentsEnabled())));
    }

    public static String normalizeSearchQuery(String query) {
        if (query == null) throw new IllegalArgumentException("query is required");
        String normalized = Normalizer.normalize(query, Normalizer.Form.NFKC).strip();
        int length = normalized.codePointCount(0, normalized.length());
        if (length < 2 || length > 100) throw new IllegalArgumentException("query length out of range");
        return normalized;
    }

    private static String toLikePattern(String query) {
        return "%" + query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }
    public Map<UUID, String> publicCoverUrls(Collection<UUID> mediaIds) {
        if (mediaIds == null || mediaIds.isEmpty()) return Map.of();
        return mediaRepository.findAllByIdInAndStatus(mediaIds, MediaAssetStatus.AVAILABLE).stream()
                .filter(asset -> asset.getMimeType() != null && asset.getMimeType().toLowerCase().startsWith("image/"))
                .filter(asset -> asset.getPublicUrl() != null && !asset.getPublicUrl().isBlank())
                .collect(Collectors.toUnmodifiableMap(MediaAsset::getId, MediaAsset::getPublicUrl));
    }
    private PublicArticle toPublicArticle(ArticleRevisionRepository.PublicArticleProjection projection) {
        return new PublicArticle(projection.getRevision(), projection.getPublishedAt(), projection.getCommentsEnabled());
    }

    private PublicFeedArticle toPublicFeedArticle(ArticleRevisionRepository.PublicArticleProjection projection) {
        var revision = projection.getRevision();
        return new PublicFeedArticle(revision.getArticleId(), revision.getSlug(), revision.getTitle(),
                revision.getExcerpt(), projection.getPublishedAt());
    }

    public record PublicArticle(ArticleRevision revision, java.time.Instant publishedAt, boolean commentsEnabled) {
        public PublicArticle(ArticleRevision revision, java.time.Instant publishedAt) {
            this(revision, publishedAt, true);
        }

        public UUID articleId() {
            return revision.getArticleId();
        }
    }
    public record PageResult(Page<PublicArticle> page) {}
    public record PublishedBatch(List<PublicFeedArticle> items, boolean hasNext) {}
    public record PublicFeedArticle(UUID id, String slug, String title, String excerpt, java.time.Instant publishedAt) {}
    public record SearchPage(String query, Page<PublicSearchArticle> page) {}
    public record PublicSearchArticle(UUID id, String slug, String title, String excerpt,
                                      java.time.Instant publishedAt, UUID coverMediaId, boolean commentsEnabled) {}
}
