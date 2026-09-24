create index if not exists idx_sms_usage_ledger_loan_report_filters
    on sms_usage_ledger (created_at desc, applicant_member_id, loan_application_id, sacco_id, station_id)
    where loan_application_id is not null
      and unit_change < 0
      and outcome in ('ACCEPTED', 'ACCEPTANCE_UNKNOWN');
