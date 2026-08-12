package io.haoblog.content.domain;

import io.haoblog.shared.id.UuidV7;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "article")
public class Article {
    @Id
    private UUID id = UuidV7.generate();
    @Column(nullable = false, unique = true, length = 160)
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

    protected Article() {}
    public Article(String slug, String title, String excerpt, String markdownSource,
                   ArticleStatus status, Instant publishedAt, Instant now) {
        this.slug = slug; this.title = title; this.excerpt = excerpt; this.markdownSource = markdownSource;
        this.status = status; this.publishedAt = publishedAt; this.createdAt = now; this.updatedAt = now;
    }
    public UUID getId() { return id; }
    public String getSlug() { return slug; }
    public String getTitle() { return title; }
    public String getExcerpt() { return excerpt; }
    public String getMarkdownSource() { return markdownSource; }
    public ArticleStatus getStatus() { return status; }
    public Instant getPublishedAt() { return publishedAt; }
}
