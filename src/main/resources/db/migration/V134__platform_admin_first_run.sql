-- Retire only the historical shared bootstrap credential. Customized admins are unchanged.
ALTER TABLE members ADD COLUMN platform_setup_required boolean NOT NULL DEFAULT false;
UPDATE members SET password_hash='!BOOTSTRAP_REQUIRED', platform_setup_required=true
WHERE id='00000000-0000-0000-0000-000000000001' AND member_no='ADM001'
  AND password_hash='$2a$10$4744Rxj0in3jgiirjBqqY.WGf1lh1ygq.S2zK3aiI4Wuz7F5zfCky'
  AND sacco_id IS NULL AND station_id IS NULL
  AND EXISTS (SELECT 1 FROM member_staff_roles WHERE member_id=members.id AND role_name='ADMIN');
