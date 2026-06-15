ALTER TABLE email_otp_tokens
    DROP CONSTRAINT IF EXISTS email_otp_tokens_purpose_check;

ALTER TABLE email_otp_tokens
    ADD CONSTRAINT email_otp_tokens_purpose_check
        CHECK (purpose IN (
            'LOGIN',
            'STAFF_LOGIN',
            'STAFF_LOGIN_MFA',
            'REGISTRATION',
            'PASSWORD_RESET',
            'APPLICANT_SIGNATURE',
            'LOAN_APPLICATION_FORFEIT',
            'PAYMENT_DETAILS_CHANGE',
            'GUARANTOR_SIGNATURE',
            'BOARD_SIGNATURE',
            'CLAIM_ACCOUNT',
            'CLAIM_PHONE'
        ));
