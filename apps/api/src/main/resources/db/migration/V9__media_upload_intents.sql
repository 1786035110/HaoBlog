CREATE TABLE media_upload (
    id uuid PRIMARY KEY,
    object_key varchar(512) NOT NULL UNIQUE,
    mime_type varchar(127) NOT NULL,
    size_bytes bigint NOT NULL CHECK (size_bytes > 0),
    width integer NOT NULL CHECK (width > 0),
    height integer NOT NULL CHECK (height > 0),
    sha256 varchar(64) NOT NULL CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    expires_at timestamptz NOT NULL,
    completed_media_id uuid REFERENCES media_asset(id) ON DELETE RESTRICT,
    created_at timestamptz NOT NULL
);

CREATE INDEX media_upload_expires_at_idx ON media_upload (expires_at);
