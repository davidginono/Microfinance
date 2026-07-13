DROP INDEX IF EXISTS public.idx_loan_payment_transaction_history_order;

CREATE INDEX idx_loan_payment_transaction_history_order
    ON public.loan_payment_transactions (loan_application_id, receipt_date ASC, provider_order DESC);

DROP TABLE IF EXISTS public.loan_payment_installment_records;
