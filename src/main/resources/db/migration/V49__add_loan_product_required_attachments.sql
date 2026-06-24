create table if not exists loan_product_required_attachments (
    id uuid primary key,
    loan_product_setting_id uuid not null references loan_product_settings(id) on delete cascade,
    attachment_name varchar(120) not null,
    max_size_mb numeric(8, 2),
    display_order integer not null default 1,
    active boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists idx_loan_product_required_attachments_product
    on loan_product_required_attachments (loan_product_setting_id, active, display_order);
