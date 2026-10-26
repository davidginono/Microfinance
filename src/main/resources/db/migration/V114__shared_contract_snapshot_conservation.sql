-- Complete equality checks between the three retained representations of an allocation.
DO $$ DECLARE definition text;marker text=' periods=jsonb_array_length(f.snapshot->''schedule'');'; BEGIN
 SELECT pg_get_functiondef('lending_group_shared_validate(uuid)'::regprocedure) INTO definition;
 IF strpos(definition,marker)=0 THEN RAISE EXCEPTION 'Shared dated-component marker unavailable'; END IF;
 EXECUTE replace(definition,marker,$checks$
 IF jsonb_array_length(f.snapshot->'shares') IS DISTINCT FROM cnt OR jsonb_array_length(c.form_data->'participants') IS DISTINCT FROM cnt
  OR (SELECT count(DISTINCT s->>'clientId') FROM jsonb_array_elements(f.snapshot->'shares') s)<>cnt
  OR (SELECT count(DISTINCT s->>'clientId') FROM jsonb_array_elements(c.form_data->'participants') s)<>cnt
  OR (f.snapshot->>'totalPayable')::numeric IS DISTINCT FROM a.amount+(f.snapshot->>'interest')::numeric+(f.snapshot->>'charges')::numeric
  OR (f.snapshot->>'net')::numeric IS DISTINCT FROM a.amount-(f.snapshot->>'deductions')::numeric
  OR EXISTS(SELECT 1 FROM lending_group_shared_allocation s LEFT JOIN jsonb_to_recordset(f.snapshot->'shares')
    AS q("clientId" uuid,principal numeric,interest numeric,charges numeric,deductions numeric,net numeric,"monthlyPayment" numeric) ON q."clientId"=s.client_id
    WHERE s.cycle_id=cycle AND ROW(s.principal,s.interest,s.charges,s.deductions,s.net,s.monthly_payment)
      IS DISTINCT FROM ROW(q.principal,q.interest,q.charges,q.deductions,q.net,q."monthlyPayment"))
  OR EXISTS(SELECT 1 FROM lending_group_participant x LEFT JOIN jsonb_to_recordset(c.form_data->'participants')
    AS q("clientId" uuid,principal numeric,income numeric,expenses numeric,"otherDebt" numeric,"assessmentEvidence" text) ON q."clientId"=x.client_id
    WHERE x.cycle_id=cycle AND ROW(x.principal,x.income,x.expenses,x.other_debt,x.assessment_evidence)
      IS DISTINCT FROM ROW(q.principal,q.income,q.expenses,q."otherDebt",trim(q."assessmentEvidence")))
 THEN RAISE EXCEPTION 'Shared form, contractual shares and stored allocation must agree'; END IF;
$checks$||marker);
 SELECT pg_get_functiondef('lending_group_shared_insert()'::regprocedure) INTO definition;
 marker=' IF TG_TABLE_NAME=''lending_group_shared_facility'' THEN';
 IF strpos(definition,marker)=0 THEN RAISE EXCEPTION 'Shared creation lock marker unavailable'; END IF;
 EXECUTE replace(definition,marker,marker||$locks$
  PERFORM pg_advisory_xact_lock_shared(hashtextextended('GROUP_PRODUCT/'||c.sacco_id||'/'||c.station_id||'/'||c.product_id::text,0));
  PERFORM id FROM lending_group WHERE id=c.group_id FOR SHARE;
$locks$);
END $$;
