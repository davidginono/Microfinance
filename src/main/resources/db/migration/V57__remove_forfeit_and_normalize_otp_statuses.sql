ALTER TABLE sacco_stations
    ADD COLUMN IF NOT EXISTS otp_requirement_mode VARCHAR(40) NOT NULL DEFAULT 'LOGIN_MFA_ONLY';

UPDATE sacco_stations
SET otp_requirement_mode = 'LOGIN_MFA_ONLY'
WHERE otp_requirement_mode IS NULL
   OR otp_requirement_mode NOT IN ('LOGIN_MFA_ONLY', 'APPROVAL_ONLY', 'LOGIN_MFA_AND_APPROVAL');

ALTER TABLE sacco_stations
    ALTER COLUMN otp_requirement_mode SET DEFAULT 'LOGIN_MFA_ONLY',
    ALTER COLUMN otp_requirement_mode SET NOT NULL;

ALTER TABLE sacco_stations
    DROP CONSTRAINT IF EXISTS ck_sacco_stations_otp_requirement_mode;

ALTER TABLE sacco_stations
    ADD CONSTRAINT ck_sacco_stations_otp_requirement_mode
        CHECK (otp_requirement_mode IN ('LOGIN_MFA_ONLY', 'APPROVAL_ONLY', 'LOGIN_MFA_AND_APPROVAL'));

ALTER TABLE loan_applications
    DROP CONSTRAINT IF EXISTS loan_applications_status_check;

UPDATE loan_applications
SET status = 'DISBURSED'
WHERE status = 'FINAL_APPROVED';

UPDATE loan_applications
SET status = 'REJECTED'
WHERE status = 'FINAL_REJECTED';

UPDATE loan_applications
SET status = CASE
    WHEN EXISTS (
        SELECT 1 FROM manager_reviews mr
        WHERE mr.loan_application_id = loan_applications.id
    ) THEN 'MANAGER_REJECTED'
    WHEN EXISTS (
        SELECT 1 FROM board_reviews br
        WHERE br.loan_application_id = loan_applications.id
          AND br.review_stage = 'CREDIT_COMMITTEE'
    ) THEN 'CREDIT_COMMITTEE_REJECTED'
    WHEN EXISTS (
        SELECT 1 FROM board_reviews br
        WHERE br.loan_application_id = loan_applications.id
          AND br.review_stage = 'BOARD'
    ) THEN 'BOARD_REJECTED'
    ELSE 'MANAGER_REJECTED'
END
WHERE status = 'FORFEITED';

ALTER TABLE loan_applications
    ADD CONSTRAINT loan_applications_status_check
    CHECK (status IN (
        'DRAFT',
        'SUBMITTED',
        'AWAITING_GUARANTORS',
        'ALL_GUARANTORS_APPROVED',
        'READY_FOR_MANAGER',
        'MANAGER_REJECTED',
        'MANAGER_ACCEPTED',
        'AWAITING_LOAN_OFFICER',
        'LOAN_OFFICER_REJECTED',
        'LOAN_OFFICER_APPROVED',
        'AWAITING_CHAIRPERSON',
        'CHAIRPERSON_REJECTED',
        'CHAIRPERSON_APPROVED',
        'AWAITING_BOARD',
        'AWAITING_CREDIT_COMMITTEE',
        'BOARD_REJECTED',
        'BOARD_APPROVED',
        'CREDIT_COMMITTEE_REJECTED',
        'CREDIT_COMMITTEE_APPROVED',
        'AWAITING_ACCOUNTANT',
        'ACCOUNTANT_REJECTED',
        'ACCOUNTANT_APPROVED',
        'READY_FOR_DISBURSEMENT',
        'REJECTED',
        'DISBURSED',
        'DEFAULTED',
        'PAID'
    ));

ALTER TABLE sacco_settings
    DROP COLUMN IF EXISTS applicant_max_forfeited_loans,
    DROP COLUMN IF EXISTS applicant_forfeited_lookback_days,
    DROP COLUMN IF EXISTS applicant_forfeited_wait_days;

ALTER TABLE sacco_station_policies
    DROP COLUMN IF EXISTS applicant_max_forfeited_loans,
    DROP COLUMN IF EXISTS applicant_forfeited_lookback_days,
    DROP COLUMN IF EXISTS applicant_forfeited_wait_days;
