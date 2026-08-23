package io.haoblog.site.application;

import io.haoblog.site.persistence.SiteSettingRepository;
import io.haoblog.site.domain.SiteSetting;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SiteServiceTest {
    @Test
    void normalizesConfiguredPublicOriginAndAuthor() {
        var repository = mock(SiteSettingRepository.class);
        when(repository.findBySiteKey("default")).thenReturn(Optional.of(new SiteSetting("default", "HaoBlog", "Night station")));
        var service = new SiteService(repository, " https://blog.example.test/ ", " Hao ");

        var result = service.get();

        assertEquals("https://blog.example.test", result.siteUrl());
        assertEquals("Hao", result.authorName());
    }

    @Test
    void rejectsNonOriginPublicUrls() {
        var repository = mock(SiteSettingRepository.class);

        assertThrows(IllegalArgumentException.class, () -> new SiteService(repository, "https://blog.example.test/articles", "Hao"));
        assertThrows(IllegalArgumentException.class, () -> new SiteService(repository, "//blog.example.test", "Hao"));
        assertThrows(IllegalArgumentException.class, () -> new SiteService(repository, "javascript:alert(1)", "Hao"));
        assertThrows(IllegalArgumentException.class, () -> new SiteService(repository, "https://user:pass@blog.example.test", "Hao"));
    }

    @Test
    void validatesManifestByProfileAndRejectsEnablingWithoutConfiguration() {
        var repository = mock(SiteSettingRepository.class);
        var setting = new SiteSetting("default", "HaoBlog", "Night station");
        when(repository.findBySiteKey("default")).thenReturn(Optional.of(setting));

        assertEquals("http://localhost:8080/music.json",
                SiteService.normalizeMusicManifestUrl(" http://localhost:8080/music.json ", "local"));
        assertEquals("https://cdn.example.test/music.json",
                SiteService.normalizeMusicManifestUrl("https://cdn.example.test/music.json", "prod"));
        assertThrows(IllegalArgumentException.class,
                () -> SiteService.normalizeMusicManifestUrl("http://cdn.example.test/music.json", "prod"));

        var service = new SiteService(repository, "https://blog.example.test", "Hao", null, "prod");
        var missing = assertThrows(io.haoblog.shared.web.ProblemException.class,
                () -> service.updateSettings(0, true, true, false));
        assertEquals("MUSIC_MANIFEST_NOT_CONFIGURED", missing.getCode());
    }

    @Test
    void publicMusicFlagSafelyTurnsOffWhenManifestIsMissing() {
        var repository = mock(SiteSettingRepository.class);
        var setting = new SiteSetting("default", "HaoBlog", "Night station");
        setting.setMusicEnabled(true);
        setting.setThreeDEnabled(true);
        when(repository.findBySiteKey("default")).thenReturn(Optional.of(setting));

        var service = new SiteService(repository, "https://blog.example.test", "Hao", null, "prod");
        var result = service.get();

        assertEquals(false, result.musicEnabled());
        assertEquals(true, result.threeDEnabled());
        assertEquals(null, result.musicManifestUrl());
    }
}
