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
 *   - stored_upload_migrations
 *
 * Clears MINOR_ADMIN/SACCOS Admin accounts and roles.
 *
 * Run only during maintenance on the intended database.
 */

BEGIN;

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
    is_member = false
WHERE id IN (SELECT id FROM _reset_keep_members);

DELETE FROM public.sms_usage_ledger;
DELETE FROM public.station_sms_accounts;
DELETE FROM public.sacco_station_policies;
DELETE FROM public.sacco_stations;
DELETE FROM public.sacco_settings;

DELETE FROM public.registered_saccos;

DO $$
DECLARE
    preserved_super_admins integer;
BEGIN
    SELECT COUNT(*) INTO preserved_super_admins FROM _reset_keep_members;
    RAISE NOTICE 'Operational reset complete. Preserved % Super Admin account(s).', preserved_super_admins;
END $$;

COMMIT;
