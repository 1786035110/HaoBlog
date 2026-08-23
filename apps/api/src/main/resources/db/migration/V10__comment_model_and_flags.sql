ALTER TABLE site_setting
    ADD COLUMN comments_enabled boolean NOT NULL DEFAULT true,
    ADD COLUMN version bigint NOT NULL DEFAULT 0;

ALTER TABLE article
    ADD COLUMN comments_enabled boolean NOT NULL DEFAULT true;

CREATE TABLE comment (
    id uuid PRIMARY KEY,
    article_id uuid NOT NULL REFERENCES article(id) ON DELETE CASCADE,
    parent_id uuid,
    nickname varchar(40) NOT NULL,
    email_ciphertext bytea,
    email_nonce bytea,
    body text NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'APPROVED', 'SPAM', 'REJECTED', 'USER_DELETED')),
    ip_hmac bytea NOT NULL CHECK (octet_length(ip_hmac) = 32),
    content_fingerprint bytea NOT NULL CHECK (octet_length(content_fingerprint) = 32),
    delete_token_digest bytea NOT NULL UNIQUE CHECK (octet_length(delete_token_digest) = 32),
    moderated_by uuid REFERENCES admin_user(id) ON DELETE SET NULL,
    moderation_reason varchar(600),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    moderated_at timestamptz,
    deleted_at timestamptz,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT comment_id_article_uq UNIQUE (id, article_id),
    CONSTRAINT comment_email_pair CHECK ((email_ciphertext IS NULL) = (email_nonce IS NULL)),
    CONSTRAINT comment_email_nonce_length CHECK (email_nonce IS NULL OR octet_length(email_nonce) = 12),
    CONSTRAINT comment_article_parent_fk FOREIGN KEY (parent_id, article_id)
        REFERENCES comment(id, article_id) ON DELETE RESTRICT
);

CREATE INDEX comment_article_status_created_idx ON comment (article_id, status, created_at, id);
CREATE INDEX comment_parent_created_idx ON comment (parent_id, created_at, id);
