package io.haoblog.comment.persistence;

import io.haoblog.comment.domain.Comment;
import io.haoblog.comment.domain.CommentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommentRepository extends JpaRepository<Comment, UUID> {
    Page<Comment> findByArticleIdAndStatusAndParentIdIsNullOrderByCreatedAtAscIdAsc(
            UUID articleId, CommentStatus status, Pageable pageable);

    List<Comment> findByArticleIdAndStatusAndParentIdInOrderByCreatedAtAscIdAsc(
            UUID articleId, CommentStatus status, List<UUID> parentIds);

    Optional<Comment> findByIdAndArticleId(UUID id, UUID articleId);

    Optional<Comment> findByIdAndArticleIdAndStatusAndParentIdIsNull(
            UUID id, UUID articleId, CommentStatus status);

    boolean existsByArticleIdAndContentFingerprint(UUID articleId, byte[] contentFingerprint);
}
