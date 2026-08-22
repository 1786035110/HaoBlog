package io.haoblog.content.application;

import io.haoblog.content.persistence.ArticleRepository;
import io.haoblog.content.persistence.ArticleRevisionRepository;
import io.haoblog.media.application.MediaReferenceQuery;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ContentMediaReferenceQuery implements MediaReferenceQuery {
    private final ArticleRepository articles;
    private final ArticleRevisionRepository revisions;

    public ContentMediaReferenceQuery(ArticleRepository articles, ArticleRevisionRepository revisions) {
        this.articles = articles;
        this.revisions = revisions;
    }

    @Override
    public boolean isReferenced(UUID mediaId, String publicUrl) {
        return articles.existsByCoverMediaId(mediaId)
                || revisions.existsByCoverMediaId(mediaId)
                || articles.existsByMarkdownSourceContaining(publicUrl)
                || revisions.existsByMarkdownSourceContaining(publicUrl);
    }
}
