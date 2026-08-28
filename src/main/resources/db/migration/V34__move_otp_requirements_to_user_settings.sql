ALTER TABLE user_settings
    ADD COLUMN login_otp_enabled boolean,
    ADD COLUMN approval_otp_enabled boolean;

INSERT INTO user_settings (
    member_id,
    language,
    notification_prefs,
    login_otp_enabled,
    approval_otp_enabled,
    created_at,
    updated_at
)
SELECT
    member.id,
    'en',
    '{}'::jsonb,
    CASE
        WHEN station.otp_requirement_mode IN ('LOGIN_MFA_ONLY', 'LOGIN_MFA_AND_APPROVAL') THEN true
        WHEN member.position = 'MINOR_ADMIN' AND member.station_id IS NULL THEN true
        ELSE false
    END,
    station.otp_requirement_mode IN ('APPROVAL_ONLY', 'LOGIN_MFA_AND_APPROVAL'),
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM members member
LEFT JOIN sacco_stations station
    ON station.sacco_id = member.sacco_id
    AND station.station_id = member.station_id
ON CONFLICT (member_id) DO UPDATE SET
    login_otp_enabled = EXCLUDED.login_otp_enabled,
    approval_otp_enabled = EXCLUDED.approval_otp_enabled,
    updated_at = CURRENT_TIMESTAMP;

UPDATE user_settings
SET login_otp_enabled = true
WHERE login_otp_enabled IS NULL;

UPDATE user_settings
SET approval_otp_enabled = false
WHERE approval_otp_enabled IS NULL;

ALTER TABLE user_settings
    ALTER COLUMN login_otp_enabled SET DEFAULT true,
    ALTER COLUMN login_otp_enabled SET NOT NULL,
    ALTER COLUMN approval_otp_enabled SET DEFAULT false,
    ALTER COLUMN approval_otp_enabled SET NOT NULL;
