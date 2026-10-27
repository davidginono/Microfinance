-- Personal guarantees remain separate from organization authority and joint borrowing.
CREATE TABLE lending_group_personal_guarantee (
 id uuid PRIMARY KEY,loan_id uuid NOT NULL,cycle_id uuid NOT NULL,sacco_id varchar(255) NOT NULL,station_id varchar(255) NOT NULL,
 client_id uuid NOT NULL REFERENCES members(id),client_number varchar(255) NOT NULL,client_name varchar(255) NOT NULL,
 request_key uuid NOT NULL,payload_hash varchar(64) NOT NULL,packet_hash varchar(64) NOT NULL,
 amount_cap numeric(18,2) NOT NULL CHECK(amount_cap>0),capacity numeric(18,2) NOT NULL CHECK(capacity>=amount_cap),
 capacity_evidence varchar(500) NOT NULL CHECK(length(trim(capacity_evidence))>0),valid_through date NOT NULL,
 document_metadata jsonb NOT NULL,filename varchar(200) NOT NULL,body bytea NOT NULL CHECK(octet_length(body) BETWEEN 5 AND 5242880),
 sha256 varchar(64) NOT NULL,page_count integer NOT NULL CHECK(page_count BETWEEN 1 AND 50),snapshot jsonb NOT NULL,
 recorded_by uuid NOT NULL REFERENCES members(id),recorded_at timestamptz NOT NULL,
 UNIQUE(sacco_id,station_id,request_key),UNIQUE(id,loan_id,client_id,sacco_id,station_id),
 FOREIGN KEY(loan_id,sacco_id,station_id) REFERENCES loan_applications(id,sacco_id,station_id),
 FOREIGN KEY(cycle_id,sacco_id,station_id) REFERENCES lending_group_cycle(id,sacco_id,station_id)
);
CREATE INDEX ix_personal_guarantee_loan ON lending_group_personal_guarantee(loan_id,recorded_at DESC,id);
CREATE INDEX ix_personal_guarantee_client ON lending_group_personal_guarantee(client_id,sacco_id,station_id,recorded_at DESC,id);
CREATE TABLE lending_group_personal_decision (
 offer_id uuid PRIMARY KEY REFERENCES lending_group_personal_guarantee(id),client_id uuid NOT NULL REFERENCES members(id),
 decision varchar(16) NOT NULL CHECK(decision IN ('ACCEPTED','REFUSED','CANCELLED')),reason varchar(500) NOT NULL,
 actor_id uuid NOT NULL REFERENCES members(id),packet_hash varchar(64) NOT NULL,signature_text text,
 decided_at timestamptz NOT NULL,otp_required boolean NOT NULL,challenge_id uuid UNIQUE
);
CREATE TABLE lending_group_personal_challenge (
 id uuid PRIMARY KEY,offer_id uuid NOT NULL REFERENCES lending_group_personal_guarantee(id),client_id uuid NOT NULL REFERENCES members(id),
 sacco_id varchar(255) NOT NULL,station_id varchar(255) NOT NULL,packet_hash varchar(64) NOT NULL,code_hash varchar(255) NOT NULL,
 issued_at timestamptz NOT NULL,expires_at timestamptz NOT NULL CHECK(expires_at>issued_at),delivered_at timestamptz,consumed_at timestamptz,
 attempts integer NOT NULL DEFAULT 0 CHECK(attempts BETWEEN 0 AND 5)
);
ALTER TABLE lending_group_personal_decision ADD FOREIGN KEY(challenge_id) REFERENCES lending_group_personal_challenge(id);
CREATE INDEX ix_personal_guarantee_challenge ON lending_group_personal_challenge(offer_id,issued_at DESC,id DESC);
CREATE TABLE lending_group_personal_opened (
 offer_id uuid NOT NULL REFERENCES lending_group_personal_guarantee(id),client_id uuid NOT NULL REFERENCES members(id),
 packet_hash varchar(64) NOT NULL,opened_at timestamptz NOT NULL,PRIMARY KEY(offer_id,client_id)
);
CREATE TRIGGER personal_guarantee_immutable BEFORE UPDATE OR DELETE ON lending_group_personal_guarantee FOR EACH ROW EXECUTE FUNCTION gl_protect_evidence();
CREATE TRIGGER personal_decision_immutable BEFORE UPDATE OR DELETE ON lending_group_personal_decision FOR EACH ROW EXECUTE FUNCTION gl_protect_evidence();
CREATE TRIGGER personal_opened_immutable BEFORE UPDATE OR DELETE ON lending_group_personal_opened FOR EACH ROW EXECUTE FUNCTION gl_protect_evidence();

-- A pending offer expires; an accepted guarantee is retained until the underlying debt closes.
CREATE VIEW lending_group_personal_commitment AS
 SELECT g.*,d.decision FROM lending_group_personal_guarantee g JOIN loan_applications l ON l.id=g.loan_id AND l.sacco_id=g.sacco_id AND l.station_id=g.station_id
 LEFT JOIN lending_group_personal_decision d ON d.offer_id=g.id
 WHERE (d.decision='ACCEPTED' OR d.offer_id IS NULL AND g.valid_through>=(current_timestamp AT TIME ZONE 'Africa/Dar_es_Salaam')::date)
 AND l.status NOT IN ('WITHDRAWN','PAID','DISBURSEMENT_REVERSED','MANAGER_REJECTED','LOAN_OFFICER_REJECTED','CHAIRPERSON_REJECTED','BOARD_REJECTED','CREDIT_COMMITTEE_REJECTED','ACCOUNTANT_REJECTED','REJECTED');

DO $$ DECLARE definition text; marker text='SELECT * FROM ordinary'; BEGIN
 SELECT pg_get_viewdef('lending_guarantor_commitment'::regclass,true) INTO definition;
 -- Preserve the prior projection as a named source, then add typed promises without duplicate receivables.
 EXECUTE 'ALTER VIEW lending_guarantor_commitment RENAME TO lending_guarantor_ordinary_commitment';
END $$;
CREATE VIEW lending_guarantor_commitment AS
 SELECT guarantor_id,loan_id,sacco_id,station_id,max(principal_amount) principal_amount,
 CASE WHEN bool_or(amount_cap IS NULL) THEN NULL ELSE max(amount_cap) END amount_cap
 FROM (
  SELECT * FROM lending_guarantor_ordinary_commitment
  UNION ALL SELECT g.client_id,g.loan_id,g.sacco_id,g.station_id,l.amount,g.amount_cap
   FROM lending_group_personal_commitment g JOIN loan_applications l ON l.id=g.loan_id
 ) commitments GROUP BY guarantor_id,loan_id,sacco_id,station_id;

CREATE FUNCTION lending_personal_capacity(client uuid,institution text,branch text,target uuid,cap numeric) RETURNS void LANGUAGE plpgsql AS $$
DECLARE budget numeric;reserved numeric;unknown integer;
BEGIN
 IF NOT lending_guarantor_capacity_lock(client,institution,branch) THEN RAISE EXCEPTION 'Current scoped guarantor required'; END IF;
 IF EXISTS(SELECT 1 FROM lending_group_personal_commitment WHERE client_id=client AND sacco_id=institution AND station_id<>branch)
 THEN RAISE EXCEPTION 'Unresolved guarantor branch exposure'; END IF;
 SELECT min(capacity) INTO budget FROM lending_group_personal_commitment WHERE client_id=client AND sacco_id=institution AND station_id=branch;
 IF budget IS NULL THEN RETURN; END IF;
 SELECT coalesce(sum(amount_cap),0),count(*) FILTER(WHERE amount_cap IS NULL) INTO reserved,unknown
 FROM lending_guarantor_commitment WHERE guarantor_id=client AND sacco_id=institution AND station_id=branch AND loan_id<>target;
 IF unknown>0 OR cap IS NULL OR reserved+cap>budget THEN RAISE EXCEPTION 'Verified personal guarantee capacity exceeded'; END IF;
END $$;

CREATE FUNCTION lending_personal_guarantees_ready(application uuid) RETURNS boolean LANGUAGE sql STABLE AS $$
 SELECT coalesce(l.required_guarantors,0)>0 AND
  (SELECT count(DISTINCT g.client_id) FROM lending_group_personal_guarantee g JOIN lending_group_personal_decision d ON d.offer_id=g.id AND d.decision='ACCEPTED'
   WHERE g.loan_id=l.id AND g.sacco_id=l.sacco_id AND g.station_id=l.station_id)>=l.required_guarantors
 FROM loan_applications l WHERE l.id=application AND l.applicant_member_id IS NULL;
$$;

CREATE FUNCTION lending_personal_offer_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE l loan_applications;c lending_group_cycle;m members;limit_amount numeric;
BEGIN
 SELECT * INTO l FROM loan_applications WHERE id=NEW.loan_id AND sacco_id=NEW.sacco_id AND station_id=NEW.station_id FOR UPDATE;
 SELECT * INTO c FROM lending_group_cycle WHERE id=NEW.cycle_id AND sacco_id=NEW.sacco_id AND station_id=NEW.station_id;
 IF l.id IS NULL OR c.id IS NULL OR l.applicant_member_id IS NOT NULL OR coalesce(l.required_guarantors,0)<=0 OR l.status<>'AWAITING_GUARANTORS' OR c.state<>'SUBMITTED'
  OR NOT EXISTS(SELECT 1 FROM (SELECT cycle_id,loan_id FROM lending_group_facility UNION ALL SELECT cycle_id,loan_id FROM lending_group_shared_facility) f WHERE f.cycle_id=c.id AND f.loan_id=l.id)
 THEN RAISE EXCEPTION 'Personal guarantee requires its waiting typed group loan'; END IF;
 IF EXISTS(SELECT 1 FROM lending_group_shared_allocation WHERE cycle_id=c.id AND client_id=NEW.client_id)
 THEN RAISE EXCEPTION 'A joint borrower cannot separately guarantee its own debt'; END IF;
 IF NOT lending_guarantor_capacity_lock(NEW.client_id,NEW.sacco_id,NEW.station_id) THEN RAISE EXCEPTION 'Scoped active guarantor required'; END IF;
 SELECT * INTO m FROM members WHERE id=NEW.client_id;
 SELECT max_guarantee INTO limit_amount FROM lending_group_policy WHERE id=c.policy_id;
 IF NEW.client_number IS DISTINCT FROM m.member_no OR NEW.client_name IS DISTINCT FROM m.full_name
  OR NEW.amount_cap IS DISTINCT FROM l.amount+(l.financial_snapshot->>'interestAmount')::numeric+(l.financial_snapshot->>'recordedChargeTotal')::numeric
  OR NEW.snapshot->'financial' IS DISTINCT FROM l.financial_snapshot OR NEW.amount_cap>limit_amount
  OR NEW.valid_through<(NEW.recorded_at AT TIME ZONE 'Africa/Dar_es_Salaam')::date
  OR NOT EXISTS(SELECT 1 FROM members WHERE id=NEW.recorded_by AND sacco_id=NEW.sacco_id AND station_id=NEW.station_id AND status='ACTIVE')
  OR EXISTS(SELECT 1 FROM lending_group_personal_commitment g WHERE g.loan_id=l.id AND g.client_id=NEW.client_id)
  OR (SELECT count(*) FROM lending_group_personal_commitment WHERE loan_id=l.id)>=50
 THEN RAISE EXCEPTION 'Personal guarantee must retain exact assessed liability and identity'; END IF;
 -- Include the proposed budget, before this row becomes visible in the commitment view.
 IF EXISTS(SELECT 1 FROM lending_group_personal_commitment WHERE client_id=NEW.client_id AND sacco_id=NEW.sacco_id AND station_id<>NEW.station_id)
  OR EXISTS(SELECT 1 FROM lending_guarantor_commitment WHERE guarantor_id=NEW.client_id AND sacco_id=NEW.sacco_id AND station_id=NEW.station_id AND amount_cap IS NULL)
  OR (SELECT coalesce(sum(amount_cap),0) FROM lending_guarantor_commitment WHERE guarantor_id=NEW.client_id AND sacco_id=NEW.sacco_id AND station_id=NEW.station_id)+NEW.amount_cap>NEW.capacity
 THEN RAISE EXCEPTION 'Personal guarantee cannot exceed verified available capacity'; END IF;
 PERFORM lending_personal_capacity(NEW.client_id,NEW.sacco_id,NEW.station_id,NEW.loan_id,NEW.amount_cap);
 RETURN NEW;
END $$;
CREATE TRIGGER personal_offer_guard BEFORE INSERT ON lending_group_personal_guarantee FOR EACH ROW EXECUTE FUNCTION lending_personal_offer_guard();

CREATE FUNCTION lending_personal_evidence_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE g lending_group_personal_guarantee;l loan_applications;m members;q lending_group_personal_challenge;
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Personal guarantee challenges are retained'; END IF;
 SELECT * INTO g FROM lending_group_personal_guarantee WHERE id=NEW.offer_id;
 SELECT * INTO l FROM loan_applications WHERE id=g.loan_id FOR UPDATE;
 IF TG_TABLE_NAME='lending_group_personal_challenge' AND TG_OP='UPDATE' THEN
  IF ROW(NEW.id,NEW.offer_id,NEW.client_id,NEW.sacco_id,NEW.station_id,NEW.packet_hash,NEW.code_hash,NEW.issued_at,NEW.expires_at)
   IS DISTINCT FROM ROW(OLD.id,OLD.offer_id,OLD.client_id,OLD.sacco_id,OLD.station_id,OLD.packet_hash,OLD.code_hash,OLD.issued_at,OLD.expires_at)
   OR NEW.attempts<OLD.attempts OR NEW.attempts>OLD.attempts+1
   OR OLD.delivered_at IS NOT NULL AND NEW.delivered_at IS DISTINCT FROM OLD.delivered_at
   OR OLD.consumed_at IS NOT NULL AND NEW.consumed_at IS DISTINCT FROM OLD.consumed_at
  THEN RAISE EXCEPTION 'Only bounded challenge progress may change'; END IF;
  RETURN NEW;
 END IF;
 SELECT * INTO m FROM members WHERE id=NEW.client_id AND sacco_id=g.sacco_id AND station_id=g.station_id AND status='ACTIVE' AND is_member;
 IF m.id IS NULL OR NEW.client_id<>g.client_id OR NEW.packet_hash<>g.packet_hash THEN RAISE EXCEPTION 'Personal guarantee evidence owner mismatch'; END IF;
 IF TG_TABLE_NAME='lending_group_personal_opened' THEN RETURN NEW; END IF;
 IF l.status<>'AWAITING_GUARANTORS' OR EXISTS(SELECT 1 FROM lending_group_personal_decision WHERE offer_id=g.id)
 THEN RAISE EXCEPTION 'Personal guarantee decision is closed'; END IF;
 IF TG_TABLE_NAME='lending_group_personal_decision' THEN
  IF NEW.decision='CANCELLED' THEN
   IF NOT EXISTS(SELECT 1 FROM members WHERE id=NEW.actor_id AND sacco_id=g.sacco_id AND station_id=g.station_id AND status='ACTIVE') THEN RAISE EXCEPTION 'Scoped staff cancellation required'; END IF;
  ELSE
   IF NEW.actor_id<>g.client_id THEN RAISE EXCEPTION 'Only the named guarantor can decide'; END IF;
   IF NEW.decision='ACCEPTED' THEN
    IF NEW.signature_text IS DISTINCT FROM m.signature_text OR length(trim(coalesce(NEW.signature_text,'')))=0
     OR NEW.decided_at<g.recorded_at OR (NEW.decided_at AT TIME ZONE 'Africa/Dar_es_Salaam')::date>g.valid_through
     OR NOT EXISTS(SELECT 1 FROM lending_group_personal_opened WHERE offer_id=g.id AND client_id=m.id AND opened_at<=NEW.decided_at)
    THEN RAISE EXCEPTION 'Personal guarantee requires its opened document and current signature'; END IF;
    IF NEW.otp_required THEN
     SELECT * INTO q FROM lending_group_personal_challenge WHERE id=NEW.challenge_id;
     IF q.offer_id IS DISTINCT FROM g.id OR q.client_id IS DISTINCT FROM m.id OR q.packet_hash IS DISTINCT FROM g.packet_hash
      OR q.delivered_at IS NULL OR q.consumed_at IS DISTINCT FROM NEW.decided_at OR q.expires_at<NEW.decided_at OR q.attempts>5
     THEN RAISE EXCEPTION 'Personal guarantee requires its delivered consumed challenge'; END IF;
    ELSIF NEW.challenge_id IS NOT NULL THEN RAISE EXCEPTION 'Unexpected guarantee challenge'; END IF;
   END IF;
  END IF;
  IF NEW.decision<>'ACCEPTED' AND length(trim(NEW.reason))=0 THEN RAISE EXCEPTION 'Refusal or cancellation reason required'; END IF;
 ELSE
  IF NEW.sacco_id<>g.sacco_id OR NEW.station_id<>g.station_id OR NEW.issued_at<g.recorded_at THEN RAISE EXCEPTION 'Personal challenge scope mismatch'; END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER personal_decision_guard BEFORE INSERT ON lending_group_personal_decision FOR EACH ROW EXECUTE FUNCTION lending_personal_evidence_guard();
CREATE TRIGGER personal_opened_guard BEFORE INSERT ON lending_group_personal_opened FOR EACH ROW EXECUTE FUNCTION lending_personal_evidence_guard();
CREATE TRIGGER personal_challenge_guard BEFORE INSERT OR UPDATE OR DELETE ON lending_group_personal_challenge FOR EACH ROW EXECUTE FUNCTION lending_personal_evidence_guard();

-- Existing typed contracts never had positive required-guarantor counts; do not infer historical approvals.
DO $$ DECLARE definition text; name text; marker text='a.required_guarantors IS DISTINCT FROM 0'; BEGIN
 FOREACH name IN ARRAY ARRAY['lending_group_facility_commit()','lending_group_shared_validate(uuid)'] LOOP
  SELECT pg_get_functiondef(name::regprocedure) INTO definition;
  IF strpos(definition,marker)=0 THEN RAISE EXCEPTION 'Typed guarantor guard marker missing'; END IF;
  EXECUTE replace(definition,marker,'(a.required_guarantors IS NULL OR a.required_guarantors<0 OR a.required_guarantors>50)');
 END LOOP;
 SELECT pg_get_functiondef('accountant_loan_terms_protect()'::regprocedure) INTO definition;
 marker='IF NEW.status IS DISTINCT FROM OLD.status AND NOT (';
 IF strpos(definition,marker)=0 THEN RAISE EXCEPTION 'Recorded transition marker missing'; END IF;
 EXECUTE replace(definition,marker,marker||$rule$(OLD.applicant_member_id IS NULL AND OLD.required_guarantors>0 AND ((OLD.status='RECORDED' AND NEW.status='AWAITING_GUARANTORS') OR (OLD.status='AWAITING_GUARANTORS' AND NEW.status IN ('READY_FOR_MANAGER','AWAITING_LOAN_OFFICER','AWAITING_CHAIRPERSON','AWAITING_BOARD','AWAITING_CREDIT_COMMITTEE','AWAITING_ACCOUNTANT','READY_FOR_DISBURSEMENT') AND lending_personal_guarantees_ready(OLD.id)))) OR $rule$);
END $$;
CREATE FUNCTION lending_personal_review_guard() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF NEW.status IS DISTINCT FROM OLD.status AND NEW.applicant_member_id IS NULL AND NEW.required_guarantors>0
  AND NEW.status NOT IN ('RECORDED','AWAITING_GUARANTORS','WITHDRAWN','MANAGER_REJECTED','LOAN_OFFICER_REJECTED','CHAIRPERSON_REJECTED','BOARD_REJECTED','CREDIT_COMMITTEE_REJECTED','ACCOUNTANT_REJECTED','REJECTED')
  AND NOT coalesce(lending_personal_guarantees_ready(NEW.id),false)
 THEN RAISE EXCEPTION 'Required personal guarantees must precede review and funding'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER personal_review_guard BEFORE UPDATE ON loan_applications FOR EACH ROW EXECUTE FUNCTION lending_personal_review_guard();

-- Ordinary approvals and reciprocal group promises must respect the same reserved personal budgets.
CREATE FUNCTION lending_personal_existing_capacity_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE l loan_applications;client uuid;cap numeric;
BEGIN
 IF TG_TABLE_NAME='guarantor_requests' THEN
  IF NEW.status<>'APPROVED' OR NEW.guarantor_member_id IS NULL THEN RETURN NEW; END IF;
  SELECT * INTO l FROM loan_applications WHERE id=NEW.loan_application_id;client=NEW.guarantor_member_id;
  cap=l.amount+(l.financial_snapshot->>'interestAmount')::numeric+coalesce((l.financial_snapshot->>'recordedChargeTotal')::numeric,0);
 ELSE
  SELECT * INTO l FROM loan_applications WHERE id=NEW.loan_id;client=NEW.guarantor_id;cap=NEW.amount_cap;
 END IF;
 IF EXISTS(SELECT 1 FROM lending_group_personal_commitment WHERE client_id=client AND sacco_id=l.sacco_id)
 THEN PERFORM lending_personal_capacity(client,l.sacco_id,l.station_id,l.id,cap);END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER personal_ordinary_capacity_guard BEFORE INSERT OR UPDATE ON guarantor_requests FOR EACH ROW EXECUTE FUNCTION lending_personal_existing_capacity_guard();
CREATE TRIGGER personal_group_capacity_guard BEFORE INSERT ON lending_group_guarantee FOR EACH ROW EXECUTE FUNCTION lending_personal_existing_capacity_guard();
