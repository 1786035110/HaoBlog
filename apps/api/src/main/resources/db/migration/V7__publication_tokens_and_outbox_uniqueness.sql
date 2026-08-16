ALTER TABLE article_preview_token
    ADD COLUMN source_version bigint;

UPDATE article_preview_token token
SET source_version = article.version
FROM article
WHERE article.id = token.article_id;

ALTER TABLE article_preview_token
    ALTER COLUMN source_version SET NOT NULL,
    ADD CONSTRAINT article_preview_token_source_version_check CHECK (source_version >= 0);

CREATE UNIQUE INDEX outbox_article_published_revision_uq
    ON outbox_event (aggregate_id, event_type, (payload ->> 'revisionId'))
    WHERE event_type = 'ARTICLE_PUBLISHED';
