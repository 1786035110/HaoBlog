package io.haoblog.comment.persistence;

import io.haoblog.comment.domain.Comment;
import io.haoblog.comment.domain.CommentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CommentRepository extends JpaRepository<Comment, UUID> {
    Page<Comment> findByArticleIdAndStatusAndParentIdIsNull(UUID articleId, CommentStatus status, Pageable pageable);
}
