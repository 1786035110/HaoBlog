package io.haoblog.content.domain;

import io.haoblog.shared.id.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "article_preview_token")
public class ArticlePreviewToken {
    @Id
    private UUID id = UuidV7.generate();
    @Column(name = "article_id", nullable = false)
    private UUID articleId;
    @Column(name = "token_digest", nullable = false, columnDefinition = "bytea")
    private byte[] tokenDigest;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "revoked_at")
    private Instant revokedAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ArticlePreviewToken() {}

    public ArticlePreviewToken(UUID articleId, byte[] tokenDigest, Instant expiresAt, Instant createdAt) {
        if (tokenDigest == null || tokenDigest.length != 32) throw new IllegalArgumentException("Preview token digest must be SHA-256");
        this.articleId = articleId;
        this.tokenDigest = tokenDigest.clone();
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getArticleId() { return articleId; }
    public byte[] getTokenDigest() { return tokenDigest.clone(); }
    public Instant getExpiresAt() { return expiresAt; }
}
