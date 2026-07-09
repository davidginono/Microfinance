CREATE TABLE IF NOT EXISTS app_usage_page_metrics (
    id uuid PRIMARY KEY,
    bucket_start timestamptz NOT NULL,
    sacco_id varchar(80) NOT NULL,
    station_id varchar(80) NOT NULL,
    page_path varchar(240) NOT NULL,
    device_type varchar(40) NOT NULL,
    browser_family varchar(40) NOT NULL,
    hit_count bigint NOT NULL DEFAULT 0,
    last_recorded_at timestamptz NOT NULL
);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uk_app_usage_page_metrics_bucket_scope_page_device'
    ) THEN
        ALTER TABLE app_usage_page_metrics
            ADD CONSTRAINT uk_app_usage_page_metrics_bucket_scope_page_device
            UNIQUE (bucket_start, sacco_id, station_id, page_path, device_type, browser_family);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_app_usage_page_metrics_bucket
    ON app_usage_page_metrics (bucket_start);

CREATE INDEX IF NOT EXISTS idx_app_usage_page_metrics_scope_bucket
    ON app_usage_page_metrics (sacco_id, station_id, bucket_start);

CREATE INDEX IF NOT EXISTS ix_notifications_read_created
    ON notifications (read_at, created_at)
    WHERE read_at IS NOT NULL;
