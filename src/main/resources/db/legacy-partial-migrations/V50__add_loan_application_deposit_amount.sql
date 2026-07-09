ALTER TABLE loan_applications
    ADD COLUMN IF NOT EXISTS deposit_amount NUMERIC(18, 2);
