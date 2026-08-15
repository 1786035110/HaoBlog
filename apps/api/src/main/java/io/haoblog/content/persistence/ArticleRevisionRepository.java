package io.haoblog.content.persistence;

import io.haoblog.content.domain.ArticleRevision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ArticleRevisionRepository extends JpaRepository<ArticleRevision, UUID> {
}
