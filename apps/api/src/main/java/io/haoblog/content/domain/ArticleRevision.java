package io.haoblog.content.domain;

import io.haoblog.shared.id.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Immutable
@Table(name = "article_revision")
public class ArticleRevision {
    @Id
    private UUID id = UuidV7.generate();
    @Column(name = "article_id", nullable = false)
    private UUID articleId;
    @Column(name = "source_version", nullable = false)
    private long sourceVersion;
    @Column(nullable = false, length = 240)
    private String title;
    @Column(nullable = false, length = 160)
    private String slug;
    @Column(length = 600)
    private String excerpt;
    @Column(name = "markdown_source", nullable = false, columnDefinition = "text")
    private String markdownSource;
    @Column(name = "seo_title", length = 240)
    private String seoTitle;
    @Column(name = "seo_description", length = 600)
    private String seoDescription;
    @Column(name = "cover_media_id")
    private UUID coverMediaId;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "category_snapshot", columnDefinition = "jsonb")
    private Map<String, String> categorySnapshot;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tag_snapshot", nullable = false, columnDefinition = "jsonb")
    private List<Map<String, String>> tagSnapshot;
    @Column(name = "change_reason", length = 240)
    private String changeReason;
    @Column(name = "created_by")
    private UUID createdBy;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ArticleRevision() {}

    public ArticleRevision(UUID articleId, long sourceVersion, String title, String slug, String excerpt,
                            String markdownSource, String seoTitle, String seoDescription, UUID coverMediaId,
                            Map<String, String> categorySnapshot, List<Map<String, String>> tagSnapshot,
                            String changeReason, UUID createdBy, Instant createdAt) {
        this.articleId = articleId;
        this.sourceVersion = sourceVersion;
        this.title = title;
        this.slug = Slug.normalizeRequired(slug);
        this.excerpt = excerpt;
        this.markdownSource = markdownSource;
        this.seoTitle = seoTitle;
        this.seoDescription = seoDescription;
        this.coverMediaId = coverMediaId;
        this.categorySnapshot = categorySnapshot;
        this.tagSnapshot = tagSnapshot == null ? List.of() : List.copyOf(tagSnapshot);
        this.changeReason = changeReason;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getArticleId() { return articleId; }
    public long getSourceVersion() { return sourceVersion; }
    public String getTitle() { return title; }
    public String getSlug() { return slug; }
    public String getExcerpt() { return excerpt; }
    public String getMarkdownSource() { return markdownSource; }
    public String getSeoTitle() { return seoTitle; }
    public String getSeoDescription() { return seoDescription; }
    public UUID getCoverMediaId() { return coverMediaId; }
    public Map<String, String> getCategorySnapshot() { return categorySnapshot; }
    public List<Map<String, String>> getTagSnapshot() { return tagSnapshot; }
    public String getChangeReason() { return changeReason; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}
