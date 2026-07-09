alter table loan_product_settings
    add column if not exists board_review_required boolean,
    add column if not exists board_priority integer;

alter table loan_product_board_reviewers
    add column if not exists review_stage varchar(32);

update loan_product_board_reviewers
set review_stage = 'CREDIT_COMMITTEE'
where review_stage is null;

alter table loan_product_board_reviewers
    alter column review_stage set not null;

alter table loan_product_board_reviewers
    drop constraint if exists uk_loan_product_board_reviewer;

alter table loan_product_board_reviewers
    add constraint uk_loan_product_stage_reviewer unique (loan_product_setting_id, review_stage, board_member_id);

create index if not exists idx_loan_product_board_reviewers_product_stage
    on loan_product_board_reviewers (loan_product_setting_id, review_stage);
