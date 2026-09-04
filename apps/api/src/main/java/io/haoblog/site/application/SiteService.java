package io.haoblog.site.application;

import io.haoblog.site.persistence.SiteSettingRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.Arrays;
import java.util.Locale;


@Service
public class SiteService {
    private final SiteSettingRepository repository;
    private final String publicBaseUrl;
    private final String authorName;
    private final String fallbackMusicManifestUrl;
    private final String activeProfiles;

    @Autowired
    public SiteService(SiteSettingRepository repository,
                       @Value("${haoblog.site.public-base-url}") String publicBaseUrl,
                       @Value("${haoblog.site.author-name}") String authorName,
                       @Value("${haoblog.site.music-manifest-url:}") String musicManifestUrl,
                       @Value("${spring.profiles.active:local}") String activeProfiles) {
        this.repository = repository;
        this.publicBaseUrl = normalizePublicBaseUrl(publicBaseUrl);
        if (authorName == null || authorName.isBlank()) {
            throw new IllegalArgumentException("HAOBLOG_AUTHOR_NAME must not be blank");
        }
        this.authorName = authorName.trim();
        this.activeProfiles = activeProfiles == null ? "" : activeProfiles;
        this.fallbackMusicManifestUrl = normalizeMusicManifestUrl(musicManifestUrl, this.activeProfiles);
    }

    public SiteService(SiteSettingRepository repository, String publicBaseUrl, String authorName) {
        this(repository, publicBaseUrl, authorName, null, "local");
    }

    public SiteResult get() {
        var setting = repository.findBySiteKey("default").orElseThrow();
        String manifestUrl = effectiveMusicManifestUrl(setting);
        return new SiteResult(setting.getTitle(), setting.getDescription(), publicBaseUrl, authorName,
                setting.isCommentsEnabled(), setting.isMusicEnabled() && manifestUrl != null,
                setting.isMusicEnabled() ? manifestUrl : null,
                setting.isThreeDEnabled());
    }

    public AdminSiteResult getAdmin() {
        var setting = repository.findBySiteKey("default").orElseThrow();
        return new AdminSiteResult(setting.getTitle(), setting.getDescription(), publicBaseUrl, authorName,
                setting.isCommentsEnabled(), setting.isMusicEnabled(), effectiveMusicManifestUrl(setting),
                setting.isThreeDEnabled(), setting.getVersion());
    }

    @org.springframework.transaction.annotation.Transactional
    public AdminSiteResult updateCommentsEnabled(long expectedVersion, boolean commentsEnabled) {
        return updateSettings(expectedVersion, commentsEnabled, null, null);
    }

    @org.springframework.transaction.annotation.Transactional
    public AdminSiteResult updateSettings(long expectedVersion, Boolean commentsEnabled,
                                          Boolean musicEnabled, Boolean threeDEnabled) {
        return updateSettings(expectedVersion, commentsEnabled, musicEnabled, threeDEnabled, null, false);
    }

    @org.springframework.transaction.annotation.Transactional
    public AdminSiteResult updateSettings(long expectedVersion, Boolean commentsEnabled,
                                          Boolean musicEnabled, Boolean threeDEnabled, String musicManifestUrl) {
        return updateSettings(expectedVersion, commentsEnabled, musicEnabled, threeDEnabled, musicManifestUrl, true);
    }

    private AdminSiteResult updateSettings(long expectedVersion, Boolean commentsEnabled,
                                           Boolean musicEnabled, Boolean threeDEnabled,
                                           String musicManifestUrl, boolean updateManifest) {
        var setting = repository.findBySiteKey("default").orElseThrow();
        if (setting.getVersion() != expectedVersion) {
            throw new io.haoblog.shared.web.ProblemException("SITE_VERSION_CONFLICT", "Site setting version conflict",
                    "Reload the latest site settings before saving", setting.getVersion());
        }
        if (updateManifest) {
            try {
                setting.setMusicManifestUrl(normalizeMusicManifestUrl(musicManifestUrl, activeProfiles));
            } catch (IllegalArgumentException exception) {
                throw new io.haoblog.shared.web.ProblemException("MUSIC_MANIFEST_URL_INVALID",
                        "Invalid music manifest URL", exception.getMessage());
            }
        }
        boolean nextMusicEnabled = musicEnabled == null ? setting.isMusicEnabled() : musicEnabled;
        if (nextMusicEnabled && effectiveMusicManifestUrl(setting) == null) {
            throw new io.haoblog.shared.web.ProblemException("MUSIC_MANIFEST_NOT_CONFIGURED",
                    "Music manifest is not configured", "Configure a music manifest URL before enabling music");
        }
        if (commentsEnabled != null) setting.setCommentsEnabled(commentsEnabled);
        setting.setMusicEnabled(nextMusicEnabled);
        if (threeDEnabled != null) setting.setThreeDEnabled(threeDEnabled);
        var saved = repository.saveAndFlush(setting);
        return new AdminSiteResult(saved.getTitle(), saved.getDescription(), publicBaseUrl, authorName,
                saved.isCommentsEnabled(), saved.isMusicEnabled(), effectiveMusicManifestUrl(saved),
                saved.isThreeDEnabled(), saved.getVersion());
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

    static String normalizeMusicManifestUrl(String raw, String activeProfiles) {
        if (raw == null || raw.isBlank()) return null;
        String value = raw.trim();
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("HAOBLOG_MUSIC_MANIFEST_URL must be an absolute HTTPS URL", exception);
        }
        String host = uri.getHost();
        boolean absoluteHost = uri.isAbsolute() && host != null && uri.getUserInfo() == null
                && uri.getFragment() == null;
        boolean https = "https".equalsIgnoreCase(uri.getScheme());
        boolean localProfile = Arrays.stream((activeProfiles == null ? "" : activeProfiles).split(","))
                .map(String::trim).map(valueProfile -> valueProfile.toLowerCase(Locale.ROOT))
                .anyMatch(profile -> profile.equals("local") || profile.equals("dev") || profile.equals("test"));
        boolean localhostHttp = host != null && "http".equalsIgnoreCase(uri.getScheme()) && localProfile
                && SetOfLocalHosts.contains(host.toLowerCase(Locale.ROOT));
        if (!absoluteHost || (!https && !localhostHttp)) {
            throw new IllegalArgumentException("HAOBLOG_MUSIC_MANIFEST_URL must be HTTPS; local/dev may use localhost HTTP");
        }
        return value;
    }

    private static final java.util.Set<String> SetOfLocalHosts = java.util.Set.of("localhost", "127.0.0.1", "::1", "[::1]");

    private String effectiveMusicManifestUrl(io.haoblog.site.domain.SiteSetting setting) {
        return setting.getMusicManifestUrl() == null ? fallbackMusicManifestUrl : setting.getMusicManifestUrl();
    }

    public record SiteResult(String title, String description, String siteUrl, String authorName,
                             boolean commentsEnabled, boolean musicEnabled, String musicManifestUrl,
                             boolean threeDEnabled) {
        public SiteResult(String title, String description, String siteUrl, String authorName) {
            this(title, description, siteUrl, authorName, true, false, null, false);
        }
        public SiteResult(String title, String description, String siteUrl, String authorName,
                          boolean commentsEnabled) {
            this(title, description, siteUrl, authorName, commentsEnabled, false, null, false);
        }
    }

    public record AdminSiteResult(String title, String description, String siteUrl, String authorName,
                                  boolean commentsEnabled, boolean musicEnabled, String musicManifestUrl,
                                  boolean threeDEnabled,
                                  long version) {
        public AdminSiteResult(String title, String description, String siteUrl, String authorName,
                               boolean commentsEnabled, long version) {
            this(title, description, siteUrl, authorName, commentsEnabled, false, null, false, version);
        }
    }
}
