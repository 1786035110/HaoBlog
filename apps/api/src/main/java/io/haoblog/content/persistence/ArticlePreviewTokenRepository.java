package io.haoblog.content.persistence;

import io.haoblog.content.domain.ArticlePreviewToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ArticlePreviewTokenRepository extends JpaRepository<ArticlePreviewToken, UUID> {
    Optional<ArticlePreviewToken> findByTokenDigest(byte[] tokenDigest);

    Optional<ArticlePreviewToken> findByIdAndArticleId(UUID id, UUID articleId);
}
