ALTER TABLE public.members
    ALTER COLUMN sacco_id DROP NOT NULL;

UPDATE public.members
SET sacco_id = NULL,
    station_id = NULL,
    "position" = 'ADMIN',
    status = 'ACTIVE',
    is_member = false
WHERE member_no = 'ADM001'
  AND EXISTS (
      SELECT 1
      FROM public.member_staff_roles msr
      WHERE msr.member_id = members.id
        AND msr.role_name = 'ADMIN'
  );

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
