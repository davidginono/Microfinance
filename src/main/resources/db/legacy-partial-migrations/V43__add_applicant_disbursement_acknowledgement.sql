ALTER TABLE loan_applications
    ADD COLUMN IF NOT EXISTS applicant_disbursement_acknowledged_at timestamptz;

CREATE INDEX IF NOT EXISTS ix_loan_applications_applicant_disbursement_ack
    ON loan_applications (applicant_member_id, status, applicant_disbursement_acknowledged_at, updated_at DESC);
