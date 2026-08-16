package io.haoblog.content.domain;

import io.haoblog.shared.id.UuidV7;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "article")
public class Article {
    @Id
    private UUID id = UuidV7.generate();
    @Column(unique = true, length = 160)
    private String slug;
    @Column(nullable = false, length = 240)
    private String title;
    @Column(length = 600)
    private String excerpt;
    @Column(name = "markdown_source", nullable = false, columnDefinition = "text")
    private String markdownSource;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ArticleStatus status;
    @Column(name = "published_at")
    private Instant publishedAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    @Column(nullable = false)
    private long version;
    @Column(name = "seo_title", length = 240)
    private String seoTitle;
    @Column(name = "seo_description", length = 600)
    private String seoDescription;
    @Column(name = "scheduled_at")
    private Instant scheduledAt;
    @Column(name = "published_revision_id")
    private UUID publishedRevisionId;
    @Column(name = "category_id")
    private UUID categoryId;
    @Column(name = "cover_media_id")
    private UUID coverMediaId;
    @ManyToMany
    @JoinTable(name = "article_tag",
            joinColumns = @JoinColumn(name = "article_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"))
    private Set<Tag> tags = new LinkedHashSet<>();

    protected Article() {}
    public Article(String slug, String title, String excerpt, String markdownSource,
                   ArticleStatus status, Instant publishedAt, Instant now) {
        this.slug = normalizeSlug(slug, status); this.title = title; this.excerpt = excerpt; this.markdownSource = markdownSource;
        this.status = status; this.publishedAt = publishedAt; this.createdAt = now; this.updatedAt = now;
    }
    public void updateWorkingCopy(String slug, String title, String excerpt, String markdownSource,
                                  String seoTitle, String seoDescription, Instant scheduledAt,
                                  UUID categoryId, UUID coverMediaId, Instant now) {
        this.slug = normalizeSlug(slug, status);
        this.title = title;
        this.excerpt = excerpt;
        this.markdownSource = markdownSource;
        this.seoTitle = seoTitle;
        this.seoDescription = seoDescription;
        this.scheduledAt = scheduledAt;
        this.categoryId = categoryId;
        this.coverMediaId = coverMediaId;
        this.updatedAt = now;
    }

    public void replaceTags(Set<Tag> tags) {
        this.tags.clear();
        this.tags.addAll(tags);
    }

    public void archive(Instant now) {
        this.status = ArticleStatus.ARCHIVED;
        this.scheduledAt = null;
        this.updatedAt = now;
    }

    public void schedule(Instant scheduledAt, Instant now) {
        this.status = ArticleStatus.SCHEDULED;
        this.scheduledAt = scheduledAt;
        this.updatedAt = now;
    }

    public void publish(UUID revisionId, Instant now) {
        this.status = ArticleStatus.PUBLISHED;
        this.publishedRevisionId = revisionId;
        this.publishedAt = now;
        this.scheduledAt = null;
        this.updatedAt = now;
    }

    public void returnToDraft(Instant now) {
        this.status = ArticleStatus.DRAFT;
        this.scheduledAt = null;
        this.updatedAt = now;
    }

    private static String normalizeSlug(String raw, ArticleStatus status) {
        String normalized = Slug.normalizeNullable(raw);
        if (status != ArticleStatus.DRAFT && normalized == null) {
            throw new IllegalArgumentException("Non-draft articles require a slug");
        }
        return normalized;
    }
    public UUID getId() { return id; }
    public String getSlug() { return slug; }
    public String getTitle() { return title; }
    public String getExcerpt() { return excerpt; }
    public String getMarkdownSource() { return markdownSource; }
    public ArticleStatus getStatus() { return status; }
    public Instant getPublishedAt() { return publishedAt; }
    public long getVersion() { return version; }
    public String getSeoTitle() { return seoTitle; }
    public String getSeoDescription() { return seoDescription; }
    public Instant getScheduledAt() { return scheduledAt; }
    public UUID getPublishedRevisionId() { return publishedRevisionId; }
    public UUID getCategoryId() { return categoryId; }
    public UUID getCoverMediaId() { return coverMediaId; }
    public Set<Tag> getTags() { return tags; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
