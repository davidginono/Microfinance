/*
 * SACCO onboarding/reset maintenance script.
 *
 * Purpose:
 *   Clear operational/application data so the platform can onboard SACCOs,
 *   members, and staff from a clean state while preserving Super Admin access.
 *
 * Preserves:
 *   - members with position = 'ADMIN'
 *   - members with member_staff_roles.role_name = 'ADMIN'
 *   - preserved Super Admin user_settings, ADMIN staff role, and current access claims
 *   - platform_sms_settings
 *   - platform_branding_settings
 *   - platform_support_contact_settings
 *   - platform_session_settings
 *   - platform_email_settings
 *   - platform_sms_gateway_settings
 *   - stored_upload_migrations
 *
 * Clears MINOR_ADMIN/SACCOS Admin accounts and roles.
 *
 * Run only during maintenance on the intended database.
 */

BEGIN;

ALTER TABLE IF EXISTS public.audit_log
    ADD COLUMN IF NOT EXISTS event_status character varying(20),
    ADD COLUMN IF NOT EXISTS sacco_id character varying(255),
    ADD COLUMN IF NOT EXISTS station_id character varying(255),
    ADD COLUMN IF NOT EXISTS action_description character varying(255),
    ADD COLUMN IF NOT EXISTS reference_type character varying(80),
    ADD COLUMN IF NOT EXISTS reference_value character varying(255),
    ADD COLUMN IF NOT EXISTS request_metadata jsonb;

ALTER TABLE IF EXISTS public.members
    ADD COLUMN IF NOT EXISTS staff_no character varying(255),
    ADD COLUMN IF NOT EXISTS staff_access_status character varying(64) NOT NULL DEFAULT 'NONE',
    ADD COLUMN IF NOT EXISTS staff_access_assigned_at timestamp(6) with time zone,
    ADD COLUMN IF NOT EXISTS staff_access_activated_at timestamp(6) with time zone;

CREATE SEQUENCE IF NOT EXISTS public.staff_number_seq
    AS bigint
    MINVALUE 10000
    MAXVALUE 99999
    START WITH 10000
    INCREMENT BY 1
    CYCLE
    CACHE 1;

ALTER SEQUENCE public.staff_number_seq
    MINVALUE 10000
    MAXVALUE 99999
    CYCLE
    CACHE 1;

CREATE INDEX IF NOT EXISTS idx_audit_log_status_created_at
    ON public.audit_log (event_status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_audit_log_scope_created_at
    ON public.audit_log (sacco_id, station_id, created_at DESC);

CREATE TABLE IF NOT EXISTS public.platform_branding_settings (
    id varchar(40) NOT NULL,
    logo_min_width_px integer NOT NULL,
    logo_min_height_px integer NOT NULL,
    logo_max_width_px integer NOT NULL,
    logo_max_height_px integer NOT NULL,
    logo_max_file_size_kb integer NOT NULL,
    updated_by_member_id uuid,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT platform_branding_settings_pkey PRIMARY KEY (id),
    CONSTRAINT ck_platform_branding_logo_min_width CHECK (logo_min_width_px BETWEEN 32 AND 4096),
    CONSTRAINT ck_platform_branding_logo_min_height CHECK (logo_min_height_px BETWEEN 32 AND 4096),
    CONSTRAINT ck_platform_branding_logo_max_width CHECK (logo_max_width_px BETWEEN 32 AND 4096),
    CONSTRAINT ck_platform_branding_logo_max_height CHECK (logo_max_height_px BETWEEN 32 AND 4096),
    CONSTRAINT ck_platform_branding_logo_file_size CHECK (logo_max_file_size_kb BETWEEN 64 AND 5120),
    CONSTRAINT ck_platform_branding_logo_width_order CHECK (logo_min_width_px <= logo_max_width_px),
    CONSTRAINT ck_platform_branding_logo_height_order CHECK (logo_min_height_px <= logo_max_height_px)
);

CREATE TABLE IF NOT EXISTS public.platform_support_contact_settings (
    id varchar(40) NOT NULL,
    display_name varchar(120) NOT NULL,
    display_role varchar(120) NOT NULL,
    phone varchar(40) NOT NULL,
    email varchar(160) NOT NULL,
    office_hours varchar(120) NOT NULL,
    support_note varchar(280) NOT NULL,
    updated_by_member_id uuid,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT platform_support_contact_settings_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS public.platform_session_settings (
    id varchar(40) NOT NULL,
    timeout_minutes integer NOT NULL,
    updated_by_member_id uuid,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT platform_session_settings_pkey PRIMARY KEY (id),
    CONSTRAINT ck_platform_session_timeout_minutes CHECK (timeout_minutes BETWEEN 2 AND 480)
);

CREATE TABLE IF NOT EXISTS public.platform_email_settings (
    id varchar(40) NOT NULL,
    enabled boolean NOT NULL DEFAULT false,
    host varchar(255) NOT NULL DEFAULT '',
    port integer NOT NULL DEFAULT 465,
    username varchar(160) NOT NULL DEFAULT '',
    password_encrypted varchar(512) NOT NULL DEFAULT '',
    from_address varchar(160) NOT NULL DEFAULT '',
    override_recipient varchar(160) NOT NULL DEFAULT '',
    ssl_enabled boolean NOT NULL DEFAULT true,
    starttls_enabled boolean NOT NULL DEFAULT false,
    connection_timeout_ms integer NOT NULL DEFAULT 10000,
    read_timeout_ms integer NOT NULL DEFAULT 10000,
    write_timeout_ms integer NOT NULL DEFAULT 10000,
    updated_by_member_id uuid,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT platform_email_settings_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS public.platform_sms_gateway_settings (
    id varchar(40) NOT NULL,
    enabled boolean NOT NULL DEFAULT false,
    base_url varchar(255) NOT NULL DEFAULT '',
    send_path varchar(255) NOT NULL DEFAULT '',
    client_id varchar(120) NOT NULL DEFAULT '',
    api_key_encrypted varchar(512) NOT NULL DEFAULT '',
    sender_id varchar(40) NOT NULL DEFAULT '',
    connect_timeout_seconds integer NOT NULL DEFAULT 3,
    read_timeout_seconds integer NOT NULL DEFAULT 8,
    updated_by_member_id uuid,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT platform_sms_gateway_settings_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS public.member_access_claims (
    member_id uuid NOT NULL,
    claim_name varchar(120) NOT NULL,
    CONSTRAINT member_access_claims_pkey PRIMARY KEY (member_id, claim_name),
    CONSTRAINT member_access_claims_member_fk FOREIGN KEY (member_id) REFERENCES public.members(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_member_access_claims_claim_name
    ON public.member_access_claims (claim_name);

ALTER TABLE IF EXISTS public.sacco_settings
    ADD COLUMN IF NOT EXISTS portfolio_at_risk_days integer NOT NULL DEFAULT 30;

ALTER TABLE IF EXISTS public.sacco_station_policies
    ADD COLUMN IF NOT EXISTS portfolio_at_risk_days integer;

CREATE TABLE IF NOT EXISTS public.external_guarantor_registry (
    id uuid NOT NULL,
    sacco_id character varying(255) NOT NULL,
    station_id character varying(255),
    external_station_id character varying(80) NOT NULL,
    external_member_no character varying(80) NOT NULL,
    full_name character varying(255),
    email character varying(255),
    phone character varying(40),
    latest_financial_snapshot jsonb,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    last_approved_at timestamp with time zone,
    version integer NOT NULL DEFAULT 0,
    CONSTRAINT external_guarantor_registry_pkey PRIMARY KEY (id)
);

ALTER TABLE IF EXISTS public.guarantor_requests
    DROP CONSTRAINT IF EXISTS uk695pu1ejdrmydcxnx9vjjjkfv;

ALTER TABLE IF EXISTS public.guarantor_requests
    ALTER COLUMN guarantor_member_id DROP NOT NULL;

ALTER TABLE IF EXISTS public.guarantor_requests
    ADD COLUMN IF NOT EXISTS guarantor_source character varying(20) NOT NULL DEFAULT 'LMS',
    ADD COLUMN IF NOT EXISTS external_member_no character varying(80),
    ADD COLUMN IF NOT EXISTS external_station_id character varying(80),
    ADD COLUMN IF NOT EXISTS external_full_name character varying(255),
    ADD COLUMN IF NOT EXISTS external_email character varying(255),
    ADD COLUMN IF NOT EXISTS external_phone character varying(40),
    ADD COLUMN IF NOT EXISTS external_financial_snapshot jsonb,
    ADD COLUMN IF NOT EXISTS external_guarantor_registry_id uuid;

ALTER TABLE IF EXISTS public.stored_uploads
    ADD COLUMN IF NOT EXISTS storage_backend varchar(40) NOT NULL DEFAULT 'DATABASE',
    ADD COLUMN IF NOT EXISTS storage_key varchar(1024);

ALTER TABLE IF EXISTS public.stored_uploads
    ALTER COLUMN content DROP NOT NULL;

CREATE INDEX IF NOT EXISTS idx_stored_uploads_storage_backend
    ON public.stored_uploads (storage_backend)
    WHERE content IS NOT NULL;

CREATE TEMP TABLE _reset_keep_members ON COMMIT DROP AS
SELECT DISTINCT
    m.id,
    m.sacco_id
FROM public.members m
WHERE m.position = 'ADMIN'
   OR EXISTS (
       SELECT 1
       FROM public.member_staff_roles msr
       WHERE msr.member_id = m.id
         AND msr.role_name = 'ADMIN'
   );

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM _reset_keep_members) THEN
        RAISE EXCEPTION 'Reset aborted: no Super Admin account was found.';
    END IF;
END $$;

DELETE FROM public.app_usage_events;
DELETE FROM public.app_usage_page_metrics;
DELETE FROM public.audit_log;
DELETE FROM public.outbox_events;
DELETE FROM public.admin_incidents;
DELETE FROM public.notifications;
DELETE FROM public.email_otp_tokens;
DELETE FROM public.minor_admin_invitations;

DELETE FROM public.reversal_requests;
DELETE FROM public.board_reviews;
DELETE FROM public.manager_reviews;
DELETE FROM public.guarantor_requests;
DELETE FROM public.external_guarantor_registry;
DELETE FROM public.loan_applications;
DELETE FROM public.sacco_loan_app_counter;

ALTER TABLE IF EXISTS public.outbox_events
    DROP CONSTRAINT IF EXISTS outbox_events_status_check;

ALTER TABLE IF EXISTS public.outbox_events
    ADD CONSTRAINT outbox_events_status_check
    CHECK ((status)::text IN ('NEW', 'PROCESSING', 'PUBLISHED', 'FAILED'));

ALTER TABLE IF EXISTS public.loan_applications
    DROP CONSTRAINT IF EXISTS loan_applications_status_check;

ALTER TABLE IF EXISTS public.loan_applications
    ADD CONSTRAINT loan_applications_status_check
    CHECK (((status)::text = ANY ((ARRAY[
        'DRAFT'::character varying,
        'SUBMITTED'::character varying,
        'AWAITING_GUARANTORS'::character varying,
        'ALL_GUARANTORS_APPROVED'::character varying,
        'READY_FOR_MANAGER'::character varying,
        'MANAGER_REJECTED'::character varying,
        'MANAGER_ACCEPTED'::character varying,
        'AWAITING_LOAN_OFFICER'::character varying,
        'LOAN_OFFICER_REJECTED'::character varying,
        'LOAN_OFFICER_APPROVED'::character varying,
        'AWAITING_CHAIRPERSON'::character varying,
        'CHAIRPERSON_REJECTED'::character varying,
        'CHAIRPERSON_APPROVED'::character varying,
        'AWAITING_BOARD'::character varying,
        'AWAITING_CREDIT_COMMITTEE'::character varying,
        'BOARD_REJECTED'::character varying,
        'BOARD_APPROVED'::character varying,
        'CREDIT_COMMITTEE_REJECTED'::character varying,
        'CREDIT_COMMITTEE_APPROVED'::character varying,
        'AWAITING_ACCOUNTANT'::character varying,
        'ACCOUNTANT_REJECTED'::character varying,
        'ACCOUNTANT_APPROVED'::character varying,
        'READY_FOR_DISBURSEMENT'::character varying,
        'REJECTED'::character varying,
        'DISBURSED'::character varying,
        'PAR'::character varying,
        'DEFAULTED'::character varying,
        'PAID'::character varying
    ])::text[])));

CREATE UNIQUE INDEX IF NOT EXISTS uk_external_guarantor_registry_identity
    ON public.external_guarantor_registry (sacco_id, lower(external_station_id), lower(external_member_no));

CREATE INDEX IF NOT EXISTS idx_external_guarantor_registry_scope
    ON public.external_guarantor_registry (sacco_id, station_id, lower(external_station_id), lower(external_member_no));

CREATE UNIQUE INDEX IF NOT EXISTS uk_guarantor_requests_loan_local_member
    ON public.guarantor_requests (loan_application_id, guarantor_member_id)
    WHERE guarantor_member_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_guarantor_requests_loan_external_member
    ON public.guarantor_requests (loan_application_id, lower(external_station_id), lower(external_member_no))
    WHERE guarantor_member_id IS NULL
      AND external_station_id IS NOT NULL
      AND external_member_no IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_guarantor_requests_external_registry
    ON public.guarantor_requests (external_guarantor_registry_id)
    WHERE external_guarantor_registry_id IS NOT NULL;

DELETE FROM public.stored_uploads;

ALTER TABLE IF EXISTS public.stored_uploads
    DROP CONSTRAINT IF EXISTS ck_stored_uploads_no_sacco_logo;

ALTER TABLE IF EXISTS public.stored_uploads
    ADD CONSTRAINT ck_stored_uploads_no_sacco_logo
    CHECK (NOT (owner_type = 'SACCO' AND category = 'SACCO_LOGO'));

ALTER TABLE IF EXISTS public.stored_uploads
    DROP CONSTRAINT IF EXISTS ck_stored_uploads_content_location;

ALTER TABLE IF EXISTS public.stored_uploads
    ADD CONSTRAINT ck_stored_uploads_content_location
    CHECK (
        (storage_backend = 'DATABASE' AND content IS NOT NULL)
        OR
        (storage_backend <> 'DATABASE' AND storage_key IS NOT NULL AND storage_key <> '')
    );

DELETE FROM public.loan_product_required_attachments;
DELETE FROM public.loan_product_board_reviewers;
DELETE FROM public.loan_product_settings;

DELETE FROM public.accounts_savings;

DELETE FROM public.member_payment_details
WHERE member_id NOT IN (SELECT id FROM _reset_keep_members);

DELETE FROM public.user_settings
WHERE member_id NOT IN (SELECT id FROM _reset_keep_members);

DELETE FROM public.member_access_claims
WHERE claim_name IN (
    'LOAN_OFFICER_QUEUE_UPDATE',
    'MANAGER_QUEUE_ASSIGN',
    'MANAGER_QUEUE_UPDATE',
    'ACCOUNTANT_QUEUE_ASSIGN',
    'ACCOUNTANT_QUEUE_UPDATE',
    'BOARD_QUEUE_ASSIGN',
    'BOARD_QUEUE_UPDATE',
    'CHAIRPERSON_QUEUE_ASSIGN',
    'CHAIRPERSON_QUEUE_UPDATE',
    'CREDIT_COMMITTEE_QUEUE_ASSIGN',
    'CREDIT_COMMITTEE_QUEUE_UPDATE',
    'DISBURSEMENT_QUEUE_UPDATE'
);

DELETE FROM public.member_access_claims
WHERE member_id NOT IN (SELECT id FROM _reset_keep_members);

DELETE FROM public.member_staff_roles
WHERE member_id NOT IN (SELECT id FROM _reset_keep_members);

DELETE FROM public.member_staff_roles
WHERE member_id IN (SELECT id FROM _reset_keep_members)
  AND role_name <> 'ADMIN';

DELETE FROM public.members
WHERE id NOT IN (SELECT id FROM _reset_keep_members);

UPDATE public.members
SET sacco_id = NULL,
    station_id = NULL,
    "position" = 'ADMIN',
    status = 'ACTIVE',
    is_member = false,
    staff_no = COALESCE(NULLIF(staff_no, ''), member_no),
    staff_access_status = 'ACTIVE',
    staff_access_assigned_at = COALESCE(staff_access_assigned_at, created_at, now()),
    staff_access_activated_at = COALESCE(staff_access_activated_at, now())
WHERE id IN (SELECT id FROM _reset_keep_members);

WITH platform_admin_claims(claim_name) AS (
    VALUES
        ('ACCESS_MATRIX_VIEW'), ('ACCESS_MATRIX_UPDATE'),
        ('USER_ACCESS_VIEW'), ('USER_ACCESS_CREATE'), ('USER_ACCESS_UPDATE'), ('USER_ACCESS_DELETE'),
        ('ADMIN_DASHBOARD_VIEW'),
        ('SACCO_REGISTRY_VIEW'), ('SACCO_REGISTRY_CREATE'), ('SACCO_REGISTRY_UPDATE'), ('SACCO_REGISTRY_DELETE'),
        ('PLATFORM_SETTINGS_VIEW'), ('PLATFORM_SETTINGS_UPDATE'),
        ('WORKSPACE_SETTINGS_VIEW'), ('WORKSPACE_SETTINGS_UPDATE'), ('WORKSPACE_SETTINGS_CONFIGURE'),
        ('LOAN_PRODUCTS_VIEW'), ('LOAN_PRODUCTS_CREATE'), ('LOAN_PRODUCTS_UPDATE'), ('LOAN_PRODUCTS_CONFIGURE'), ('LOAN_PRODUCTS_DELETE'),
        ('APPROVAL_FLOW_VIEW'), ('APPROVAL_FLOW_CONFIGURE'),
        ('SMS_USAGE_VIEW'), ('SMS_USAGE_ADD'), ('SMS_USAGE_CONFIGURE'),
        ('OUTBOX_VIEW'), ('OUTBOX_UPDATE'),
        ('NOTIFICATIONS_VIEW'), ('NOTIFICATIONS_UPDATE'),
        ('SUPPORT_VIEW'), ('SUPPORT_CREATE'), ('SUPPORT_UPDATE')
)
INSERT INTO public.member_access_claims (member_id, claim_name)
SELECT keep.id, claims.claim_name
FROM _reset_keep_members keep
CROSS JOIN platform_admin_claims claims
ON CONFLICT (member_id, claim_name) DO NOTHING;

CREATE UNIQUE INDEX IF NOT EXISTS uk_members_staff_no
    ON public.members (lower(staff_no))
    WHERE staff_no IS NOT NULL;

DELETE FROM public.sms_usage_ledger;
DELETE FROM public.station_sms_accounts;
DELETE FROM public.sacco_station_policies;
DELETE FROM public.sacco_stations;
DELETE FROM public.sacco_settings;

ALTER TABLE IF EXISTS public.sacco_settings
    DROP CONSTRAINT IF EXISTS sacco_settings_portfolio_at_risk_days_check;

ALTER TABLE IF EXISTS public.sacco_settings
    ADD CONSTRAINT sacco_settings_portfolio_at_risk_days_check
    CHECK (portfolio_at_risk_days BETWEEN 1 AND 365);

ALTER TABLE IF EXISTS public.sacco_station_policies
    DROP CONSTRAINT IF EXISTS sacco_station_policies_portfolio_at_risk_days_check;

ALTER TABLE IF EXISTS public.sacco_station_policies
    ADD CONSTRAINT sacco_station_policies_portfolio_at_risk_days_check
    CHECK (portfolio_at_risk_days IS NULL OR portfolio_at_risk_days BETWEEN 1 AND 365);

DELETE FROM public.registered_saccos;

CREATE SEQUENCE IF NOT EXISTS public.sacco_numeric_id_seq
    AS integer
    MINVALUE 1001
    MAXVALUE 9999
    START WITH 1001
    INCREMENT BY 1
    NO CYCLE;

SELECT setval('public.sacco_numeric_id_seq', 1001, false);
SELECT setval('public.staff_number_seq', 10000, false);

INSERT INTO public.platform_sms_settings (id, low_percent, critical_percent, created_at, updated_at)
VALUES ('DEFAULT', 20, 10, now(), now())
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.platform_branding_settings (
    id,
    logo_min_width_px,
    logo_min_height_px,
    logo_max_width_px,
    logo_max_height_px,
    logo_max_file_size_kb,
    created_at,
    updated_at
)
VALUES ('DEFAULT', 64, 64, 1024, 1024, 1024, now(), now())
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.platform_support_contact_settings (
    id,
    display_name,
    display_role,
    phone,
    email,
    office_hours,
    support_note,
    created_at,
    updated_at
)
VALUES ('DEFAULT', 'Platform Support', 'Super Admin Support', '', '', '', '', now(), now())
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.platform_session_settings (
    id,
    timeout_minutes,
    created_at,
    updated_at
)
VALUES ('DEFAULT', 30, now(), now())
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.platform_email_settings (
    id,
    enabled,
    host,
    port,
    username,
    password_encrypted,
    from_address,
    override_recipient,
    ssl_enabled,
    starttls_enabled,
    connection_timeout_ms,
    read_timeout_ms,
    write_timeout_ms,
    created_at,
    updated_at
)
VALUES ('DEFAULT', false, '', 465, '', '', '', '', true, false, 10000, 10000, 10000, now(), now())
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.platform_sms_gateway_settings (
    id,
    enabled,
    base_url,
    send_path,
    client_id,
    api_key_encrypted,
    sender_id,
    connect_timeout_seconds,
    read_timeout_seconds,
    created_at,
    updated_at
)
VALUES ('DEFAULT', false, '', '', '', '', '', 3, 8, now(), now())
ON CONFLICT (id) DO NOTHING;

ALTER TABLE IF EXISTS public.guarantor_requests
    DROP CONSTRAINT IF EXISTS fk_guarantor_requests_external_registry;

ALTER TABLE IF EXISTS public.guarantor_requests
    ADD CONSTRAINT fk_guarantor_requests_external_registry
    FOREIGN KEY (external_guarantor_registry_id)
    REFERENCES public.external_guarantor_registry(id);

DO $$
DECLARE
    preserved_super_admins integer;
BEGIN
    SELECT COUNT(*) INTO preserved_super_admins FROM _reset_keep_members;
    RAISE NOTICE 'Operational reset complete. Preserved % Super Admin account(s).', preserved_super_admins;
END $$;

COMMIT;
