package io.haoblog.media.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "media_upload")
public class MediaUpload {
    @Id
    private UUID id;
    @Column(name = "object_key", nullable = false, unique = true, length = 512)
    private String objectKey;
    @Column(name = "mime_type", nullable = false, length = 127)
    private String mimeType;
    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;
    @Column(nullable = false)
    private int width;
    @Column(nullable = false)
    private int height;
    @Column(nullable = false, length = 64)
    private String sha256;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "completed_media_id")
    private UUID completedMediaId;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected MediaUpload() {}

    public MediaUpload(UUID id, String objectKey, String mimeType, long sizeBytes,
                       int width, int height, String sha256, Instant expiresAt, Instant createdAt) {
        this.id = id;
        this.objectKey = objectKey;
        this.mimeType = mimeType;
        this.sizeBytes = sizeBytes;
        this.width = width;
        this.height = height;
        this.sha256 = sha256;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    public void complete(UUID mediaId) { this.completedMediaId = mediaId; }
    public UUID getId() { return id; }
    public String getObjectKey() { return objectKey; }
    public String getMimeType() { return mimeType; }
    public long getSizeBytes() { return sizeBytes; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public String getSha256() { return sha256; }
    public Instant getExpiresAt() { return expiresAt; }
    public UUID getCompletedMediaId() { return completedMediaId; }
}
