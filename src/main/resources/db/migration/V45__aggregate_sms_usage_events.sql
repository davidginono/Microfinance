alter table sms_usage_ledger
    add column if not exists event_count bigint not null default 1;

alter table sms_usage_ledger
    add column if not exists last_occurred_at timestamptz;

update sms_usage_ledger
set last_occurred_at = created_at
where last_occurred_at is null;

alter table sms_usage_ledger
    alter column last_occurred_at set not null;

alter table sms_usage_ledger
    drop constraint if exists ck_sms_usage_ledger_event_count_positive;

alter table sms_usage_ledger
    add constraint ck_sms_usage_ledger_event_count_positive
    check (event_count > 0);

create index if not exists idx_sms_usage_ledger_depleted_block_bucket
    on sms_usage_ledger (account_id, event_type, outcome, note, created_at desc)
    where outcome = 'BLOCKED';

alter table station_sms_accounts
    add column if not exists depleted_alert_sms_sent_count bigint not null default 0;

alter table station_sms_accounts
    drop constraint if exists ck_station_sms_depleted_alert_sms_sent_count;

alter table station_sms_accounts
    add constraint ck_station_sms_depleted_alert_sms_sent_count
    check (depleted_alert_sms_sent_count >= 0);
