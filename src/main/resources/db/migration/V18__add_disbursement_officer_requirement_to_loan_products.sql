ALTER TABLE loan_product_settings
    ADD COLUMN IF NOT EXISTS disbursement_officer_required BOOLEAN;

UPDATE loan_product_settings
SET disbursement_officer_required = COALESCE(disbursement_officer_required, TRUE);
