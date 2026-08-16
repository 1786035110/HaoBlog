package io.haoblog.content.persistence;

import io.haoblog.content.domain.Article;
import io.haoblog.content.domain.ArticleStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

import java.time.Instant;
import java.util.List;
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

    @Query("""
            select a.id as id, a.version as version, a.scheduledAt as scheduledAt
            from Article a
            where a.status = :status
              and a.scheduledAt is not null
              and a.scheduledAt <= :now
            order by a.scheduledAt asc, a.id asc
            """)
    List<ScheduledPublicationProjection> findDueScheduled(@Param("status") ArticleStatus status,
                                                           @Param("now") Instant now,
                                                           Pageable pageable);

    @EntityGraph(attributePaths = "tags")
    Optional<Article> findWithTagsById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "tags")
    @Query("select a from Article a where a.id = :id")
    Optional<Article> findWithTagsByIdForUpdate(@Param("id") UUID id);

    @Query("""
            select a from Article a
            where (:status is null or a.status = :status)
              and (:keyword is null or lower(a.title) like lower(concat('%', :keyword, '%')) escape '!'
                   or lower(coalesce(a.slug, '')) like lower(concat('%', :keyword, '%')) escape '!')
            """)
    Page<Article> findAdminArticles(ArticleStatus status, String keyword, Pageable pageable);

    boolean existsByCategoryId(UUID categoryId);

    boolean existsByTags_Id(UUID tagId);

    interface ScheduledPublicationProjection {
        UUID getId();
        long getVersion();
        Instant getScheduledAt();
    }
}
