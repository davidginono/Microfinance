alter table sacco_settings
    add column if not exists applicant_forfeited_wait_days integer;

alter table sacco_station_policies
    add column if not exists applicant_forfeited_wait_days integer;
