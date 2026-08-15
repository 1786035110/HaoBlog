package io.haoblog.content.persistence;

import io.haoblog.content.domain.Article;
import io.haoblog.content.domain.ArticleStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.UUID;
import java.util.Optional;

public interface ArticleRepository extends JpaRepository<Article, UUID> {
    Optional<Article> findBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, UUID id);

    boolean existsBySlug(String slug);

    Optional<Article> findBySlugAndStatusAndPublishedAtIsNotNullAndPublishedAtLessThanEqual(
            String slug, ArticleStatus status, Instant now);

    Page<Article> findByStatusAndPublishedAtIsNotNullAndPublishedAtLessThanEqual(
            ArticleStatus status, Instant now, Pageable pageable);

    @EntityGraph(attributePaths = "tags")
    Optional<Article> findWithTagsById(UUID id);

    @Query("""
            select a from Article a
            where (:status is null or a.status = :status)
              and (:keyword is null or lower(a.title) like lower(concat('%', :keyword, '%')) escape '!'
                   or lower(coalesce(a.slug, '')) like lower(concat('%', :keyword, '%')) escape '!')
            """)
    Page<Article> findAdminArticles(ArticleStatus status, String keyword, Pageable pageable);

    boolean existsByCategoryId(UUID categoryId);

    boolean existsByTags_Id(UUID tagId);
}
