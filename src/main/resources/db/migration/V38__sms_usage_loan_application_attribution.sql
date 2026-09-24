alter table sms_usage_ledger
    add column if not exists loan_application_id uuid,
    add column if not exists applicant_member_id uuid,
    add column if not exists recipient_member_id uuid;

create index if not exists idx_sms_usage_ledger_loan_created
    on sms_usage_ledger (loan_application_id, created_at desc)
    where loan_application_id is not null;

create index if not exists idx_sms_usage_ledger_loan_scope_created
    on sms_usage_ledger (sacco_id, station_id, loan_application_id, created_at desc)
    where loan_application_id is not null;
