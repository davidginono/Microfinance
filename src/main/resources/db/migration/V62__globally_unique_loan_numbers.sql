-- Preserve issued IDs; stop deployment if historical IDs need an explicit reconciliation.
CREATE UNIQUE INDEX ux_loan_global_number ON loan_applications(loan_id) WHERE loan_id IS NOT NULL;
DROP INDEX ux_loan_legacy_number;
DROP INDEX ux_loan_period_number;

-- Sequence values are never reused, including after a failed posting transaction.
CREATE SEQUENCE loan_disbursement_number_seq AS bigint START WITH 1;
WITH existing AS (
    SELECT coalesce(max(loan_id::numeric) FILTER (WHERE loan_id ~ '^[0-9]+$'), 0)::bigint AS last_number
    FROM loan_applications
)
SELECT setval('loan_disbursement_number_seq', greatest(last_number, 1), last_number > 0) FROM existing;
