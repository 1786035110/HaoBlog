package io.haoblog.toolbox.domain;

import io.haoblog.shared.id.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tool_category")
public class ToolCategory {
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
    @Version
    @Column(nullable = false)
    private long version;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ToolCategory() {}

    public ToolCategory(String name, String slug, String description, int sortOrder, Instant now) {
        this.name = name.trim();
        this.slug = ToolSlug.normalizeRequired(slug);
        this.description = description;
        this.sortOrder = sortOrder;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(String name, String slug, String description, int sortOrder, Instant now) {
        this.name = name.trim();
        this.slug = ToolSlug.normalizeRequired(slug);
        this.description = description;
        this.sortOrder = sortOrder;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getSlug() { return slug; }
    public String getDescription() { return description; }
    public int getSortOrder() { return sortOrder; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
