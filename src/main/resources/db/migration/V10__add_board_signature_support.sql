ALTER TABLE board_reviews
ADD COLUMN IF NOT EXISTS board_signature_text VARCHAR(255);

ALTER TABLE board_reviews
ADD COLUMN IF NOT EXISTS board_signature_verified_at TIMESTAMPTZ;

ALTER TABLE email_otp_tokens
DROP CONSTRAINT IF EXISTS email_otp_tokens_purpose_check;

ALTER TABLE email_otp_tokens
ADD CONSTRAINT email_otp_tokens_purpose_check
CHECK (purpose IN (
    'LOGIN',
    'STAFF_LOGIN',
    'REGISTRATION',
    'APPLICANT_SIGNATURE',
    'GUARANTOR_SIGNATURE',
    'BOARD_SIGNATURE'
));
