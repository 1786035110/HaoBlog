package io.haoblog.site.domain;

import jakarta.persistence.*;
import io.haoblog.shared.id.UuidV7;
import java.util.UUID;

@Entity
@Table(name = "site_setting")
public class SiteSetting {
    @Id private UUID id = UuidV7.generate();
    @Column(name = "site_key", nullable = false, unique = true, length = 64) private String siteKey;
    @Column(nullable = false, length = 160) private String title;
    @Column(nullable = false, length = 600) private String description;
    @Column(name = "comments_enabled", nullable = false) private boolean commentsEnabled = true;
    @Version @Column(nullable = false) private long version;
    protected SiteSetting() {}
    public SiteSetting(String siteKey, String title, String description) {
        this.siteKey = siteKey; this.title = title; this.description = description;
    }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public boolean isCommentsEnabled() { return commentsEnabled; }
    public long getVersion() { return version; }
    public void setCommentsEnabled(boolean commentsEnabled) { this.commentsEnabled = commentsEnabled; }
}
