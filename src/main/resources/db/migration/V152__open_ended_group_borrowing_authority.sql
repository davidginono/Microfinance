-- NULL means an open-ended mandate. Existing dates and signed snapshots stay as recorded.
ALTER TABLE lending_group_authority ALTER COLUMN valid_through DROP NOT NULL;

-- Keep the existing start-date, scope and revocation checks on origination, signing and payout.
DO $$
DECLARE
 guard regprocedure;
 definition text;
BEGIN
 FOREACH guard IN ARRAY ARRAY[
  'lending_group_facility_guard()'::regprocedure,
  'lending_group_org_signature_guard()'::regprocedure,
  'lending_group_org_payout_scope(uuid,uuid,character varying,character varying,timestamp with time zone)'::regprocedure
 ] LOOP
  SELECT pg_get_functiondef(guard) INTO definition;
  IF strpos(definition, 'h.valid_through')=0 THEN
   RAISE EXCEPTION 'Missing authority end-date check in %', guard;
  END IF;
  EXECUTE replace(definition, 'h.valid_through', 'coalesce(h.valid_through, ''infinity''::date)');
 END LOOP;
END $$;
