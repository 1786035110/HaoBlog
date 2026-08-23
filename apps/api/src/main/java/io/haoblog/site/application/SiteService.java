package io.haoblog.site.application;

import io.haoblog.site.persistence.SiteSettingRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;


@Service
public class SiteService {
    private final SiteSettingRepository repository;
    private final String publicBaseUrl;
    private final String authorName;

    public SiteService(SiteSettingRepository repository,
                       @Value("${haoblog.site.public-base-url}") String publicBaseUrl,
                       @Value("${haoblog.site.author-name}") String authorName) {
        this.repository = repository;
        this.publicBaseUrl = normalizePublicBaseUrl(publicBaseUrl);
        if (authorName == null || authorName.isBlank()) {
            throw new IllegalArgumentException("HAOBLOG_AUTHOR_NAME must not be blank");
        }
        this.authorName = authorName.trim();
    }

    public SiteResult get() {
        var setting = repository.findBySiteKey("default").orElseThrow();
        return new SiteResult(setting.getTitle(), setting.getDescription(), publicBaseUrl, authorName, setting.isCommentsEnabled());
    }

    public AdminSiteResult getAdmin() {
        var setting = repository.findBySiteKey("default").orElseThrow();
        return new AdminSiteResult(setting.getTitle(), setting.getDescription(), publicBaseUrl, authorName,
                setting.isCommentsEnabled(), setting.getVersion());
    }

    @org.springframework.transaction.annotation.Transactional
    public AdminSiteResult updateCommentsEnabled(long expectedVersion, boolean commentsEnabled) {
        var setting = repository.findBySiteKey("default").orElseThrow();
        if (setting.getVersion() != expectedVersion) {
            throw new io.haoblog.shared.web.ProblemException("SITE_VERSION_CONFLICT", "Site setting version conflict",
                    "Reload the latest site settings before saving", setting.getVersion());
        }
        setting.setCommentsEnabled(commentsEnabled);
        var saved = repository.saveAndFlush(setting);
        return new AdminSiteResult(saved.getTitle(), saved.getDescription(), publicBaseUrl, authorName,
                saved.isCommentsEnabled(), saved.getVersion());
    }

    public long currentVersion() {
        return repository.findBySiteKey("default").orElseThrow().getVersion();
    }

    static String normalizePublicBaseUrl(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("HAOBLOG_PUBLIC_BASE_URL must be an absolute HTTP(S) origin");
        }
        URI uri;
        try {
            uri = URI.create(raw.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("HAOBLOG_PUBLIC_BASE_URL must be an absolute HTTP(S) origin", exception);
        }
        if (!uri.isAbsolute() || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                || (uri.getPath() != null && !uri.getPath().isEmpty() && !"/".equals(uri.getPath()))) {
            throw new IllegalArgumentException("HAOBLOG_PUBLIC_BASE_URL must be an absolute HTTP(S) origin");
        }
        return raw.trim().replaceFirst("/+$", "");
    }

    public record SiteResult(String title, String description, String siteUrl, String authorName, boolean commentsEnabled) {
        public SiteResult(String title, String description, String siteUrl, String authorName) {
            this(title, description, siteUrl, authorName, true);
        }
    }

    public record AdminSiteResult(String title, String description, String siteUrl, String authorName,
                                  boolean commentsEnabled, long version) {}
}
