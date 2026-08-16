CREATE UNIQUE INDEX article_revision_article_source_version_uq
    ON article_revision (article_id, source_version);
