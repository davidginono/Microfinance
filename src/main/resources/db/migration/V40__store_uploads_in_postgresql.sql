CREATE TABLE IF NOT EXISTS stored_uploads (
    id UUID PRIMARY KEY,
    owner_type VARCHAR(60) NOT NULL,
    owner_id VARCHAR(255) NOT NULL,
    category VARCHAR(80) NOT NULL,
    original_name VARCHAR(500) NOT NULL,
    content_type VARCHAR(255) NOT NULL,
    size_bytes BIGINT NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    content BYTEA NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_stored_uploads_owner ON stored_uploads (owner_type, owner_id);
CREATE INDEX IF NOT EXISTS idx_stored_uploads_owner_category ON stored_uploads (owner_type, owner_id, category);

CREATE TABLE IF NOT EXISTS stored_upload_migrations (
    migration_key VARCHAR(120) PRIMARY KEY,
    completed_at TIMESTAMPTZ NOT NULL,
    imported_count BIGINT NOT NULL,
    failed_count BIGINT NOT NULL
);
