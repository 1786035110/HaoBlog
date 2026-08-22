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
    @Query("""
            select c from Comment c
            where (:status is null or c.status = :status)
              and (:articleId is null or c.articleId = :articleId)
              and (:keyword is null or lower(c.nickname) like lower(concat('%', :keyword, '%'))
                   or lower(c.content) like lower(concat('%', :keyword, '%')))
            """)
    Page<Comment> findAdminComments(@Param("status") CommentStatus status,
                                    @Param("articleId") UUID articleId,
                                    @Param("keyword") String keyword,
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
