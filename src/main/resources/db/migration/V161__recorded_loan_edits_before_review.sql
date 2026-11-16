-- Staff may revise an individual application before its first review only.
-- Every previous loan, recording envelope, attachment list and charge term remains evidence.
CREATE FUNCTION loan_record_editable(application uuid) RETURNS boolean LANGUAGE sql STABLE AS $$
 SELECT EXISTS(SELECT 1 FROM loan_applications l WHERE l.id=application AND l.status='RECORDED'
  AND l.applicant_member_id IS NOT NULL
  AND NOT EXISTS(SELECT 1 FROM loan_group_request WHERE loan_id=l.id)
  AND NOT EXISTS(SELECT 1 FROM guarantor_requests WHERE loan_application_id=l.id)
  AND NOT EXISTS(SELECT 1 FROM loan_ledgers WHERE loan_application_id=l.id)
  AND NOT EXISTS(SELECT 1 FROM accountant_loan_post WHERE loan_id=l.id)
  AND NOT EXISTS(SELECT 1 FROM loan_charge_event WHERE loan_id=l.id)
  AND NOT EXISTS(SELECT 1 FROM loan_charge_command WHERE loan_id=l.id)
  AND NOT EXISTS(SELECT 1 FROM lending_group_participant WHERE loan_id=l.id)
  AND NOT EXISTS(SELECT 1 FROM staff_group_loan WHERE loan_id=l.id))
$$;
CREATE TABLE loan_record_edit (
 loan_id uuid NOT NULL, sacco_id varchar(255) NOT NULL, station_id varchar(255) NOT NULL,
 actor_id uuid NOT NULL REFERENCES members(id), request_key uuid NOT NULL,
 payload_hash varchar(64) NOT NULL CHECK(length(payload_hash)=64), expected_version integer NOT NULL,
 transaction_id bigint NOT NULL DEFAULT txid_current(), recorded_at timestamptz NOT NULL DEFAULT current_timestamp,
 before_snapshot jsonb NOT NULL,
 PRIMARY KEY(loan_id,expected_version), UNIQUE(sacco_id,station_id,request_key), UNIQUE(loan_id,transaction_id),
 FOREIGN KEY(loan_id,sacco_id,station_id) REFERENCES accountant_loan_record(loan_id,sacco_id,station_id)
);
CREATE TRIGGER loan_record_edit_immutable BEFORE UPDATE OR DELETE ON loan_record_edit FOR EACH ROW EXECUTE FUNCTION gl_protect_evidence();
CREATE FUNCTION loan_record_edit_capture() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE l loan_applications; r accountant_loan_record;
BEGIN
 SELECT * INTO l FROM loan_applications WHERE id=NEW.loan_id FOR UPDATE;
 SELECT * INTO r FROM accountant_loan_record WHERE loan_id=NEW.loan_id FOR UPDATE;
 IF NOT loan_record_editable(NEW.loan_id) OR l.version<>NEW.expected_version
  OR ROW(NEW.sacco_id,NEW.station_id,NEW.actor_id) IS DISTINCT FROM ROW(r.sacco_id,r.station_id,r.actor_id)
  OR NOT EXISTS(SELECT 1 FROM members m WHERE m.id=NEW.actor_id AND m.sacco_id=NEW.sacco_id AND m.station_id=NEW.station_id AND m.status='ACTIVE')
 THEN RAISE EXCEPTION 'loanGroups.error.changed'; END IF;
 NEW.transaction_id=txid_current(); NEW.recorded_at=current_timestamp;
 NEW.before_snapshot=jsonb_build_object('loan',to_jsonb(l),'record',to_jsonb(r),
  'chargeContract',(SELECT to_jsonb(c) FROM loan_charge_contract c WHERE loan_id=l.id),
  'chargeTerms',coalesce((SELECT jsonb_agg(to_jsonb(t) ORDER BY t.id) FROM loan_charge_term t WHERE loan_id=l.id),'[]'::jsonb));
 RETURN NEW;
END $$;
CREATE TRIGGER loan_record_edit_capture BEFORE INSERT ON loan_record_edit FOR EACH ROW EXECUTE FUNCTION loan_record_edit_capture();
CREATE FUNCTION loan_record_edit_open(application uuid) RETURNS boolean LANGUAGE sql STABLE AS $$
 SELECT loan_record_editable(application) AND EXISTS(SELECT 1 FROM loan_record_edit e JOIN loan_applications l ON l.id=e.loan_id
  WHERE e.loan_id=application AND e.transaction_id=txid_current() AND l.version BETWEEN e.expected_version AND e.expected_version+1)
$$;
CREATE FUNCTION loan_record_edit_complete() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE l loan_applications; r accountant_loan_record;
BEGIN
 SELECT * INTO l FROM loan_applications WHERE id=NEW.loan_id;
 SELECT * INTO r FROM accountant_loan_record WHERE loan_id=NEW.loan_id;
 IF NOT loan_record_editable(l.id) OR l.version<>NEW.expected_version+1
  OR l.form_data->>'requestKey' IS DISTINCT FROM NEW.request_key::text OR r.metadata IS DISTINCT FROM l.form_data::jsonb
  OR r.requested_principal IS DISTINCT FROM l.amount OR r.application_date IS DISTINCT FROM (r.metadata->>'applicationDate')::date
 THEN RAISE EXCEPTION 'Complete recorded loan edit required'; END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER loan_record_edit_complete AFTER INSERT ON loan_record_edit DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION loan_record_edit_complete();

-- Retain all existing guards outside the exact transaction owning the captured revision.
DO $$ DECLARE d text; BEGIN
 SELECT pg_get_functiondef('accountant_loan_terms_protect()'::regprocedure) INTO d;
 EXECUTE regexp_replace(d,'BEGIN',$patch$BEGIN
 IF TG_OP='UPDATE' AND loan_record_edit_open(OLD.id) THEN
  IF ROW(NEW.id,NEW.sacco_id,NEW.station_id,NEW.status,NEW.top_up_source_loan_id,NEW.application_number,NEW.loan_id,NEW.created_at)
   IS DISTINCT FROM ROW(OLD.id,OLD.sacco_id,OLD.station_id,OLD.status,OLD.top_up_source_loan_id,OLD.application_number,OLD.loan_id,OLD.created_at)
  THEN RAISE EXCEPTION 'Recorded loan identity and state must remain unchanged'; END IF;
  RETURN NEW;
 END IF;
 $patch$,'i');
 SELECT pg_get_functiondef('loan_charge_terms_guard()'::regprocedure) INTO d;
 EXECUTE regexp_replace(d,'BEGIN',$patch$BEGIN
 IF TG_TABLE_NAME='loan_applications' THEN IF loan_record_edit_open(OLD.id) THEN RETURN NEW; END IF; END IF;
 $patch$,'i');
 SELECT pg_get_functiondef('loan_borrower_identity_guard()'::regprocedure) INTO d;
 EXECUTE regexp_replace(d,'BEGIN',$patch$BEGIN
 IF TG_OP='UPDATE' AND loan_record_edit_open(OLD.id) THEN
  IF NEW.sacco_id IS DISTINCT FROM OLD.sacco_id OR NEW.station_id IS DISTINCT FROM OLD.station_id
   OR NOT EXISTS(SELECT 1 FROM members WHERE id=NEW.applicant_member_id AND sacco_id=NEW.sacco_id AND station_id=NEW.station_id AND status='ACTIVE' AND is_member)
  THEN RAISE EXCEPTION 'Scoped client required'; END IF;
  NEW.borrower_party_id=NEW.applicant_member_id;
  INSERT INTO loan_borrower_party(id,sacco_id,kind,client_id) VALUES(NEW.applicant_member_id,NEW.sacco_id,'CLIENT',NEW.applicant_member_id) ON CONFLICT(id) DO NOTHING;
  RETURN NEW;
 END IF;
 $patch$,'i');
END $$;
CREATE FUNCTION loan_record_edit_envelope_guard() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF TG_OP='UPDATE' AND loan_record_edit_open(OLD.loan_id)
  AND ROW(NEW.loan_id,NEW.sacco_id,NEW.station_id,NEW.actor_id,NEW.recorded_at,NEW.request_key,NEW.payload_hash)
   IS NOT DISTINCT FROM ROW(OLD.loan_id,OLD.sacco_id,OLD.station_id,OLD.actor_id,OLD.recorded_at,OLD.request_key,OLD.payload_hash)
 THEN RETURN NEW; END IF;
 RAISE EXCEPTION 'Recorded loan evidence is immutable';
END $$;
DROP TRIGGER accountant_loan_record_immutable ON accountant_loan_record;
CREATE TRIGGER accountant_loan_record_immutable BEFORE UPDATE OR DELETE ON accountant_loan_record FOR EACH ROW EXECUTE FUNCTION loan_record_edit_envelope_guard();
CREATE FUNCTION loan_record_edit_charge_guard() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF TG_OP='DELETE' AND loan_record_edit_open(OLD.loan_id) THEN RETURN OLD; END IF;
 RAISE EXCEPTION 'Frozen loan charges are immutable';
END $$;
DROP TRIGGER loan_charge_contract_immutable ON loan_charge_contract;
DROP TRIGGER loan_charge_term_immutable ON loan_charge_term;
CREATE TRIGGER loan_charge_contract_immutable BEFORE UPDATE OR DELETE ON loan_charge_contract FOR EACH ROW EXECUTE FUNCTION loan_record_edit_charge_guard();
CREATE TRIGGER loan_charge_term_immutable BEFORE UPDATE OR DELETE ON loan_charge_term FOR EACH ROW EXECUTE FUNCTION loan_record_edit_charge_guard();
