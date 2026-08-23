CREATE TABLE tool_category (
    id uuid PRIMARY KEY,
    name varchar(120) NOT NULL UNIQUE,
    slug varchar(160) NOT NULL UNIQUE,
    description varchar(600),
    sort_order integer NOT NULL DEFAULT 0,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT tool_category_slug_format CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$')
);

CREATE TABLE tool (
    id uuid PRIMARY KEY,
    category_id uuid NOT NULL REFERENCES tool_category(id) ON DELETE RESTRICT,
    type varchar(16) NOT NULL CHECK (type IN ('LINK', 'EMBEDDED', 'SHOWCASE')),
    status varchar(16) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
    title varchar(160) NOT NULL,
    slug varchar(160) NOT NULL UNIQUE,
    description varchar(600),
    url varchar(2048),
    image_url varchar(2048),
    component_key varchar(32),
    tags jsonb NOT NULL DEFAULT '[]'::jsonb,
    sort_order integer NOT NULL DEFAULT 0,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT tool_tags_array CHECK (jsonb_typeof(tags) = 'array'),
    CONSTRAINT tool_component_key CHECK (component_key IS NULL OR component_key IN (
        'json-format', 'base64', 'url-codec', 'timestamp', 'regex-test'
    )),
    CONSTRAINT tool_https_urls CHECK (
        (url IS NULL OR url ~ '^https://[^[:space:]]+$')
        AND (image_url IS NULL OR image_url ~ '^https://[^[:space:]]+$')
    ),
    CONSTRAINT tool_type_fields CHECK (
        (type IN ('LINK', 'SHOWCASE') AND url IS NOT NULL AND component_key IS NULL)
        OR (type = 'EMBEDDED' AND url IS NULL AND component_key IS NOT NULL)
    )
);

CREATE INDEX tool_category_sort_idx ON tool (category_id, sort_order, id);
CREATE INDEX tool_filter_idx ON tool (type, status, updated_at, id);
