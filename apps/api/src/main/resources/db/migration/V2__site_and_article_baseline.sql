CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE site_setting (
    id uuid PRIMARY KEY,
    site_key varchar(64) NOT NULL UNIQUE,
    title varchar(160) NOT NULL,
    description varchar(600) NOT NULL
);

INSERT INTO site_setting (id, site_key, title, description)
VALUES ('0198a4f0-0000-7000-8000-000000000001', 'default', 'HaoBlog', '极夜观测站');

CREATE TABLE article (
    id uuid PRIMARY KEY,
    slug varchar(160) NOT NULL UNIQUE,
    title varchar(240) NOT NULL,
    excerpt varchar(600),
    markdown_source text NOT NULL,
    status varchar(16) NOT NULL CHECK (status IN ('DRAFT', 'SCHEDULED', 'PUBLISHED', 'ARCHIVED')),
    published_at timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CHECK ((status = 'PUBLISHED' AND published_at IS NOT NULL) OR status <> 'PUBLISHED')
);

CREATE INDEX article_public_published_idx ON article (published_at DESC, id DESC)
    WHERE status = 'PUBLISHED' AND published_at IS NOT NULL;
