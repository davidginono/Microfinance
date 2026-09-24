ALTER TABLE sacco_stations
    DROP CONSTRAINT chk_sacco_stations_user_otp_selection_policy;

ALTER TABLE sacco_stations
    ADD CONSTRAINT chk_sacco_stations_user_otp_selection_policy
    CHECK (user_otp_selection_policy IN ('NONE', 'AT_LEAST_ONE', 'BOTH'));
