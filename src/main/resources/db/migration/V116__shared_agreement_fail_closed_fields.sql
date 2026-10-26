-- Missing language/type is unavailable data, never a valid document or payment instruction.
DO $$ DECLARE definition text;marker text; BEGIN
 SELECT pg_get_functiondef('lending_group_shared_document_insert()'::regprocedure) INTO definition;
 marker='NEW.metadata->>''documentLanguage'' NOT IN';
 IF strpos(definition,marker)=0 THEN RAISE EXCEPTION 'Shared document language marker unavailable'; END IF;
 definition=replace(definition,marker,'coalesce(NEW.metadata->>''documentLanguage'','''') NOT IN');
 marker=' NEW.contract_hash=encode';
 IF strpos(definition,marker)=0 THEN RAISE EXCEPTION 'Shared contract digest marker unavailable'; END IF;
 definition=replace(definition,marker,$fields$
 IF length(trim(NEW.institution_name))=0
   OR (length(trim(coalesce(NEW.metadata->>'contactPhone','')))=0 AND length(trim(coalesce(NEW.metadata->>'contactEmail','')))=0)
   OR (length(trim(coalesce(NEW.metadata->>'complaintPhone','')))=0 AND length(trim(coalesce(NEW.metadata->>'complaintEmail','')))=0)
 THEN RAISE EXCEPTION 'Shared agreement institution and complaint contacts are required'; END IF;
$fields$||marker);
 EXECUTE definition;
 SELECT pg_get_functiondef('lending_group_shared_payout_guard()'::regprocedure) INTO definition;
 marker='NEW.destination->>''destinationType'' NOT IN';
 IF strpos(definition,marker)=0 THEN RAISE EXCEPTION 'Shared payout destination marker unavailable'; END IF;
 definition=replace(definition,marker,'coalesce(NEW.destination->>''destinationType'','''') NOT IN');
 marker='   OR NEW.accepted_at<d.recorded_at';
 IF strpos(definition,marker)=0 THEN RAISE EXCEPTION 'Shared signature chronology marker unavailable'; END IF;
 definition=replace(definition,marker,marker||$chronology$
   OR NEW.accepted_at<(SELECT signed_at FROM lending_group_consent_evidence WHERE cycle_id=c.id AND client_id=m.id)
$chronology$);
 EXECUTE definition;
END $$;
