ALTER TABLE sacco_stations
    ADD COLUMN IF NOT EXISTS otp_delivery_channel VARCHAR(40) NOT NULL DEFAULT 'SMS_WITH_EMAIL_FALLBACK';

ALTER TABLE sacco_stations
    DROP CONSTRAINT IF EXISTS ck_sacco_stations_otp_delivery_channel;

ALTER TABLE sacco_stations
    ADD CONSTRAINT ck_sacco_stations_otp_delivery_channel
        CHECK (otp_delivery_channel IN ('EMAIL', 'SMS', 'SMS_WITH_EMAIL_FALLBACK'));
