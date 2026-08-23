package io.haoblog.comment.persistence;

import io.haoblog.comment.domain.Comment;
import io.haoblog.comment.domain.CommentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommentRepository extends JpaRepository<Comment, UUID> {
    @Query(value = """
            select c.* from comment c
            where (cast(:status as varchar) is null or c.status = cast(:status as varchar))
              and (cast(:articleId as uuid) is null or c.article_id = cast(:articleId as uuid))
              and (cast(:keyword as text) is null or lower(c.nickname) like concat('%', cast(:keyword as text), '%')
                   or lower(c.content) like concat('%', cast(:keyword as text), '%'))
            order by
              case when cast(:direction as varchar) = 'asc' then c.created_at end asc,
              case when cast(:direction as varchar) <> 'asc' then c.created_at end desc,
              case when cast(:direction as varchar) = 'asc' then c.id end asc,
              case when cast(:direction as varchar) <> 'asc' then c.id end desc
            """,
            countQuery = """
            select count(*) from comment c
            where (cast(:status as varchar) is null or c.status = cast(:status as varchar))
              and (cast(:articleId as uuid) is null or c.article_id = cast(:articleId as uuid))
              and (cast(:keyword as text) is null or lower(c.nickname) like concat('%', cast(:keyword as text), '%')
                   or lower(c.content) like concat('%', cast(:keyword as text), '%'))
            """, nativeQuery = true)
    Page<Comment> findAdminComments(@Param("status") String status,
                                    @Param("articleId") String articleId,
                                    @Param("keyword") String keyword,
                                    @Param("direction") String direction,
                                    Pageable pageable);

    Page<Comment> findByArticleIdAndStatusAndParentIdIsNullOrderByCreatedAtAscIdAsc(
            UUID articleId, CommentStatus status, Pageable pageable);

    List<Comment> findByArticleIdAndStatusAndParentIdInOrderByCreatedAtAscIdAsc(
            UUID articleId, CommentStatus status, List<UUID> parentIds);

    Optional<Comment> findByIdAndArticleId(UUID id, UUID articleId);

    Optional<Comment> findByIdAndArticleIdAndStatusAndParentIdIsNull(
            UUID id, UUID articleId, CommentStatus status);

    boolean existsByArticleIdAndContentFingerprint(UUID articleId, byte[] contentFingerprint);
}
