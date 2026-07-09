ALTER TABLE email_otp_tokens
    DROP CONSTRAINT IF EXISTS email_otp_tokens_purpose_check;

DELETE FROM email_otp_tokens
WHERE purpose NOT IN (
    'LOGIN',
    'LOGIN_MFA',
    'STAFF_LOGIN',
    'STAFF_LOGIN_MFA',
    'REGISTRATION',
    'PASSWORD_RESET',
    'APPLICANT_SIGNATURE',
    'GUARANTOR_APPLICANT_CONFIRMATION',
    'PAYMENT_DETAILS_CHANGE',
    'GUARANTOR_SIGNATURE',
    'BOARD_SIGNATURE',
    'CLAIM_ACCOUNT',
    'CLAIM_PHONE'
);

ALTER TABLE email_otp_tokens
    ADD CONSTRAINT email_otp_tokens_purpose_check
    CHECK (purpose IN (
        'LOGIN',
        'LOGIN_MFA',
        'STAFF_LOGIN',
        'STAFF_LOGIN_MFA',
        'REGISTRATION',
        'PASSWORD_RESET',
        'APPLICANT_SIGNATURE',
        'GUARANTOR_APPLICANT_CONFIRMATION',
        'PAYMENT_DETAILS_CHANGE',
        'GUARANTOR_SIGNATURE',
        'BOARD_SIGNATURE',
        'CLAIM_ACCOUNT',
        'CLAIM_PHONE'
    ));
