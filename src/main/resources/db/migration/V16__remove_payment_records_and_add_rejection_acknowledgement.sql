ALTER TABLE public.loan_applications
    ADD COLUMN IF NOT EXISTS applicant_rejection_acknowledged_at timestamptz;

ALTER TABLE public.manager_reviews
    ADD COLUMN IF NOT EXISTS manager_signature_text character varying(255),
    ADD COLUMN IF NOT EXISTS manager_signature_verified_at timestamptz;

DROP INDEX IF EXISTS public.idx_loan_payment_transaction_history_order;
DROP INDEX IF EXISTS public.uk_loan_payment_transaction_occurrence;
DROP TABLE IF EXISTS public.loan_payment_transactions;

ALTER TABLE public.loan_applications
    DROP COLUMN IF EXISTS loan_payment_summary_fetched_at,
    DROP COLUMN IF EXISTS loan_payment_summary_json;

CREATE INDEX IF NOT EXISTS ix_loan_applications_applicant_rejection_ack
    ON public.loan_applications (applicant_member_id, status, applicant_rejection_acknowledged_at, updated_at DESC);
