-- Change defaults only. Keep existing user choices and configured branch rules.
ALTER TABLE user_settings
    ALTER COLUMN login_otp_enabled SET DEFAULT false,
    ALTER COLUMN approval_otp_enabled SET DEFAULT false;

ALTER TABLE sacco_stations
    ALTER COLUMN user_otp_selection_policy SET DEFAULT 'NONE';
