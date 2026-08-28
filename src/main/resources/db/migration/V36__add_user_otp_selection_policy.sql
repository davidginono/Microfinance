ALTER TABLE sacco_stations
    ADD COLUMN user_otp_selection_policy varchar(32) NOT NULL DEFAULT 'AT_LEAST_ONE';

ALTER TABLE sacco_stations
    ADD CONSTRAINT chk_sacco_stations_user_otp_selection_policy
    CHECK (user_otp_selection_policy IN ('AT_LEAST_ONE', 'BOTH'));
