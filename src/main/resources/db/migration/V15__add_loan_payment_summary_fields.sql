ALTER TABLE loan_applications
    ADD COLUMN IF NOT EXISTS loan_payment_summary_json jsonb;

ALTER TABLE loan_applications
    ADD COLUMN IF NOT EXISTS loan_payment_summary_fetched_at timestamptz;
