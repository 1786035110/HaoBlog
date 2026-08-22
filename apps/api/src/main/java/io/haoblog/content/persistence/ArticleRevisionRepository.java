package io.haoblog.content.persistence;

import io.haoblog.content.domain.ArticleRevision;
import io.haoblog.content.domain.ArticleStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ArticleRevisionRepository extends JpaRepository<ArticleRevision, UUID> {
    boolean existsByArticleId(UUID articleId);

    boolean existsByCoverMediaId(UUID mediaId);

    @Query("select case when count(r) > 0 then true else false end from ArticleRevision r where :publicUrl is not null and r.markdownSource like concat('%', :publicUrl, '%')")
    boolean existsByMarkdownSourceContaining(@Param("publicUrl") String publicUrl);

    @Query("""
            select r.id as id, r.sourceVersion as sourceVersion, r.changeReason as changeReason,
                   r.createdBy as createdBy, r.createdAt as createdAt
            from ArticleRevision r
            where r.articleId = :articleId
            """)
    Page<SummaryProjection> findSummariesByArticleId(@Param("articleId") UUID articleId, Pageable pageable);

    Optional<ArticleRevision> findByIdAndArticleId(UUID id, UUID articleId);

    @Query("""
            select r.title as title
            from ArticleRevision r, Article a
            where r.id = a.publishedRevisionId
              and a.id = :articleId
            """)
    Optional<NotificationArticleProjection> findNotificationArticle(@Param("articleId") UUID articleId);

    @Query("""
            select r as revision, a.publishedAt as publishedAt, a.commentsEnabled as commentsEnabled
            from ArticleRevision r, Article a
            where r.id = a.publishedRevisionId
              and a.status in (:published, :scheduled)
              and a.publishedRevisionId is not null
              and a.publishedAt is not null
              and a.publishedAt <= :now
            order by a.publishedAt desc, a.id desc
            """)
    Page<PublicArticleProjection> findVisible(@Param("published") ArticleStatus published,
                                              @Param("scheduled") ArticleStatus scheduled,
                                              @Param("now") java.time.Instant now,
                                              Pageable pageable);

    @Query("""
            select r as revision, a.publishedAt as publishedAt, a.commentsEnabled as commentsEnabled
            from ArticleRevision r, Article a
            where r.id = a.publishedRevisionId
              and a.status = :published
              and a.publishedRevisionId is not null
              and a.publishedAt is not null
              and a.publishedAt <= :now
            order by a.publishedAt desc, a.id desc
            """)
    Page<PublicArticleProjection> findPublished(@Param("published") ArticleStatus published,
                                                @Param("now") java.time.Instant now,
                                                Pageable pageable);

    @Query("""
            select r as revision, a.publishedAt as publishedAt, a.commentsEnabled as commentsEnabled
            from ArticleRevision r, Article a
            where r.id = a.publishedRevisionId
              and r.slug = :slug
              and a.status in (:published, :scheduled)
              and a.publishedRevisionId is not null
              and a.publishedAt is not null
              and a.publishedAt <= :now
            """)
    Optional<PublicArticleProjection> findVisibleBySlug(@Param("slug") String slug,
                                                        @Param("published") ArticleStatus published,
                                                        @Param("scheduled") ArticleStatus scheduled,
                                                        @Param("now") java.time.Instant now);

    @Query("""
            select case when count(r) > 0 then true else false end
            from ArticleRevision r, Article a
            where r.id = a.publishedRevisionId
              and a.id <> :articleId
              and r.slug = :slug
              and a.status in (:published, :scheduled)
              and a.publishedRevisionId is not null
              and a.publishedAt is not null
              and a.publishedAt <= :now
            """)
    boolean existsVisibleSlug(@Param("slug") String slug, @Param("articleId") UUID articleId,
                              @Param("published") ArticleStatus published,
                              @Param("scheduled") ArticleStatus scheduled,
                              @Param("now") java.time.Instant now);

    interface PublicArticleProjection {
        ArticleRevision getRevision();
        java.time.Instant getPublishedAt();
        boolean getCommentsEnabled();
    }

    interface SummaryProjection {
        UUID getId();
        long getSourceVersion();
        String getChangeReason();
        UUID getCreatedBy();
        java.time.Instant getCreatedAt();
    }

    interface NotificationArticleProjection {
        String getTitle();
    }
}
