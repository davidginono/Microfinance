ALTER TABLE sacco_stations
    ADD COLUMN IF NOT EXISTS access_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN IF NOT EXISTS payment_due_date DATE,
    ADD COLUMN IF NOT EXISTS access_suspended_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS access_suspended_by_member_id UUID,
    ADD COLUMN IF NOT EXISTS access_restriction_reason VARCHAR(500);

UPDATE sacco_stations
SET access_status = 'ACTIVE'
WHERE access_status IS NULL;
