-- Authority validity uses Tanzania's business date, independently of the pooled SQL session zone.
DO $$ DECLARE definition text; BEGIN
 SELECT pg_get_functiondef('lending_group_facility_guard()'::regprocedure) INTO definition;
 IF strpos(definition,'NEW.created_at::date')=0 OR strpos(definition,'h.recorded_at::date')=0
 THEN RAISE EXCEPTION 'Organization authority date checks unavailable'; END IF;
 definition=replace(definition,'NEW.created_at::date','(NEW.created_at AT TIME ZONE ''Africa/Dar_es_Salaam'')::date');
 definition=replace(definition,'h.recorded_at::date','(h.recorded_at AT TIME ZONE ''Africa/Dar_es_Salaam'')::date');
 EXECUTE definition;
END $$;
