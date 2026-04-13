UPDATE email_otp_tokens
SET purpose = 'STAFF_LOGIN'
WHERE purpose = 'NON_MEMBER_LOGIN';

ALTER TABLE email_otp_tokens
DROP CONSTRAINT IF EXISTS email_otp_tokens_purpose_check;

ALTER TABLE email_otp_tokens
ADD CONSTRAINT email_otp_tokens_purpose_check
CHECK (purpose IN (
    'LOGIN',
    'STAFF_LOGIN',
    'REGISTRATION',
    'APPLICANT_SIGNATURE',
    'GUARANTOR_SIGNATURE'
));
