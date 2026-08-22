package io.haoblog.content.application;

import io.haoblog.content.domain.Slug;
import io.haoblog.content.persistence.ArticleRepository;

import java.util.UUID;

public class ArticleSlugPolicy {
    private final ArticleRepository repository;

    public ArticleSlugPolicy(ArticleRepository repository) {
        this.repository = repository;
    }

    public String normalizeAndCheck(String raw, UUID articleId) {
        String slug = Slug.normalizeNullable(raw);
        if (slug != null && repository.existsBySlugAndIdNot(slug, articleId)) {
            throw new DuplicateArticleSlugException(slug);
        }
        return slug;
    }
}
