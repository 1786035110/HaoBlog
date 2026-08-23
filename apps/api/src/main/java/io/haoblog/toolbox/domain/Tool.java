package io.haoblog.toolbox.domain;

import io.haoblog.shared.id.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Convert;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "tool")
public class Tool {
    @Id
    private UUID id = UuidV7.generate();
    @Column(name = "category_id", nullable = false)
    private UUID categoryId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ToolType type;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ToolStatus status;
    @Column(nullable = false, length = 160)
    private String title;
    @Column(nullable = false, unique = true, length = 160)
    private String slug;
    @Column(length = 600)
    private String description;
    @Column(length = 2048)
    private String url;
    @Column(name = "image_url", length = 2048)
    private String imageUrl;
    @Convert(converter = ToolComponentKeyConverter.class)
    @Column(name = "component_key", length = 32)
    private ToolComponentKey componentKey;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> tags = new ArrayList<>();
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
    @Version
    @Column(nullable = false)
    private long version;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Tool() {}

    public Tool(UUID categoryId, ToolType type, ToolStatus status, String title, String slug,
                String description, String url, String imageUrl, ToolComponentKey componentKey,
                List<String> tags, int sortOrder, Instant now) {
        this.categoryId = require(categoryId, "categoryId");
        this.type = require(type, "type");
        this.status = require(status, "status");
        this.title = title.trim();
        this.slug = ToolSlug.normalizeRequired(slug);
        this.description = description;
        this.url = httpsOrNull(url);
        this.imageUrl = httpsOrNull(imageUrl);
        this.componentKey = componentKey;
        this.tags = copyTags(tags);
        this.sortOrder = sortOrder;
        validateFields(this.type, this.url, this.componentKey);
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(UUID categoryId, ToolType type, ToolStatus status, String title, String slug,
                       String description, String url, String imageUrl, ToolComponentKey componentKey,
                       List<String> tags, int sortOrder, Instant now) {
        this.categoryId = require(categoryId, "categoryId");
        this.type = require(type, "type");
        this.status = require(status, "status");
        this.title = title.trim();
        this.slug = ToolSlug.normalizeRequired(slug);
        this.description = description;
        this.url = httpsOrNull(url);
        this.imageUrl = httpsOrNull(imageUrl);
        this.componentKey = componentKey;
        this.tags = copyTags(tags);
        this.sortOrder = sortOrder;
        validateFields(this.type, this.url, this.componentKey);
        this.updatedAt = now;
    }

    private static void validateFields(ToolType type, String url, ToolComponentKey componentKey) {
        if (type == ToolType.EMBEDDED && (url != null || componentKey == null)) {
            throw new IllegalArgumentException("Embedded tools require a whitelisted componentKey and no url");
        }
        if (type != ToolType.EMBEDDED && (url == null || componentKey != null)) {
            throw new IllegalArgumentException("Link and showcase tools require an https url and no componentKey");
        }
    }

    private static String httpsOrNull(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String value = raw.trim();
        try {
            URI uri = URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) {
                throw new IllegalArgumentException("Only absolute https URLs are allowed");
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Only absolute https URLs are allowed", exception);
        }
        return value;
    }

    private static List<String> copyTags(List<String> values) {
        if (values == null) return new ArrayList<>();
        if (values.size() > 32 || values.stream().anyMatch(value -> value == null || value.isBlank() || value.trim().length() > 64)) {
            throw new IllegalArgumentException("tags are invalid");
        }
        return values.stream().map(String::trim).distinct().toList();
    }

    private static <T> T require(T value, String name) {
        if (value == null) throw new IllegalArgumentException(name + " is required");
        return value;
    }

    public UUID getId() { return id; }
    public UUID getCategoryId() { return categoryId; }
    public ToolType getType() { return type; }
    public ToolStatus getStatus() { return status; }
    public String getTitle() { return title; }
    public String getSlug() { return slug; }
    public String getDescription() { return description; }
    public String getUrl() { return url; }
    public String getImageUrl() { return imageUrl; }
    public ToolComponentKey getComponentKey() { return componentKey; }
    public List<String> getTags() { return List.copyOf(tags); }
    public int getSortOrder() { return sortOrder; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
