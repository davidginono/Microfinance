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
 *   - preserved Super Admin user_settings and ADMIN staff role
 *   - platform_sms_settings
 *   - platform_branding_settings
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
DELETE FROM public.loan_payment_transactions;
DELETE FROM public.board_reviews;
DELETE FROM public.manager_reviews;
DELETE FROM public.guarantor_requests;
DELETE FROM public.loan_applications;
DELETE FROM public.sacco_loan_app_counter;

DELETE FROM public.stored_uploads;

DELETE FROM public.loan_product_required_attachments;
DELETE FROM public.loan_product_board_reviewers;
DELETE FROM public.loan_product_versions;
DELETE FROM public.loan_products_versions;
DELETE FROM public.loan_product_settings;

DELETE FROM public.accounts_savings;

DELETE FROM public.member_payment_details
WHERE member_id NOT IN (SELECT id FROM _reset_keep_members);

DELETE FROM public.user_settings
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

CREATE UNIQUE INDEX IF NOT EXISTS uk_members_staff_no
    ON public.members (lower(staff_no))
    WHERE staff_no IS NOT NULL;

DELETE FROM public.sms_usage_ledger;
DELETE FROM public.station_sms_accounts;
DELETE FROM public.sacco_station_policies;
DELETE FROM public.sacco_stations;
DELETE FROM public.sacco_settings;

DELETE FROM public.registered_saccos;

CREATE SEQUENCE IF NOT EXISTS public.sacco_numeric_id_seq
    AS integer
    MINVALUE 1001
    MAXVALUE 9999
    START WITH 1001
    INCREMENT BY 1
    NO CYCLE;

SELECT setval('public.sacco_numeric_id_seq', 1001, false);

INSERT INTO public.platform_sms_settings (id, low_percent, critical_percent, created_at, updated_at)
VALUES ('DEFAULT', 20, 10, now(), now())
ON CONFLICT (id) DO UPDATE
SET low_percent = excluded.low_percent,
    critical_percent = excluded.critical_percent,
    updated_at = now();

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

DO $$
DECLARE
    preserved_super_admins integer;
BEGIN
    SELECT COUNT(*) INTO preserved_super_admins FROM _reset_keep_members;
    RAISE NOTICE 'Operational reset complete. Preserved % Super Admin account(s).', preserved_super_admins;
END $$;

COMMIT;
