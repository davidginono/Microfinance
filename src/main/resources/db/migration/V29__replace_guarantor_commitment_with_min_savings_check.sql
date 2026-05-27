ALTER TABLE loan_product_settings
    ADD COLUMN IF NOT EXISTS guarantor_min_savings_check_required BOOLEAN NOT NULL DEFAULT FALSE,
    DROP COLUMN IF EXISTS guarantor_commitment_required,
    DROP COLUMN IF EXISTS guarantor_commitment_stage;
