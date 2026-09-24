-- Normalize empty-database onboarding after the baseline/seed migrations.
-- This keeps Flyway as the source of truth for the platform Super Admin and
-- platform-level defaults without relying on Hibernate ddl-auto updates.

ALTER TABLE public.members
    ALTER COLUMN sacco_id DROP NOT NULL;

INSERT INTO public.members (
    id,
    created_at,
    email,
    full_name,
    member_no,
    password_hash,
    phone,
    "position",
    profile_last_synced_at,
    rank,
    sacco_id,
    status,
    station_id,
    is_member,
    signature_registered_at,
    signature_text,
    phone_verified_at
)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    now(),
    null,
    'Super Admin',
    'ADM001',
    '$2a$10$4744Rxj0in3jgiirjBqqY.WGf1lh1ygq.S2zK3aiI4Wuz7F5zfCky',
    null,
    'ADMIN',
    null,
    1,
    null,
    'ACTIVE',
    null,
    false,
    null,
    null,
    null
)
ON CONFLICT (member_no) DO UPDATE
SET password_hash = excluded.password_hash,
    full_name = excluded.full_name,
    "position" = 'ADMIN',
    rank = excluded.rank,
    sacco_id = null,
    status = 'ACTIVE',
    station_id = null,
    is_member = false;

INSERT INTO public.member_staff_roles (member_id, role_name)
SELECT id, 'ADMIN'
FROM public.members
WHERE member_no = 'ADM001'
ON CONFLICT (member_id, role_name) DO NOTHING;

DELETE FROM public.member_staff_roles
WHERE member_id = (SELECT id FROM public.members WHERE member_no = 'ADM001')
  AND role_name <> 'ADMIN';

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

INSERT INTO public.platform_sms_settings (id, low_percent, critical_percent, created_at, updated_at)
VALUES ('DEFAULT', 20, 10, now(), now())
ON CONFLICT (id) DO UPDATE
SET low_percent = excluded.low_percent,
    critical_percent = excluded.critical_percent,
    updated_at = now();
