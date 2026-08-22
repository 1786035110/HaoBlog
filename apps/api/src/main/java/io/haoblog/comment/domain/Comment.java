package io.haoblog.comment.domain;

import io.haoblog.shared.id.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

@Entity
@Table(name = "comment")
public class Comment {
    @Id
    private UUID id = UuidV7.generate();
    @Column(name = "article_id", nullable = false)
    private UUID articleId;
    @Column(name = "parent_id")
    private UUID parentId;
    @Column(nullable = false, length = 40)
    private String nickname;
    @Column(name = "email_ciphertext")
    private byte[] emailCiphertext;
    @Column(name = "email_nonce")
    private byte[] emailNonce;
    @Column(nullable = false, columnDefinition = "text")
    private String body;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CommentStatus status = CommentStatus.PENDING;
    @Column(name = "ip_hmac", nullable = false)
    private byte[] ipHmac;
    @Column(name = "content_fingerprint", nullable = false)
    private byte[] contentFingerprint;
    @Column(name = "delete_token_digest", nullable = false, unique = true)
    private byte[] deleteTokenDigest;
    @Column(name = "moderated_by")
    private UUID moderatedBy;
    @Column(name = "moderation_reason", length = 600)
    private String moderationReason;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Column(name = "moderated_at")
    private Instant moderatedAt;
    @Column(name = "deleted_at")
    private Instant deletedAt;
    @Version
    @Column(nullable = false)
    private long version;

    protected Comment() {}

    public Comment(UUID articleId, UUID parentId, String nickname, byte[] emailCiphertext,
                   byte[] emailNonce, String body, byte[] ipHmac, byte[] contentFingerprint,
                   byte[] deleteTokenDigest, Instant now) {
        this.articleId = articleId;
        this.parentId = parentId;
        this.nickname = nickname;
        this.emailCiphertext = copy(emailCiphertext);
        this.emailNonce = copy(emailNonce);
        this.body = body;
        this.ipHmac = copy(ipHmac);
        this.contentFingerprint = copy(contentFingerprint);
        this.deleteTokenDigest = copy(deleteTokenDigest);
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getArticleId() { return articleId; }
    public UUID getParentId() { return parentId; }
    public String getNickname() { return nickname; }
    public byte[] getEmailCiphertext() { return copy(emailCiphertext); }
    public byte[] getEmailNonce() { return copy(emailNonce); }
    public String getBody() { return body; }
    public CommentStatus getStatus() { return status; }
    public byte[] getIpHmac() { return copy(ipHmac); }
    public byte[] getContentFingerprint() { return copy(contentFingerprint); }
    public byte[] getDeleteTokenDigest() { return copy(deleteTokenDigest); }
    public UUID getModeratedBy() { return moderatedBy; }
    public String getModerationReason() { return moderationReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getModeratedAt() { return moderatedAt; }
    public Instant getDeletedAt() { return deletedAt; }
    public long getVersion() { return version; }

    private static byte[] copy(byte[] value) {
        return value == null ? null : Arrays.copyOf(value, value.length);
    }
}
