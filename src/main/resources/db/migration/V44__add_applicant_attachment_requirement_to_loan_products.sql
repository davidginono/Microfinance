ALTER TABLE loan_product_settings
    ADD COLUMN IF NOT EXISTS applicant_attachment_required boolean NOT NULL DEFAULT false;
