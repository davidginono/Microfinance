ALTER TABLE loan_product_settings
    ADD COLUMN IF NOT EXISTS guarantor_minimum_savings NUMERIC(18,2) NOT NULL DEFAULT 0;

UPDATE loan_product_settings product
SET guarantor_minimum_savings = COALESCE(settings.guarantor_min_savings, 0)
FROM sacco_settings settings
WHERE product.sacco_id = settings.sacco_id
  AND product.guarantor_minimum_savings = 0
  AND settings.guarantor_min_savings IS NOT NULL;

ALTER TABLE sacco_settings
    ADD COLUMN IF NOT EXISTS guarantor_with_active_loan_allowed BOOLEAN NOT NULL DEFAULT TRUE,
    DROP COLUMN IF EXISTS applicant_max_active_loan_amount,
    DROP COLUMN IF EXISTS guarantor_min_savings,
    DROP COLUMN IF EXISTS guarantor_max_active_loan_amount;

ALTER TABLE sacco_station_policies
    ADD COLUMN IF NOT EXISTS guarantor_with_active_loan_allowed BOOLEAN NOT NULL DEFAULT TRUE,
    DROP COLUMN IF EXISTS applicant_max_active_loan_amount,
    DROP COLUMN IF EXISTS guarantor_min_savings,
    DROP COLUMN IF EXISTS guarantor_max_active_loan_amount;
