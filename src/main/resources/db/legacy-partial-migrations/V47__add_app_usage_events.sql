CREATE TABLE IF NOT EXISTS app_usage_events (
    id uuid PRIMARY KEY,
    event_type varchar(32) NOT NULL,
    member_id uuid NOT NULL,
    username varchar(120),
    display_name varchar(180),
    roles varchar(240),
    sacco_id varchar(80),
    station_id varchar(80),
    page_path varchar(240),
    device_type varchar(40),
    browser_family varchar(40),
    occurred_at timestamptz NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_app_usage_events_occurred_at
    ON app_usage_events (occurred_at);

CREATE INDEX IF NOT EXISTS idx_app_usage_events_scope_time
    ON app_usage_events (sacco_id, station_id, occurred_at);

CREATE INDEX IF NOT EXISTS idx_app_usage_events_type_time
    ON app_usage_events (event_type, occurred_at);
