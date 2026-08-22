CREATE UNIQUE INDEX comment_article_fingerprint_uq
    ON comment (article_id, content_fingerprint);
