CREATE INDEX IF NOT EXISTS ix_loan_applications_top_up_source_status
    ON public.loan_applications (top_up_source_loan_id, status, id)
    WHERE top_up_source_loan_id IS NOT NULL;
