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
}
