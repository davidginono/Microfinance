-- Stores loan payment transactions fetched from the memberportal API.
-- Populated by LoanPaymentTransactionSyncScheduler on the 1st of each month
-- (for the previous month) and by manager-triggered on-demand syncs.

CREATE TABLE IF NOT EXISTS loan_payment_transactions (
    id                  UUID            PRIMARY KEY,
    loan_application_id UUID            NOT NULL REFERENCES loan_applications(id),
    sacco_id            VARCHAR(64)     NOT NULL,
    external_loan_id    VARCHAR(20)     NOT NULL,
    receipt_date        DATE            NOT NULL,
    principal_paid      NUMERIC(18, 2)  NOT NULL,
    interest_paid       NUMERIC(18, 2)  NOT NULL,
    total_paid          NUMERIC(18, 2)  NOT NULL,
    fetched_at          TIMESTAMPTZ     NOT NULL
);

-- Natural key for idempotent upserts: the API does not expose a transaction ID,
-- so we dedupe on the full payment tuple per loan.
CREATE UNIQUE INDEX IF NOT EXISTS ux_loan_payment_txn_natural_key
    ON loan_payment_transactions
        (loan_application_id, receipt_date, principal_paid, interest_paid, total_paid);

CREATE INDEX IF NOT EXISTS ix_loan_payment_txn_loan_date
    ON loan_payment_transactions (loan_application_id, receipt_date);
