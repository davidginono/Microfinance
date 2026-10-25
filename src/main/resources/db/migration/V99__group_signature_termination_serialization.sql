-- Direct database consent writes must serialize with cycle withdrawal too.
DO $$ DECLARE name text; definition text; original text='SELECT * INTO c FROM lending_group_cycle WHERE id=NEW.cycle_id;'; BEGIN
 FOREACH name IN ARRAY ARRAY['lending_group_signed_consent_guard()','lending_group_cycle_scope()'] LOOP
  SELECT pg_get_functiondef(name::regprocedure) INTO definition;
  IF strpos(definition,original)=0 THEN RAISE EXCEPTION 'Group consent scope guard was not found'; END IF;
  EXECUTE replace(definition,original,'SELECT * INTO c FROM lending_group_cycle WHERE id=NEW.cycle_id FOR SHARE;');
 END LOOP;
END $$;
