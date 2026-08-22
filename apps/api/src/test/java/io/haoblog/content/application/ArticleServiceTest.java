package io.haoblog.content.application;

import io.haoblog.content.persistence.ArticleRevisionRepository;
import io.haoblog.content.domain.ArticleRevision;
import io.haoblog.content.domain.ArticleStatus;
import io.haoblog.media.persistence.MediaAssetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ArticleServiceTest {
    private ArticleRevisionRepository repository;
    private MediaAssetRepository mediaRepository;
    private ArticleService service;

    @BeforeEach
    void setUp() {
        repository = mock(ArticleRevisionRepository.class);
        mediaRepository = mock(MediaAssetRepository.class);
        service = new ArticleService(repository, mediaRepository, Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void rejectsNegativePageWithoutCallingRepository() {
        assertThrows(IllegalArgumentException.class, () -> service.list(-1, 20));
        verifyNoInteractions(repository, mediaRepository);
    }

    @Test
    void rejectsZeroSizeWithoutCallingRepository() {
        assertThrows(IllegalArgumentException.class, () -> service.list(0, 0));
        verifyNoInteractions(repository, mediaRepository);
    }

    @Test
    void rejectsOversizedPageWithoutCallingRepository() {
        assertThrows(IllegalArgumentException.class, () -> service.list(0, 51));
        verifyNoInteractions(repository, mediaRepository);
    }

    @Test
    void acceptsValidPageAndSizeAndQueriesRepository() {
        when(repository.findVisible(eq(ArticleStatus.PUBLISHED), eq(ArticleStatus.SCHEDULED), any(Instant.class), any()))
                .thenReturn(new PageImpl<>(List.of()));

        assertDoesNotThrow(() -> service.list(2, 50));
        verify(repository).findVisible(eq(ArticleStatus.PUBLISHED), eq(ArticleStatus.SCHEDULED), any(Instant.class), any());
    }

    @Test
    void publicDetailUsesPublishedAndDueFilter() {
        Instant publishedAt = Instant.parse("2025-12-31T00:00:00Z");
        Instant modifiedAt = Instant.parse("2026-01-01T00:00:00Z");
        ArticleRevision article = new ArticleRevision(null, 0, "Visible", "visible", "Excerpt", "# Body",
                null, null, null, null, List.of(), null, null, modifiedAt);
        var projection = mock(ArticleRevisionRepository.PublicArticleProjection.class);
        when(projection.getRevision()).thenReturn(article);
        when(projection.getPublishedAt()).thenReturn(publishedAt);
        when(repository.findVisibleBySlug(eq("visible"), eq(ArticleStatus.PUBLISHED), eq(ArticleStatus.SCHEDULED), any(Instant.class)))
                .thenReturn(Optional.of(projection));

        var result = service.findPublicBySlug("visible").orElseThrow();
        assertEquals(article, result.revision());
        assertEquals(publishedAt, result.publishedAt());
        verify(repository).findVisibleBySlug(eq("visible"), eq(ArticleStatus.PUBLISHED), eq(ArticleStatus.SCHEDULED), any(Instant.class));
    }
}
