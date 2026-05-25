alter table sacco_settings
    add column if not exists applicant_max_defaulted_loans integer,
    add column if not exists applicant_max_active_loan_amount numeric(18,2),
    add column if not exists applicant_max_forfeited_loans integer,
    add column if not exists applicant_forfeited_lookback_days integer,
    add column if not exists guarantor_min_savings numeric(18,2),
    add column if not exists guarantor_max_active_loan_amount numeric(18,2),
    add column if not exists guarantor_max_guaranteed_loan_amount numeric(18,2),
    add column if not exists guarantor_max_defaulted_loans integer;

alter table loan_product_settings
    add column if not exists guarantor_commitment_required boolean,
    add column if not exists guarantor_commitment_stage varchar(32);
