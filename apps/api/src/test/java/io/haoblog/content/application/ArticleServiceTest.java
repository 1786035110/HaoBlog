package io.haoblog.content.application;

import io.haoblog.content.persistence.ArticleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ArticleServiceTest {
    private ArticleRepository repository;
    private ArticleService service;

    @BeforeEach
    void setUp() {
        repository = mock(ArticleRepository.class);
        service = new ArticleService(repository, Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void rejectsNegativePageWithoutCallingRepository() {
        assertThrows(IllegalArgumentException.class, () -> service.list(-1, 20));
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsZeroSizeWithoutCallingRepository() {
        assertThrows(IllegalArgumentException.class, () -> service.list(0, 0));
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsOversizedPageWithoutCallingRepository() {
        assertThrows(IllegalArgumentException.class, () -> service.list(0, 51));
        verifyNoInteractions(repository);
    }

    @Test
    void acceptsValidPageAndSizeAndQueriesRepository() {
        when(repository.findByStatusAndPublishedAtIsNotNullAndPublishedAtLessThanEqual(
                eq(io.haoblog.content.domain.ArticleStatus.PUBLISHED), any(Instant.class), any()))
                .thenReturn(new PageImpl<>(List.of()));

        assertDoesNotThrow(() -> service.list(2, 50));
        verify(repository).findByStatusAndPublishedAtIsNotNullAndPublishedAtLessThanEqual(
                eq(io.haoblog.content.domain.ArticleStatus.PUBLISHED), any(Instant.class), any());
    }
}
