-- New statement workspace; retired evidence in accounting_archive remains read-only.
create table statement_account_mapping (
 account_id uuid primary key references gl_account(id), sacco_id varchar(255) not null,
 line_name varchar(160) not null, section varchar(32) not null,
 cash_section varchar(20) not null, equity_component varchar(100) not null default '',
 note_no integer not null default 0 check(note_no between 0 and 999), note_text varchar(2000) not null default '',
 revision uuid not null, updated_by uuid not null references members(id), updated_at timestamptz not null,
 check(section in ('CURRENT_ASSETS','NON_CURRENT_ASSETS','CURRENT_LIABILITIES','NON_CURRENT_LIABILITIES','EQUITY','INCOME','EXPENSE')),
 check(cash_section in ('OPERATING','INVESTING','FINANCING','UNCLASSIFIED'))
);
create index statement_mapping_institution on statement_account_mapping(sacco_id,account_id);
create table statement_run (
 id uuid primary key, sacco_id varchar(255) not null, station_id varchar(255) not null,
 institution_wide boolean not null, requested_by uuid not null references members(id), request_key uuid not null,
 date_from date not null, date_through date not null, generated_at timestamptz not null,
 saved_at timestamptz, payload text check(payload is null or jsonb_typeof(payload::jsonb)='object'), checksum varchar(64),
 unique(sacco_id,station_id,requested_by,request_key), check(date_through>=date_from)
);
create index statement_run_register on statement_run(sacco_id,station_id,generated_at desc,id);
create table statement_run_journal (
 run_id uuid not null references statement_run(id), journal_id uuid not null references gl_journal(id),
 primary key(run_id,journal_id)
);
create table statement_run_artifact (
 run_id uuid not null references statement_run(id), kind varchar(20) not null, format varchar(8) not null,
 language varchar(2) not null, bytes bytea not null, checksum varchar(64) not null,
 primary key(run_id,kind,format,language), check(format in ('PDF','XLSX'))
);
create function protect_statement_snapshot() returns trigger language plpgsql as $$
begin
 if TG_OP='DELETE' then raise exception 'Statement evidence is retained'; end if;
 if old.payload is not null and (new.payload is distinct from old.payload or new.checksum is distinct from old.checksum
  or new.id<>old.id or new.sacco_id<>old.sacco_id or new.station_id<>old.station_id
  or new.institution_wide<>old.institution_wide or new.requested_by<>old.requested_by
  or new.request_key<>old.request_key or new.date_from<>old.date_from or new.date_through<>old.date_through
  or new.generated_at<>old.generated_at or (old.saved_at is not null and new.saved_at is distinct from old.saved_at))
 then raise exception 'Statement snapshot is immutable'; end if;
 return new;
end $$;
create trigger statement_snapshot_immutable before update or delete on statement_run for each row execute function protect_statement_snapshot();
create function protect_statement_children() returns trigger language plpgsql as $$
begin raise exception 'Statement evidence is immutable'; end $$;
create trigger statement_membership_immutable before update or delete on statement_run_journal for each statement execute function protect_statement_children();
create trigger statement_artifact_immutable before update or delete on statement_run_artifact for each statement execute function protect_statement_children();
create function validate_statement_membership() returns trigger language plpgsql as $$
begin
 if not exists(select 1 from statement_run r join gl_journal j on j.id=new.journal_id
   where r.id=new.run_id and r.payload is null and j.sacco_id=r.sacco_id
   and (r.institution_wide or j.station_id=r.station_id) and j.state='POSTED'
   and j.effective_date<=r.date_through and j.posted_at<=r.generated_at)
 then raise exception 'Invalid or frozen statement journal membership'; end if;
 return new;
end $$;
create trigger statement_membership_insert before insert on statement_run_journal for each row execute function validate_statement_membership();
create trigger statement_run_no_truncate before truncate on statement_run for each statement execute function protect_statement_children();
create trigger statement_membership_no_truncate before truncate on statement_run_journal for each statement execute function protect_statement_children();
create trigger statement_artifact_no_truncate before truncate on statement_run_artifact for each statement execute function protect_statement_children();
