-- Missing legacy terms stay unknown; do not parse declarations as numeric evidence.
CREATE OR REPLACE FUNCTION lending_personal_existing_capacity_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE l loan_applications;client uuid;cap numeric;ordinary boolean;
BEGIN
 ordinary=TG_TABLE_NAME='guarantor_requests';
 IF ordinary THEN
  IF NEW.status<>'APPROVED' OR NEW.guarantor_member_id IS NULL THEN RETURN NEW; END IF;
  SELECT * INTO l FROM loan_applications WHERE id=NEW.loan_application_id;client=NEW.guarantor_member_id;
 ELSE
  SELECT * INTO l FROM loan_applications WHERE id=NEW.loan_id;client=NEW.guarantor_id;
 END IF;
 PERFORM pg_advisory_xact_lock(hashtextextended('GROUP_CAPACITY/'||l.sacco_id||'/'||l.station_id||'/'||client::text,0));
 IF EXISTS(SELECT 1 FROM lending_group_personal_commitment WHERE client_id=client AND sacco_id=l.sacco_id) THEN
  IF ordinary THEN
   IF l.amount>0 AND jsonb_typeof(l.financial_snapshot->'interestAmount')='number'
    AND (CASE WHEN jsonb_typeof(l.financial_snapshot->'interestAmount')='number' THEN (l.financial_snapshot->>'interestAmount')::numeric>=0 ELSE false END)
    AND (NOT coalesce(l.financial_snapshot ? 'recordedChargeTotal',false) OR CASE WHEN jsonb_typeof(l.financial_snapshot->'recordedChargeTotal')='number' THEN (l.financial_snapshot->>'recordedChargeTotal')::numeric>=0 ELSE false END)
   THEN cap=l.amount+(l.financial_snapshot->>'interestAmount')::numeric+coalesce((l.financial_snapshot->>'recordedChargeTotal')::numeric,0);END IF;
  ELSE cap=NEW.amount_cap;END IF;
  PERFORM lending_personal_capacity(client,l.sacco_id,l.station_id,l.id,cap);
 END IF;
 RETURN NEW;
END $$;
-- Only the current scoped staff actor may cancel; moving the nominee never traps an unaccepted offer.
DO $$ DECLARE definition text;marker text;BEGIN
 SELECT pg_get_functiondef('lending_personal_evidence_guard()'::regprocedure) INTO definition;
 marker=$old$SELECT * INTO m FROM members WHERE id=NEW.client_id AND sacco_id=g.sacco_id AND station_id=g.station_id AND is_member
  AND (status='ACTIVE' OR TG_TABLE_NAME='lending_group_personal_decision' AND to_jsonb(NEW)->>'decision'='CANCELLED');$old$;
 IF strpos(definition,marker)=0 THEN RAISE EXCEPTION 'Personal cancellation identity marker missing'; END IF;
 definition=replace(definition,marker,$new$SELECT * INTO m FROM members WHERE id=NEW.client_id AND
  ((sacco_id=g.sacco_id AND station_id=g.station_id AND is_member AND status='ACTIVE') OR
   (TG_TABLE_NAME='lending_group_personal_decision' AND to_jsonb(NEW)->>'decision'='CANCELLED'));$new$);
 EXECUTE definition;
END $$;
