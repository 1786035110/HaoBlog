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

    @Query("""
            select r from ArticleRevision r, Article a
            where r.id = a.publishedRevisionId
              and a.status in (:published, :scheduled)
              and a.publishedRevisionId is not null
              and a.publishedAt is not null
              and a.publishedAt <= :now
            order by a.publishedAt desc, a.id desc
            """)
    Page<ArticleRevision> findVisible(@Param("published") ArticleStatus published,
                                     @Param("scheduled") ArticleStatus scheduled,
                                     @Param("now") java.time.Instant now,
                                     Pageable pageable);

    @Query("""
            select r from ArticleRevision r, Article a
            where r.id = a.publishedRevisionId
              and r.slug = :slug
              and a.status in (:published, :scheduled)
              and a.publishedRevisionId is not null
              and a.publishedAt is not null
              and a.publishedAt <= :now
            """)
    Optional<ArticleRevision> findVisibleBySlug(@Param("slug") String slug,
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
}
