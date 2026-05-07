ALTER TABLE loan_product_settings
    ADD COLUMN IF NOT EXISTS loan_officer_review_required BOOLEAN;

ALTER TABLE loan_product_settings
    ADD COLUMN IF NOT EXISTS workflow_start_stage VARCHAR(32);

ALTER TABLE loan_product_settings
    ADD COLUMN IF NOT EXISTS committee_priority INTEGER;

ALTER TABLE loan_product_settings
    ADD COLUMN IF NOT EXISTS accountant_review_required BOOLEAN;

ALTER TABLE loan_product_settings
    ADD COLUMN IF NOT EXISTS accountant_priority INTEGER;

UPDATE loan_product_settings
SET workflow_start_stage = COALESCE(workflow_start_stage, 'MANAGER'),
    committee_priority = COALESCE(committee_priority, 3),
    accountant_review_required = COALESCE(accountant_review_required, TRUE),
    accountant_priority = COALESCE(accountant_priority, 4);
