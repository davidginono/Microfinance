create table if not exists sacco_station_policies (
    id uuid primary key,
    sacco_id varchar(255) not null,
    station_id varchar(255) not null,
    applicant_max_defaulted_loans integer,
    applicant_max_active_loan_amount numeric(18,2),
    applicant_max_forfeited_loans integer,
    applicant_forfeited_lookback_days integer,
    guarantor_min_savings numeric(18,2),
    guarantor_max_active_loan_amount numeric(18,2),
    guarantor_max_guaranteed_loan_amount numeric(18,2),
    guarantor_max_defaulted_loans integer,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint uk_sacco_station_policy unique (sacco_id, station_id)
);
