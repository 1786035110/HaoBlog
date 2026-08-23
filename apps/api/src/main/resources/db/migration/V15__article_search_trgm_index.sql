CREATE INDEX article_revision_search_trgm_idx
    ON article_revision USING gin (
        (COALESCE(title, '') || ' ' || COALESCE(excerpt, '') || ' ' || markdown_source) gin_trgm_ops
    );
