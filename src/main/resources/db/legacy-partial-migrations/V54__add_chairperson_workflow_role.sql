ALTER TABLE loan_product_settings
    ADD COLUMN IF NOT EXISTS chairperson_review_required boolean,
    ADD COLUMN IF NOT EXISTS chairperson_priority integer,
    ADD COLUMN IF NOT EXISTS savings_limit_check_required boolean DEFAULT true;

UPDATE loan_product_settings
SET savings_limit_check_required = true
WHERE savings_limit_check_required IS NULL;

ALTER TABLE loan_applications
    DROP CONSTRAINT IF EXISTS loan_applications_status_check;

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
        'AWAITING_ACCOUNTANT',
        'ACCOUNTANT_REJECTED',
        'ACCOUNTANT_APPROVED',
        'READY_FOR_DISBURSEMENT',
        'FORFEITED',
        'FINAL_REJECTED',
        'FINAL_APPROVED',
        'DEFAULTED',
        'PAID'
    ));
