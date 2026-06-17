ALTER TABLE email_otp_tokens
    ADD COLUMN IF NOT EXISTS resend_count integer NOT NULL DEFAULT 0;
