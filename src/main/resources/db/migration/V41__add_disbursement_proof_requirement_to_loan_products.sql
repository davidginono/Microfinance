ALTER TABLE loan_product_settings
    ADD COLUMN IF NOT EXISTS disbursement_proof_required boolean NOT NULL DEFAULT true;
