-- Bootstrap the default platform Super Admin only.
-- No SACCO workspace, staff, member, loan product, workflow, or demo data is seeded here.

INSERT INTO public.registered_saccos (sacco_id, sacco_name, active, created_at, updated_at)
VALUES ('PLATFORM', 'Platform Administration', true, now(), now())
ON CONFLICT (sacco_id) DO UPDATE
SET sacco_name = excluded.sacco_name,
    active = true,
    updated_at = now();

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
    'PLATFORM',
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
    sacco_id = 'PLATFORM',
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

