ALTER TABLE public.loan_payment_transactions
    ADD COLUMN IF NOT EXISTS outstanding_balance numeric(18,2),
    ADD COLUMN IF NOT EXISTS outstanding_principal numeric(18,2),
    ADD COLUMN IF NOT EXISTS outstanding_interest numeric(18,2),
    ADD COLUMN IF NOT EXISTS provider_order integer,
    ADD COLUMN IF NOT EXISTS duplicate_occurrence integer;

WITH ranked AS (
    SELECT id,
           row_number() OVER (
               PARTITION BY loan_application_id, receipt_date, principal_paid, interest_paid, total_paid
               ORDER BY fetched_at DESC, id
           ) - 1 AS duplicate_occurrence,
           row_number() OVER (
               PARTITION BY loan_application_id
               ORDER BY receipt_date DESC, fetched_at DESC, id
           ) - 1 AS provider_order
    FROM public.loan_payment_transactions
)
UPDATE public.loan_payment_transactions transaction
SET duplicate_occurrence = ranked.duplicate_occurrence,
    provider_order = ranked.provider_order
FROM ranked
WHERE transaction.id = ranked.id
  AND (transaction.duplicate_occurrence IS NULL OR transaction.provider_order IS NULL);

ALTER TABLE public.loan_payment_transactions
    ALTER COLUMN provider_order SET DEFAULT 0,
    ALTER COLUMN provider_order SET NOT NULL,
    ALTER COLUMN duplicate_occurrence SET DEFAULT 0,
    ALTER COLUMN duplicate_occurrence SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_loan_payment_transaction_occurrence
    ON public.loan_payment_transactions (
        loan_application_id,
        receipt_date,
        principal_paid,
        interest_paid,
        total_paid,
        duplicate_occurrence
    );

CREATE INDEX IF NOT EXISTS idx_loan_payment_transaction_history_order
    ON public.loan_payment_transactions (loan_application_id, receipt_date, provider_order);
