-- Reuse recorded-loan revisions for staff joint contracts before their first review.
ALTER TABLE loan_record_edit ADD COLUMN before_group_agreement bytea;

CREATE OR REPLACE FUNCTION loan_record_editable(application uuid) RETURNS boolean LANGUAGE sql STABLE AS $$
 SELECT EXISTS(SELECT 1 FROM loan_applications l WHERE l.id=application AND l.status='RECORDED'
  AND NOT EXISTS(SELECT 1 FROM loan_group_request WHERE loan_id=l.id)
  AND NOT EXISTS(SELECT 1 FROM guarantor_requests WHERE loan_application_id=l.id)
  AND NOT EXISTS(SELECT 1 FROM loan_ledgers WHERE loan_application_id=l.id)
  AND NOT EXISTS(SELECT 1 FROM accountant_loan_post WHERE loan_id=l.id)
  AND NOT EXISTS(SELECT 1 FROM loan_charge_event WHERE loan_id=l.id)
  AND NOT EXISTS(SELECT 1 FROM loan_charge_command WHERE loan_id=l.id)
  AND NOT EXISTS(SELECT 1 FROM lending_group_participant WHERE loan_id=l.id)
  AND NOT EXISTS(SELECT 1 FROM staff_group_funding WHERE loan_id=l.id)
  AND NOT EXISTS(SELECT 1 FROM staff_group_withdrawal WHERE loan_id=l.id)
  AND ((l.applicant_member_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM staff_group_loan WHERE loan_id=l.id))
    OR (l.applicant_member_id IS NULL AND EXISTS(SELECT 1 FROM staff_group_loan WHERE loan_id=l.id))))
$$;

CREATE OR REPLACE FUNCTION loan_record_edit_capture() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE l loan_applications; r accountant_loan_record; c staff_group_loan;
BEGIN
 SELECT * INTO l FROM loan_applications WHERE id=NEW.loan_id FOR UPDATE;
 SELECT * INTO r FROM accountant_loan_record WHERE loan_id=NEW.loan_id FOR UPDATE;
 IF NOT loan_record_editable(NEW.loan_id) OR l.version<>NEW.expected_version
  OR ROW(NEW.sacco_id,NEW.station_id,NEW.actor_id) IS DISTINCT FROM ROW(r.sacco_id,r.station_id,r.actor_id)
  OR NOT EXISTS(SELECT 1 FROM members m WHERE m.id=NEW.actor_id AND m.sacco_id=NEW.sacco_id AND m.station_id=NEW.station_id AND m.status='ACTIVE')
 THEN RAISE EXCEPTION 'loanGroups.error.changed'; END IF;
 NEW.transaction_id=txid_current(); NEW.recorded_at=current_timestamp;
 NEW.before_snapshot=jsonb_build_object('loan',to_jsonb(l),'record',to_jsonb(r),
  'chargeContract',(SELECT to_jsonb(x) FROM loan_charge_contract x WHERE loan_id=l.id),
  'chargeTerms',coalesce((SELECT jsonb_agg(to_jsonb(t) ORDER BY t.id) FROM loan_charge_term t WHERE loan_id=l.id),'[]'::jsonb));
 NEW.before_group_agreement=NULL;
 IF l.applicant_member_id IS NULL THEN
  SELECT * INTO c FROM staff_group_loan WHERE loan_id=l.id;
  NEW.before_group_agreement=c.body;
  NEW.before_snapshot=NEW.before_snapshot || jsonb_build_object('groupContract',to_jsonb(c)-'body',
   'groupBorrowers',(SELECT jsonb_agg(to_jsonb(b) ORDER BY b.client_id) FROM staff_group_borrower b WHERE loan_id=l.id),
   'groupGuarantors',coalesce((SELECT jsonb_agg(to_jsonb(g) ORDER BY g.client_id) FROM staff_group_guarantor g WHERE loan_id=l.id),'[]'::jsonb));
 END IF;
 RETURN NEW;
END $$;

CREATE OR REPLACE FUNCTION loan_record_edit_complete() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE l loan_applications; r accountant_loan_record;
BEGIN
 SELECT * INTO l FROM loan_applications WHERE id=NEW.loan_id;
 SELECT * INTO r FROM accountant_loan_record WHERE loan_id=NEW.loan_id;
 IF NOT loan_record_editable(l.id) OR l.version<>NEW.expected_version+1
  OR l.form_data->>'requestKey' IS DISTINCT FROM NEW.request_key::text OR r.metadata IS DISTINCT FROM l.form_data::jsonb
  OR r.requested_principal IS DISTINCT FROM l.amount OR r.application_date IS DISTINCT FROM (r.metadata->>'applicationDate')::date
 THEN RAISE EXCEPTION 'Complete recorded loan edit required'; END IF;
 IF NEW.before_snapshot ? 'groupContract' THEN
  IF (SELECT count(*) FROM staff_group_guarantor WHERE loan_id=l.id)
   <> coalesce(jsonb_array_length(r.metadata #> '{applicationDetails,guarantors}'),0)
  THEN RAISE EXCEPTION 'Complete recorded group guarantees required'; END IF;
  PERFORM staff_group_contract_validate(l.id,true);
 END IF;
 RETURN NULL;
END $$;

CREATE FUNCTION staff_group_edit_history_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF loan_record_edit_open(OLD.loan_id) THEN
  IF TG_TABLE_NAME='staff_group_loan' THEN
   IF TG_OP='UPDATE'
    AND ROW(NEW.loan_id,NEW.group_id,NEW.sacco_id,NEW.station_id,NEW.contract_version,NEW.request_hash,NEW.recorded_by,NEW.reference,NEW.language)
     IS NOT DISTINCT FROM ROW(OLD.loan_id,OLD.group_id,OLD.sacco_id,OLD.station_id,OLD.contract_version,OLD.request_hash,OLD.recorded_by,OLD.reference,OLD.language)
   THEN RETURN NEW; END IF;
  ELSIF TG_OP='DELETE' THEN RETURN OLD;
  END IF;
 END IF;
 RAISE EXCEPTION 'Recorded group loan evidence is immutable';
END $$;

DO $$ DECLARE name text; definition text; marker text; BEGIN
 FOREACH name IN ARRAY ARRAY['staff_group_loan','staff_group_borrower','staff_group_guarantor'] LOOP
  EXECUTE format('DROP TRIGGER staff_group_history ON %I',name);
  EXECUTE format('CREATE TRIGGER staff_group_history BEFORE UPDATE OR DELETE ON %I FOR EACH ROW EXECUTE FUNCTION staff_group_edit_history_guard()',name);
 END LOOP;

 -- The individual edit path can change clients. A group edit keeps its existing borrower party.
 SELECT pg_get_functiondef('loan_borrower_identity_guard()'::regprocedure) INTO definition;
 marker := 'IF TG_OP=''UPDATE'' AND loan_record_edit_open(OLD.id) THEN';
 IF strpos(definition,marker)=0 THEN RAISE EXCEPTION 'Individual edit identity guard missing'; END IF;
 EXECUTE replace(definition,marker,'IF TG_OP=''UPDATE'' AND OLD.applicant_member_id IS NOT NULL AND loan_record_edit_open(OLD.id) THEN');

 SELECT pg_get_functiondef('staff_group_application_guard()'::regprocedure) INTO definition;
 marker := 'AND ((NEW.first_repayment_date IS DISTINCT FROM OLD.first_repayment_date';
 IF strpos(definition,marker)=0 THEN RAISE EXCEPTION 'Group term guard missing'; END IF;
 EXECUTE replace(definition,marker,'AND NOT loan_record_edit_open(OLD.id) '||marker);
END $$;
