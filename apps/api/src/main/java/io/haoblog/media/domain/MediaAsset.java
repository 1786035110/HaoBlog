package io.haoblog.media.domain;

import io.haoblog.shared.id.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "media_asset")
public class MediaAsset {
    @Id
    private UUID id = UuidV7.generate();
    @Column(name = "object_key", nullable = false, unique = true, length = 512)
    private String objectKey;
    @Column(name = "public_url", length = 2048)
    private String publicUrl;
    @Column(name = "mime_type", nullable = false, length = 127)
    private String mimeType;
    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;
    private Integer width;
    private Integer height;
    @Column(nullable = false, columnDefinition = "bytea")
    private byte[] sha256;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MediaAssetStatus status;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MediaAsset() {}

    public MediaAsset(String objectKey, String publicUrl, String mimeType, long sizeBytes,
                      Integer width, Integer height, byte[] sha256, MediaAssetStatus status, Instant now) {
        if (sha256 == null || sha256.length != 32) throw new IllegalArgumentException("Media hash must be SHA-256");
        this.objectKey = objectKey;
        this.publicUrl = publicUrl;
        this.mimeType = mimeType;
        this.sizeBytes = sizeBytes;
        this.width = width;
        this.height = height;
        this.sha256 = sha256.clone();
        this.status = status;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void markDeleted(Instant now) {
        if (status == MediaAssetStatus.DELETED) return;
        status = MediaAssetStatus.DELETED;
        updatedAt = now;
    }

    public UUID getId() { return id; }
    public String getObjectKey() { return objectKey; }
    public String getPublicUrl() { return publicUrl; }
    public String getMimeType() { return mimeType; }
    public Integer getWidth() { return width; }
    public Integer getHeight() { return height; }
    public long getSizeBytes() { return sizeBytes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public MediaAssetStatus getStatus() { return status; }
    public byte[] getSha256() { return sha256.clone(); }
}
