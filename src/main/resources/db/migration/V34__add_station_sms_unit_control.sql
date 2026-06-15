create table if not exists platform_sms_settings (
    id varchar(64) primary key,
    low_percent integer not null,
    critical_percent integer not null,
    updated_by_member_id uuid,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint ck_platform_sms_low_percent check (low_percent between 1 and 99),
    constraint ck_platform_sms_critical_percent check (critical_percent between 1 and 98),
    constraint ck_platform_sms_threshold_order check (critical_percent < low_percent)
);

insert into platform_sms_settings (id, low_percent, critical_percent, created_at, updated_at)
values ('DEFAULT', 20, 10, current_timestamp, current_timestamp)
on conflict (id) do nothing;

create table if not exists station_sms_accounts (
    id uuid primary key,
    sacco_id varchar(255) not null,
    station_id varchar(255) not null,
    available_units bigint not null default 0,
    warning_baseline bigint not null default 0,
    status varchar(32) not null default 'DEPLETED',
    low_alert_sent boolean not null default false,
    critical_alert_sent boolean not null default false,
    depleted_alert_sent boolean not null default false,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    version bigint not null default 0,
    constraint uk_station_sms_account unique (sacco_id, station_id),
    constraint ck_station_sms_available_units check (available_units >= 0),
    constraint ck_station_sms_warning_baseline check (warning_baseline >= 0)
);

create index if not exists idx_station_sms_account_status on station_sms_accounts (status);
create index if not exists idx_station_sms_account_scope on station_sms_accounts (sacco_id, station_id);

insert into station_sms_accounts (
    id, sacco_id, station_id, available_units, warning_baseline, status, created_at, updated_at, version
)
select md5(s.sacco_id || ':' || s.station_id)::uuid,
       s.sacco_id,
       s.station_id,
       0,
       0,
       'DEPLETED',
       current_timestamp,
       current_timestamp,
       0
from sacco_stations s
on conflict (sacco_id, station_id) do nothing;

create table if not exists sms_usage_ledger (
    id uuid primary key,
    account_id uuid,
    sacco_id varchar(255),
    station_id varchar(255),
    notification_id uuid,
    event_type varchar(255),
    unit_change bigint not null,
    outcome varchar(32) not null,
    provider_reference varchar(500),
    actor_member_id uuid,
    note varchar(500),
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint fk_sms_usage_ledger_account foreign key (account_id) references station_sms_accounts(id)
);

create index if not exists idx_sms_usage_ledger_scope_created on sms_usage_ledger (sacco_id, station_id, created_at desc);
create index if not exists idx_sms_usage_ledger_account_created on sms_usage_ledger (account_id, created_at desc);
