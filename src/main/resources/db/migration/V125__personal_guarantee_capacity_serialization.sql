-- Serialize competing approval paths before inspecting whether a personal budget exists.
CREATE OR REPLACE FUNCTION lending_personal_existing_capacity_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE l loan_applications;client uuid;cap numeric;
BEGIN
 IF TG_TABLE_NAME='guarantor_requests' THEN
  IF NEW.status<>'APPROVED' OR NEW.guarantor_member_id IS NULL THEN RETURN NEW; END IF;
  SELECT * INTO l FROM loan_applications WHERE id=NEW.loan_application_id;client=NEW.guarantor_member_id;
  cap=l.amount+(l.financial_snapshot->>'interestAmount')::numeric+coalesce((l.financial_snapshot->>'recordedChargeTotal')::numeric,0);
 ELSE
  SELECT * INTO l FROM loan_applications WHERE id=NEW.loan_id;client=NEW.guarantor_id;cap=NEW.amount_cap;
 END IF;
 PERFORM pg_advisory_xact_lock(hashtextextended('GROUP_CAPACITY/'||l.sacco_id||'/'||l.station_id||'/'||client::text,0));
 IF EXISTS(SELECT 1 FROM lending_group_personal_commitment WHERE client_id=client AND sacco_id=l.sacco_id)
 THEN PERFORM lending_personal_capacity(client,l.sacco_id,l.station_id,l.id,cap);END IF;
 RETURN NEW;
END $$;
CREATE OR REPLACE FUNCTION lending_personal_capacity(client uuid,institution text,branch text,target uuid,cap numeric) RETURNS void LANGUAGE plpgsql AS $$
DECLARE budget numeric;reserved numeric;unknown integer;contingent numeric;
BEGIN
 IF NOT lending_guarantor_capacity_lock(client,institution,branch) THEN RAISE EXCEPTION 'Current scoped guarantor required'; END IF;
 IF EXISTS(SELECT 1 FROM lending_group_personal_commitment WHERE client_id=client AND sacco_id=institution AND station_id<>branch)
  OR EXISTS(SELECT 1 FROM lending_shared_credit_obligation WHERE client_id=client AND sacco_id=institution AND (station_id<>branch OR missing_history))
 THEN RAISE EXCEPTION 'Unresolved guarantor branch exposure'; END IF;
 SELECT min(capacity) INTO budget FROM lending_group_personal_commitment WHERE client_id=client AND sacco_id=institution AND station_id=branch;
 IF budget IS NULL THEN RETURN; END IF;
 SELECT coalesce(sum(amount_cap),0),count(*) FILTER(WHERE amount_cap IS NULL) INTO reserved,unknown
 FROM lending_guarantor_commitment WHERE guarantor_id=client AND sacco_id=institution AND station_id=branch AND loan_id<>target;
 SELECT coalesce(sum(greatest(0,maximum_liability-allocated_obligation)),0) INTO contingent FROM lending_shared_credit_obligation WHERE client_id=client AND sacco_id=institution AND station_id=branch;
 IF unknown>0 OR cap IS NULL OR reserved+contingent+cap>budget THEN RAISE EXCEPTION 'Verified personal guarantee capacity exceeded'; END IF;
END $$;
-- Scope cancellation to its staff actor even if the proposed guarantor has since become inactive.
DO $$ DECLARE definition text;marker text;BEGIN
 SELECT pg_get_functiondef('lending_personal_evidence_guard()'::regprocedure) INTO definition;
 marker=$old$SELECT * INTO m FROM members WHERE id=NEW.client_id AND sacco_id=g.sacco_id AND station_id=g.station_id AND status='ACTIVE' AND is_member;$old$;
 IF strpos(definition,marker)=0 THEN RAISE EXCEPTION 'Personal evidence identity marker missing'; END IF;
 definition=replace(definition,marker,$new$SELECT * INTO m FROM members WHERE id=NEW.client_id AND sacco_id=g.sacco_id AND station_id=g.station_id AND is_member
  AND (status='ACTIVE' OR TG_TABLE_NAME='lending_group_personal_decision' AND to_jsonb(NEW)->>'decision'='CANCELLED');$new$);
 marker=$old$IF NEW.decision='ACCEPTED' THEN$old$;
 IF strpos(definition,marker)=0 THEN RAISE EXCEPTION 'Personal consent marker missing'; END IF;
 definition=replace(definition,marker,marker||$new$
    PERFORM lending_personal_capacity(g.client_id,g.sacco_id,g.station_id,g.loan_id,g.amount_cap);$new$);
 EXECUTE definition;
END $$;
