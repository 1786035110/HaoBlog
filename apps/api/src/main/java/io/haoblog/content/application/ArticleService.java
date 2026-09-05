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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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
        var result = repository.findPublishedFeed(ArticleStatus.PUBLISHED, java.time.Instant.now(clock), PageRequest.of(page, size));
        return new PublishedBatch(result.getContent().stream().map(value -> new PublicFeedArticle(
                value.getId(), value.getSlug(), value.getTitle(), value.getExcerpt(), value.getPublishedAt())).toList(), result.hasNext());
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public GardenArticleBatch listPublicGardenArticles(int limit, int tagLimit) {
        if (limit < 1 || limit > 200 || tagLimit < 1 || tagLimit > 12) throw new IllegalArgumentException("garden limit out of range");
        Map<UUID, GardenArticleBuilder> grouped = new LinkedHashMap<>();
        boolean tagsTruncated = false;
        for (var row : repository.findPublicGardenRows(java.time.Instant.now(clock), limit + 1, tagLimit)) {
            tagsTruncated |= row.getTagsTruncated();
            var article = grouped.computeIfAbsent(row.getArticleId(), ignored -> new GardenArticleBuilder(
                    row.getArticleId(), row.getSlug(), row.getTitle(), row.getExcerpt(), row.getPublishedAt(),
                    taxonomy(row.getCategoryName(), row.getCategorySlug())));
            if (row.getTagSlug() != null && !row.getTagSlug().isBlank()) {
                article.tags.add(taxonomy(row.getTagName(), row.getTagSlug()));
            }
        }
        boolean truncated = grouped.size() > limit;
        return new GardenArticleBatch(grouped.values().stream().limit(limit).map(GardenArticleBuilder::build).toList(),
                truncated || tagsTruncated);
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
    public record GardenTaxonomy(String name, String slug) {}
    public record GardenArticle(UUID id, String slug, String title, String excerpt, java.time.Instant publishedAt,
                                GardenTaxonomy category, List<GardenTaxonomy> tags) {}
    public record GardenArticleBatch(List<GardenArticle> items, boolean truncated) {}
    public record SearchPage(String query, Page<PublicSearchArticle> page) {}
    public record PublicSearchArticle(UUID id, String slug, String title, String excerpt,
                                      java.time.Instant publishedAt, UUID coverMediaId, boolean commentsEnabled) {}

    private static GardenTaxonomy taxonomy(String name, String slug) {
        if (slug == null || slug.isBlank()) return null;
        return new GardenTaxonomy(name == null || name.isBlank() ? slug : name.trim(), slug.trim().toLowerCase(Locale.ROOT));
    }

    private static final class GardenArticleBuilder {
        private final UUID id;
        private final String slug;
        private final String title;
        private final String excerpt;
        private final java.time.Instant publishedAt;
        private final GardenTaxonomy category;
        private final java.util.Set<GardenTaxonomy> tags = new LinkedHashSet<>();

        private GardenArticleBuilder(UUID id, String slug, String title, String excerpt,
                                     java.time.Instant publishedAt, GardenTaxonomy category) {
            this.id = id;
            this.slug = slug;
            this.title = title;
            this.excerpt = excerpt;
            this.publishedAt = publishedAt;
            this.category = category;
        }

        private GardenArticle build() {
            return new GardenArticle(id, slug, title, excerpt, publishedAt, category, List.copyOf(tags));
        }
    }
}
