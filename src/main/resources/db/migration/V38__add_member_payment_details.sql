CREATE TABLE IF NOT EXISTS member_payment_details (
    member_id UUID PRIMARY KEY REFERENCES members(id) ON DELETE CASCADE,
    destination_type VARCHAR(32) NOT NULL,
    provider VARCHAR(120) NOT NULL,
    account_holder_name VARCHAR(160) NOT NULL,
    account_identifier VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_member_payment_details_destination_type
        CHECK (destination_type IN ('BANK_ACCOUNT', 'MOBILE_MONEY', 'OTHER'))
);

ALTER TABLE loan_applications
    ADD COLUMN IF NOT EXISTS payment_details_snapshot JSONB;
