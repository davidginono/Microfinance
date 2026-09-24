-- Refresh empty-database onboarding defaults after all current schema objects exist.
-- Historical onboarding migrations stay immutable; this migration aligns the
-- platform Super Admin, platform defaults, sequences, and claims with the
-- current schema.

ALTER TABLE public.members
    ALTER COLUMN sacco_id DROP NOT NULL;

DO $$
DECLARE
    super_admin_id uuid;
    super_admin_staff_no varchar(255);
BEGIN
    SELECT id
    INTO super_admin_id
    FROM public.members
    WHERE member_no = 'ADM001'
    ORDER BY created_at ASC NULLS LAST
    LIMIT 1;

    IF super_admin_id IS NULL THEN
        SELECT id
        INTO super_admin_id
        FROM public.members
        WHERE id = '00000000-0000-0000-0000-000000000001'
        LIMIT 1;

        IF super_admin_id IS NULL THEN
            super_admin_id := '00000000-0000-0000-0000-000000000001';
            super_admin_staff_no := CASE
                WHEN EXISTS (
                    SELECT 1
                    FROM public.members
                    WHERE staff_no = 'ADM001'
                ) THEN null
                ELSE 'ADM001'
            END;

            INSERT INTO public.members (
                id,
                created_at,
                email,
                full_name,
                member_no,
                staff_no,
                password_hash,
                phone,
                "position",
                profile_last_synced_at,
                rank,
                sacco_id,
                status,
                station_id,
                is_member,
                staff_access_status,
                staff_access_assigned_at,
                staff_access_activated_at,
                signature_registered_at,
                signature_text,
                phone_verified_at
            )
            VALUES (
                super_admin_id,
                now(),
                null,
                'Super Admin',
                'ADM001',
                super_admin_staff_no,
                '$2a$10$4744Rxj0in3jgiirjBqqY.WGf1lh1ygq.S2zK3aiI4Wuz7F5zfCky',
                null,
                'ADMIN',
                null,
                1,
                null,
                'ACTIVE',
                null,
                false,
                'ACTIVE',
                now(),
                now(),
                null,
                null,
                null
            );
        ELSE
            UPDATE public.members
            SET member_no = 'ADM001'
            WHERE id = super_admin_id;
        END IF;
    END IF;

    UPDATE public.members
    SET full_name = 'Super Admin',
        "position" = 'ADMIN',
        rank = 1,
        sacco_id = null,
        status = 'ACTIVE',
        station_id = null,
        is_member = false,
        staff_no = CASE
            WHEN NULLIF(staff_no, '') IS NOT NULL THEN staff_no
            WHEN EXISTS (
                SELECT 1
                FROM public.members other_member
                WHERE other_member.staff_no = 'ADM001'
                  AND other_member.id <> super_admin_id
            ) THEN null
            ELSE 'ADM001'
        END,
        staff_access_status = 'ACTIVE',
        staff_access_assigned_at = COALESCE(staff_access_assigned_at, created_at, now()),
        staff_access_activated_at = COALESCE(staff_access_activated_at, now())
    WHERE id = super_admin_id;
END $$;

INSERT INTO public.member_staff_roles (member_id, role_name)
SELECT id, 'ADMIN'
FROM public.members
WHERE member_no = 'ADM001'
ON CONFLICT (member_id, role_name) DO NOTHING;

DELETE FROM public.member_staff_roles
WHERE member_id = (SELECT id FROM public.members WHERE member_no = 'ADM001')
  AND role_name <> 'ADMIN';

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
SELECT m.id, claims.claim_name
FROM public.members m
CROSS JOIN platform_admin_claims claims
WHERE m.member_no = 'ADM001'
ON CONFLICT (member_id, claim_name) DO NOTHING;

DELETE FROM public.registered_saccos
WHERE sacco_id = 'PLATFORM';

CREATE SEQUENCE IF NOT EXISTS public.sacco_numeric_id_seq
    AS integer
    MINVALUE 1001
    MAXVALUE 9999
    START WITH 1001
    INCREMENT BY 1
    NO CYCLE;

DO $$
DECLARE
    max_numeric_id integer;
BEGIN
    SELECT max(sacco_id::integer)
    INTO max_numeric_id
    FROM public.registered_saccos
    WHERE sacco_id ~ '^[0-9]{4}$';

    IF max_numeric_id IS NULL THEN
        PERFORM setval('public.sacco_numeric_id_seq', 1001, false);
    ELSE
        PERFORM setval('public.sacco_numeric_id_seq', max_numeric_id, true);
    END IF;
END $$;

CREATE SEQUENCE IF NOT EXISTS public.staff_number_seq
    AS bigint
    START WITH 10000
    INCREMENT BY 1
    MINVALUE 10000
    MAXVALUE 99999
    CYCLE
    CACHE 1;

ALTER SEQUENCE public.staff_number_seq
    MINVALUE 10000
    MAXVALUE 99999
    CYCLE
    CACHE 1;

DO $$
DECLARE
    highest_existing bigint;
    current_issued bigint;
    sequence_target bigint;
BEGIN
    SELECT coalesce(max(value), 9999)
    INTO highest_existing
    FROM (
        SELECT staff_no::bigint AS value
        FROM public.members
        WHERE staff_no ~ '^[0-9]{5}$'
        UNION ALL
        SELECT member_no::bigint AS value
        FROM public.members
        WHERE member_no ~ '^[0-9]{5}$'
        UNION ALL
        SELECT substring(member_no from '^STAFF-([0-9]{5})$')::bigint AS value
        FROM public.members
        WHERE member_no ~ '^STAFF-[0-9]{5}$'
    ) used_numbers;

    SELECT CASE WHEN is_called THEN last_value ELSE last_value - 1 END
    INTO current_issued
    FROM public.staff_number_seq;

    sequence_target := greatest(coalesce(current_issued, 9999), highest_existing);
    IF sequence_target < 10000 THEN
        PERFORM setval('public.staff_number_seq', 10000, false);
    ELSIF sequence_target > 99999 THEN
        PERFORM setval('public.staff_number_seq', 99999, true);
    ELSE
        PERFORM setval('public.staff_number_seq', sequence_target, true);
    END IF;
END $$;

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
