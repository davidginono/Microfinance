alter table loan_product_settings
    add column if not exists repayment_frequency varchar(32) not null default 'MONTHLY',
    add column if not exists affordability_check_required boolean not null default true,
    add column if not exists max_repayment_to_disposable_income_ratio numeric(6,4) not null default 0.4000,
    add column if not exists collateral_required boolean not null default false,
    add column if not exists min_collateral_coverage_ratio numeric(6,4) not null default 0.0000;
