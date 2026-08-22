package io.haoblog.content.domain;

import io.haoblog.shared.id.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tag")
public class Tag {
    @Id
    private UUID id = UuidV7.generate();
    @Column(nullable = false, unique = true, length = 120)
    private String name;
    @Column(nullable = false, unique = true, length = 160)
    private String slug;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Tag() {}

    public Tag(String name, String slug, Instant now) {
        this.name = name;
        this.slug = Slug.normalizeRequired(slug);
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(String name, String slug, Instant now) {
        this.name = name;
        this.slug = Slug.normalizeRequired(slug);
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getSlug() { return slug; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
