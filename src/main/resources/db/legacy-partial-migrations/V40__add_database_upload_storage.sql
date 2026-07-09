CREATE TABLE IF NOT EXISTS stored_uploads (
    id UUID PRIMARY KEY,
    owner_type VARCHAR(40) NOT NULL,
    owner_id VARCHAR(160) NOT NULL,
    category VARCHAR(80) NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(160) NOT NULL,
    size_bytes BIGINT NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    content BYTEA NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS ix_stored_uploads_owner_category
    ON stored_uploads (owner_type, owner_id, category);

CREATE UNIQUE INDEX IF NOT EXISTS ux_stored_uploads_owner_category_id
    ON stored_uploads (owner_type, owner_id, category, id);
