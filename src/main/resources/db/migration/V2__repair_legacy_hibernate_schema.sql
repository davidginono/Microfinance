-- Repair legacy Hibernate-created schemas adopted by Flyway baseline-on-migrate.
-- This migration is intentionally schema/backfill-only and does not seed onboarding data.

ALTER TABLE public.loan_applications
    ADD COLUMN IF NOT EXISTS applicant_signature_text varchar(255),
    ADD COLUMN IF NOT EXISTS applicant_signature_verified_at timestamptz,
    ADD COLUMN IF NOT EXISTS paid_at timestamptz,
    ADD COLUMN IF NOT EXISTS paid_marked_by_manager_id uuid,
    ADD COLUMN IF NOT EXISTS loan_id varchar(20),
    ADD COLUMN IF NOT EXISTS application_number bigint,
    ADD COLUMN IF NOT EXISTS loan_payment_summary_fetched_at timestamptz,
    ADD COLUMN IF NOT EXISTS loan_payment_summary_json jsonb,
    ADD COLUMN IF NOT EXISTS station_id varchar(255),
    ADD COLUMN IF NOT EXISTS payment_details_snapshot jsonb,
    ADD COLUMN IF NOT EXISTS applicant_disbursement_acknowledged_at timestamptz,
    ADD COLUMN IF NOT EXISTS deposit_amount numeric(18, 2);

WITH numbered AS (
    SELECT
        id,
        row_number() OVER (
            PARTITION BY sacco_id
            ORDER BY created_at NULLS FIRST, id
        ) + 100000 AS generated_number
    FROM public.loan_applications
    WHERE application_number IS NULL
)
UPDATE public.loan_applications app
SET application_number = numbered.generated_number
FROM numbered
WHERE app.id = numbered.id;

UPDATE public.loan_applications
SET station_id = ''
WHERE station_id IS NULL;

ALTER TABLE public.loan_applications
    ALTER COLUMN application_number SET NOT NULL,
    ALTER COLUMN station_id SET NOT NULL;

CREATE TABLE IF NOT EXISTS public.sacco_loan_app_counter (
    sacco_id varchar(64) PRIMARY KEY,
    last_number bigint NOT NULL
);

INSERT INTO public.sacco_loan_app_counter (sacco_id, last_number)
SELECT sacco_id, max(application_number)
FROM public.loan_applications
GROUP BY sacco_id
ON CONFLICT (sacco_id) DO UPDATE
SET last_number = greatest(public.sacco_loan_app_counter.last_number, excluded.last_number);

CREATE UNIQUE INDEX IF NOT EXISTS ux_loan_applications_sacco_appnum
    ON public.loan_applications (sacco_id, application_number);

CREATE UNIQUE INDEX IF NOT EXISTS ux_loan_applications_sacco_loanid
    ON public.loan_applications (sacco_id, loan_id)
    WHERE loan_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS ix_loan_applications_sacco_station
    ON public.loan_applications (sacco_id, station_id);

CREATE INDEX IF NOT EXISTS ix_loan_applications_applicant_disbursement_ack
    ON public.loan_applications (applicant_member_id, status, applicant_disbursement_acknowledged_at, updated_at DESC);

CREATE TABLE IF NOT EXISTS public.member_payment_details (
    member_id uuid PRIMARY KEY REFERENCES public.members(id) ON DELETE CASCADE,
    destination_type varchar(32) NOT NULL,
    provider varchar(120) NOT NULL,
    account_holder_name varchar(160) NOT NULL,
    account_identifier varchar(120) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT ck_member_payment_details_destination_type
        CHECK (destination_type IN ('BANK_ACCOUNT', 'MOBILE_MONEY', 'OTHER'))
);

ALTER TABLE public.email_otp_tokens
    ADD COLUMN IF NOT EXISTS resend_count integer NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS public.stored_uploads (
    id uuid PRIMARY KEY,
    owner_type varchar(60) NOT NULL,
    owner_id varchar(255) NOT NULL,
    category varchar(80) NOT NULL,
    original_name varchar(500) NOT NULL,
    content_type varchar(255) NOT NULL,
    size_bytes bigint NOT NULL,
    sha256 varchar(64) NOT NULL,
    content bytea NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_stored_uploads_owner
    ON public.stored_uploads (owner_type, owner_id);

CREATE INDEX IF NOT EXISTS idx_stored_uploads_owner_category
    ON public.stored_uploads (owner_type, owner_id, category);

CREATE UNIQUE INDEX IF NOT EXISTS ux_stored_uploads_owner_category_id
    ON public.stored_uploads (owner_type, owner_id, category, id);

CREATE TABLE IF NOT EXISTS public.stored_upload_migrations (
    migration_key varchar(120) PRIMARY KEY,
    completed_at timestamptz NOT NULL,
    imported_count bigint NOT NULL,
    failed_count bigint NOT NULL
);

CREATE TABLE IF NOT EXISTS public.app_usage_events (
    id uuid PRIMARY KEY,
    event_type varchar(32) NOT NULL,
    member_id uuid NOT NULL,
    username varchar(120),
    display_name varchar(180),
    roles varchar(240),
    sacco_id varchar(80),
    station_id varchar(80),
    page_path varchar(240),
    device_type varchar(40),
    browser_family varchar(40),
    occurred_at timestamptz NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_app_usage_events_occurred_at
    ON public.app_usage_events (occurred_at);

CREATE INDEX IF NOT EXISTS idx_app_usage_events_scope_time
    ON public.app_usage_events (sacco_id, station_id, occurred_at);

CREATE INDEX IF NOT EXISTS idx_app_usage_events_type_time
    ON public.app_usage_events (event_type, occurred_at);

CREATE TABLE IF NOT EXISTS public.app_usage_page_metrics (
    id uuid PRIMARY KEY,
    bucket_start timestamptz NOT NULL,
    sacco_id varchar(80) NOT NULL,
    station_id varchar(80) NOT NULL,
    page_path varchar(240) NOT NULL,
    device_type varchar(40) NOT NULL,
    browser_family varchar(40) NOT NULL,
    hit_count bigint NOT NULL,
    last_recorded_at timestamptz NOT NULL,
    CONSTRAINT uk_app_usage_page_metrics_bucket_scope_page_device
        UNIQUE (bucket_start, sacco_id, station_id, page_path, device_type, browser_family)
);

CREATE INDEX IF NOT EXISTS idx_app_usage_page_metrics_bucket
    ON public.app_usage_page_metrics (bucket_start);

CREATE INDEX IF NOT EXISTS idx_app_usage_page_metrics_scope_bucket
    ON public.app_usage_page_metrics (sacco_id, station_id, bucket_start);

ALTER TABLE public.loan_product_settings
    ADD COLUMN IF NOT EXISTS disbursement_proof_required boolean NOT NULL DEFAULT true,
    ADD COLUMN IF NOT EXISTS applicant_attachment_required boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS application_fee numeric(18, 2),
    ADD COLUMN IF NOT EXISTS processing_fee_rate numeric(6, 4),
    ADD COLUMN IF NOT EXISTS board_review_required boolean,
    ADD COLUMN IF NOT EXISTS board_priority integer,
    ADD COLUMN IF NOT EXISTS chairperson_review_required boolean,
    ADD COLUMN IF NOT EXISTS chairperson_priority integer,
    ADD COLUMN IF NOT EXISTS savings_limit_check_required boolean;

ALTER TABLE public.station_sms_accounts
    ADD COLUMN IF NOT EXISTS alert_reserved_units bigint NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS depleted_alert_sms_sent_count bigint NOT NULL DEFAULT 0;

ALTER TABLE public.sms_usage_ledger
    ADD COLUMN IF NOT EXISTS event_count bigint NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS last_occurred_at timestamptz;

UPDATE public.sms_usage_ledger
SET last_occurred_at = coalesce(last_occurred_at, created_at, now())
WHERE last_occurred_at IS NULL;

ALTER TABLE public.sms_usage_ledger
    ALTER COLUMN last_occurred_at SET NOT NULL;

