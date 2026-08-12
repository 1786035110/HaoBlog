package io.haoblog.content.persistence;

import io.haoblog.content.domain.Article;
import io.haoblog.content.domain.ArticleStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.UUID;
import java.util.Optional;

public interface ArticleRepository extends JpaRepository<Article, UUID> {
    Optional<Article> findBySlug(String slug);

    Optional<Article> findBySlugAndStatusAndPublishedAtIsNotNullAndPublishedAtLessThanEqual(
            String slug, ArticleStatus status, Instant now);

    Page<Article> findByStatusAndPublishedAtIsNotNullAndPublishedAtLessThanEqual(
            ArticleStatus status, Instant now, Pageable pageable);
}
