DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM article
        WHERE slug IS NOT NULL
          AND slug !~ '^[a-z0-9]+(-[a-z0-9]+)*$'
    ) THEN
        RAISE EXCEPTION 'Existing article slug is not lowercase ASCII kebab-case';
    END IF;
END $$;

CREATE TABLE media_asset (
    id uuid PRIMARY KEY,
    object_key varchar(512) NOT NULL UNIQUE,
    public_url varchar(2048),
    mime_type varchar(127) NOT NULL,
    size_bytes bigint NOT NULL CHECK (size_bytes >= 0),
    width integer CHECK (width IS NULL OR width > 0),
    height integer CHECK (height IS NULL OR height > 0),
    sha256 bytea NOT NULL UNIQUE CHECK (octet_length(sha256) = 32),
    status varchar(16) NOT NULL CHECK (status IN ('AVAILABLE', 'DELETED')),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL
);

CREATE TABLE category (
    id uuid PRIMARY KEY,
    name varchar(120) NOT NULL UNIQUE,
    slug varchar(160) NOT NULL UNIQUE,
    description varchar(600),
    sort_order integer NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT category_slug_format CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$')
);

CREATE TABLE tag (
    id uuid PRIMARY KEY,
    name varchar(120) NOT NULL UNIQUE,
    slug varchar(160) NOT NULL UNIQUE,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT tag_slug_format CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$')
);

CREATE TABLE outbox_event (
    id uuid PRIMARY KEY,
    aggregate_id uuid NOT NULL,
    event_type varchar(64) NOT NULL,
    payload jsonb NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'PROCESSING', 'PROCESSED', 'FAILED')),
    attempt_count integer NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    available_at timestamptz NOT NULL,
    processed_at timestamptz,
    created_at timestamptz NOT NULL,
    CONSTRAINT outbox_article_payload CHECK (
        jsonb_typeof(payload) = 'object'
        AND payload ?& ARRAY['articleId', 'revisionId', 'eventType', 'occurredAt']
        AND (payload - ARRAY['articleId', 'revisionId', 'eventType', 'occurredAt']) = '{}'::jsonb
    )
);

ALTER TABLE article
    ALTER COLUMN slug DROP NOT NULL,
    ADD COLUMN version bigint NOT NULL DEFAULT 0,
    ADD COLUMN seo_title varchar(240),
    ADD COLUMN seo_description varchar(600),
    ADD COLUMN scheduled_at timestamptz,
    ADD COLUMN published_revision_id uuid,
    ADD COLUMN category_id uuid,
    ADD COLUMN cover_media_id uuid,
    ADD CONSTRAINT article_slug_format CHECK (slug IS NULL OR slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    ADD CONSTRAINT article_draft_slug CHECK (status = 'DRAFT' OR slug IS NOT NULL);

CREATE TABLE article_revision (
    id uuid PRIMARY KEY,
    article_id uuid NOT NULL REFERENCES article(id) ON DELETE RESTRICT,
    source_version bigint NOT NULL CHECK (source_version >= 0),
    title varchar(240) NOT NULL,
    slug varchar(160) NOT NULL,
    excerpt varchar(600),
    markdown_source text NOT NULL,
    seo_title varchar(240),
    seo_description varchar(600),
    cover_media_id uuid REFERENCES media_asset(id) ON DELETE RESTRICT,
    category_snapshot jsonb,
    tag_snapshot jsonb NOT NULL DEFAULT '[]'::jsonb,
    change_reason varchar(240),
    created_by uuid REFERENCES admin_user(id) ON DELETE SET NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT article_revision_slug_format CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    CONSTRAINT article_revision_category_snapshot_object CHECK (
        category_snapshot IS NULL OR jsonb_typeof(category_snapshot) = 'object'
    ),
    CONSTRAINT article_revision_tag_snapshot_array CHECK (jsonb_typeof(tag_snapshot) = 'array')
);

CREATE TABLE article_tag (
    article_id uuid NOT NULL REFERENCES article(id) ON DELETE CASCADE,
    tag_id uuid NOT NULL REFERENCES tag(id) ON DELETE RESTRICT,
    PRIMARY KEY (article_id, tag_id)
);

ALTER TABLE article
    ADD CONSTRAINT article_category_fk FOREIGN KEY (category_id) REFERENCES category(id) ON DELETE SET NULL,
    ADD CONSTRAINT article_cover_media_fk FOREIGN KEY (cover_media_id) REFERENCES media_asset(id) ON DELETE SET NULL,
    ADD CONSTRAINT article_published_revision_fk FOREIGN KEY (published_revision_id) REFERENCES article_revision(id) ON DELETE RESTRICT;

INSERT INTO article_revision (
    id, article_id, source_version, title, slug, excerpt, markdown_source,
    seo_title, seo_description, cover_media_id, category_snapshot, tag_snapshot,
    change_reason, created_by, created_at
)
SELECT
    a.id, a.id, a.version, a.title, a.slug, a.excerpt, a.markdown_source,
    a.seo_title, a.seo_description, a.cover_media_id, NULL, '[]'::jsonb,
    'V5 legacy published snapshot', (SELECT id FROM admin_user ORDER BY id LIMIT 1),
    COALESCE(a.published_at, a.updated_at)
FROM article a
WHERE a.status = 'PUBLISHED' AND a.published_revision_id IS NULL;

UPDATE article a
SET published_revision_id = a.id
WHERE a.status = 'PUBLISHED' AND a.published_revision_id IS NULL;

CREATE INDEX article_status_scheduled_idx ON article (status, scheduled_at, id);
CREATE INDEX article_published_revision_idx ON article (published_revision_id);
CREATE INDEX article_revision_article_created_idx ON article_revision (article_id, created_at DESC, id DESC);

CREATE TABLE article_preview_token (
    id uuid PRIMARY KEY,
    article_id uuid NOT NULL REFERENCES article(id) ON DELETE CASCADE,
    token_digest bytea NOT NULL UNIQUE CHECK (octet_length(token_digest) = 32),
    expires_at timestamptz NOT NULL,
    revoked_at timestamptz,
    created_at timestamptz NOT NULL
);

CREATE INDEX article_preview_token_article_idx ON article_preview_token (article_id);

CREATE OR REPLACE FUNCTION prevent_article_revision_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'article_revision rows are immutable';
END;
$$;

CREATE TRIGGER article_revision_immutable
    BEFORE UPDATE OR DELETE ON article_revision
    FOR EACH ROW EXECUTE FUNCTION prevent_article_revision_mutation();
