package io.haoblog.content.domain;

import io.haoblog.shared.id.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "category")
public class Category {
    @Id
    private UUID id = UuidV7.generate();
    @Column(nullable = false, unique = true, length = 120)
    private String name;
    @Column(nullable = false, unique = true, length = 160)
    private String slug;
    @Column(length = 600)
    private String description;
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Category() {}

    public Category(String name, String slug, String description, int sortOrder, Instant now) {
        this.name = name;
        this.slug = Slug.normalizeRequired(slug);
        this.description = description;
        this.sortOrder = sortOrder;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getSlug() { return slug; }
}
