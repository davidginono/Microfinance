create table if not exists loan_product_board_reviewers (
    id uuid primary key,
    loan_product_setting_id uuid not null references loan_product_settings(id) on delete cascade,
    sacco_id varchar(255) not null,
    board_member_id uuid not null references members(id),
    created_at timestamp with time zone not null,
    constraint uk_loan_product_board_reviewer unique (loan_product_setting_id, board_member_id)
);

create index if not exists idx_loan_product_board_reviewers_product
    on loan_product_board_reviewers (loan_product_setting_id);

create index if not exists idx_loan_product_board_reviewers_member
    on loan_product_board_reviewers (board_member_id);
