-- Admit only the scoped typed joint borrower; its deferred source guard remains mandatory.
DO $$ DECLARE definition text;marker text='OR (b.kind=''ORGANIZATION'''; BEGIN
 SELECT pg_get_functiondef('accountant_loan_record_scope()'::regprocedure) INTO definition;
 IF strpos(definition,marker)=0 THEN RAISE EXCEPTION 'Recorded borrower scope marker unavailable'; END IF;
 EXECUTE replace(definition,marker,$insert$
 OR (b.kind='JOINT_GROUP' AND a.applicant_member_id IS NULL AND b.group_station_id=NEW.station_id AND g.status='ACTIVE')
$insert$||marker);
END $$;
