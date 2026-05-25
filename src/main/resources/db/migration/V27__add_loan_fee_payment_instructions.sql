ALTER TABLE sacco_settings
    ADD COLUMN IF NOT EXISTS loan_fee_payment_method VARCHAR(120),
    ADD COLUMN IF NOT EXISTS loan_fee_payment_account VARCHAR(120),
    ADD COLUMN IF NOT EXISTS loan_fee_payment_payee VARCHAR(160),
    ADD COLUMN IF NOT EXISTS loan_fee_payment_instructions VARCHAR(500);
