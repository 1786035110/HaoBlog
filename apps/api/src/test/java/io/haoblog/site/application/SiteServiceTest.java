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
}
