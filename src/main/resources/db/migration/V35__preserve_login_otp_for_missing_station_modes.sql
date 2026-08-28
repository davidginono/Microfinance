UPDATE user_settings settings
SET login_otp_enabled = true,
    updated_at = CURRENT_TIMESTAMP
FROM members member
LEFT JOIN sacco_stations station
    ON station.sacco_id = member.sacco_id
    AND station.station_id = member.station_id
WHERE settings.member_id = member.id
  AND member.station_id IS NOT NULL
  AND station.otp_requirement_mode IS NULL;
