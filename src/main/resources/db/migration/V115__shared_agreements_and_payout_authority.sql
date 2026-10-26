CREATE TABLE lending_group_shared_document (
 id uuid PRIMARY KEY,cycle_id uuid NOT NULL UNIQUE,sacco_id varchar(255) NOT NULL,station_id varchar(255) NOT NULL,
 metadata jsonb NOT NULL,institution_name varchar(255) NOT NULL,filename varchar(200) NOT NULL,
 content_hash varchar(64) NOT NULL,contract_hash varchar(64) NOT NULL DEFAULT '',size_bytes integer NOT NULL CHECK(size_bytes BETWEEN 5 AND 5242880),
 page_count integer NOT NULL CHECK(page_count BETWEEN 1 AND 50),body bytea NOT NULL,
 repayment_rule varchar(32) NOT NULL DEFAULT 'PRO_RATA_REMAINING_V1' CHECK(repayment_rule='PRO_RATA_REMAINING_V1'),
 request_key uuid NOT NULL,payload_hash varchar(64) NOT NULL,recorded_by uuid NOT NULL REFERENCES members(id),recorded_at timestamptz NOT NULL,
 UNIQUE(cycle_id,sacco_id,station_id),FOREIGN KEY(cycle_id) REFERENCES lending_group_shared_facility(cycle_id),
 FOREIGN KEY(cycle_id,sacco_id,station_id) REFERENCES lending_group_cycle(id,sacco_id,station_id)
);
CREATE TRIGGER shared_document_immutable BEFORE UPDATE OR DELETE ON lending_group_shared_document FOR EACH ROW EXECUTE FUNCTION gl_protect_evidence();
CREATE FUNCTION lending_group_shared_document_insert() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE c lending_group_cycle;f lending_group_shared_facility;l loan_applications;
BEGIN
 SELECT * INTO c FROM lending_group_cycle WHERE id=NEW.cycle_id FOR UPDATE;
 PERFORM id FROM lending_group WHERE id=c.group_id FOR SHARE;
 SELECT * INTO f FROM lending_group_shared_facility WHERE cycle_id=c.id;
 SELECT * INTO l FROM loan_applications WHERE id=f.loan_id;
 IF c.state<>'SUBMITTED' OR f.cycle_id IS NULL OR ROW(c.sacco_id,c.station_id) IS DISTINCT FROM ROW(NEW.sacco_id,NEW.station_id)
  OR NOT EXISTS(SELECT 1 FROM lending_group g WHERE g.id=c.group_id AND g.status='ACTIVE')
  OR EXISTS(SELECT 1 FROM loan_ledgers WHERE loan_application_id=l.id) OR EXISTS(SELECT 1 FROM accountant_loan_post WHERE loan_id=l.id)
  OR l.status IN ('WITHDRAWN','REJECTED','MANAGER_REJECTED','BOARD_REJECTED','LOAN_OFFICER_REJECTED','CHAIRPERSON_REJECTED','CREDIT_COMMITTEE_REJECTED','ACCOUNTANT_REJECTED')
  OR NEW.size_bytes<>octet_length(NEW.body) OR NEW.content_hash<>encode(sha256(NEW.body),'hex')
  OR NOT coalesce((NEW.metadata->>'documentChecked')::boolean,false)
  OR NEW.metadata->>'documentLanguage' NOT IN ('EN','SW','BOTH')
  OR length(trim(coalesce(NEW.metadata->>'documentReference','')))=0
  OR length(trim(coalesce(NEW.metadata->>'institutionAddress','')))=0
  OR NOT EXISTS(SELECT 1 FROM members m WHERE m.id=NEW.recorded_by AND m.sacco_id=c.sacco_id AND m.station_id=c.station_id AND m.status='ACTIVE')
 THEN RAISE EXCEPTION 'Full shared agreement requires its scoped unfunded source and exact approved document'; END IF;
 NEW.contract_hash=encode(sha256(convert_to(jsonb_build_object('source',f.snapshot,'liability',f.liability_rule,
   'financial',l.financial_snapshot,'document',NEW.id,'body',NEW.content_hash,'institution',NEW.institution_name,
   'metadata',NEW.metadata,'repayment',NEW.repayment_rule)::text,'UTF8')),'hex');
 RETURN NEW;
END $$;
CREATE TRIGGER shared_document_insert BEFORE INSERT ON lending_group_shared_document FOR EACH ROW EXECUTE FUNCTION lending_group_shared_document_insert();
CREATE FUNCTION lending_group_shared_packet(cycle uuid,client uuid,destination jsonb) RETURNS text LANGUAGE sql STABLE AS $$
 SELECT encode(sha256(convert_to(jsonb_build_object('contract',d.contract_hash,'client',s.client_id,
   'destination',destination,'net',s.net,'liability',s.liability_cap)::text,'UTF8')),'hex')
 FROM lending_group_shared_document d JOIN lending_group_shared_allocation s ON s.cycle_id=d.cycle_id
 WHERE d.cycle_id=cycle AND s.client_id=client
$$;
CREATE TABLE lending_group_shared_opened (
 cycle_id uuid NOT NULL,client_id uuid NOT NULL,sacco_id varchar(255) NOT NULL,station_id varchar(255) NOT NULL,
 packet_hash varchar(64) NOT NULL,opened_at timestamptz NOT NULL,
 PRIMARY KEY(cycle_id,client_id,packet_hash),
 FOREIGN KEY(cycle_id,sacco_id,station_id) REFERENCES lending_group_shared_document(cycle_id,sacco_id,station_id),
 FOREIGN KEY(cycle_id,client_id) REFERENCES lending_group_shared_allocation(cycle_id,client_id)
);
CREATE TRIGGER shared_opened_immutable BEFORE UPDATE OR DELETE ON lending_group_shared_opened FOR EACH ROW EXECUTE FUNCTION gl_protect_evidence();
CREATE TABLE lending_group_shared_payout_challenge (
 id uuid PRIMARY KEY,cycle_id uuid NOT NULL,client_id uuid NOT NULL,sacco_id varchar(255) NOT NULL,station_id varchar(255) NOT NULL,
 packet_hash varchar(64) NOT NULL,code_hash varchar(255) NOT NULL,issued_at timestamptz NOT NULL,expires_at timestamptz NOT NULL,
 delivered_at timestamptz,consumed_at timestamptz,attempts integer NOT NULL DEFAULT 0 CHECK(attempts BETWEEN 0 AND 5),
 CHECK(expires_at>issued_at),CHECK(delivered_at IS NULL OR delivered_at>=issued_at),CHECK(consumed_at IS NULL OR (delivered_at IS NOT NULL AND consumed_at>=delivered_at)),
 FOREIGN KEY(cycle_id,client_id,packet_hash) REFERENCES lending_group_shared_opened(cycle_id,client_id,packet_hash),
 FOREIGN KEY(cycle_id,sacco_id,station_id) REFERENCES lending_group_shared_document(cycle_id,sacco_id,station_id)
);
CREATE INDEX ix_shared_payout_challenge ON lending_group_shared_payout_challenge(cycle_id,client_id,issued_at DESC,id DESC);
CREATE TABLE lending_group_shared_payout (
 cycle_id uuid NOT NULL,client_id uuid NOT NULL,sacco_id varchar(255) NOT NULL,station_id varchar(255) NOT NULL,
 document_id uuid NOT NULL REFERENCES lending_group_shared_document(id),packet_hash varchar(64) NOT NULL,
 destination jsonb NOT NULL,net_amount numeric(18,2) NOT NULL CHECK(net_amount>0),signature_text text NOT NULL CHECK(length(trim(signature_text))>0),
 otp_required boolean NOT NULL,challenge_id uuid UNIQUE REFERENCES lending_group_shared_payout_challenge(id),accepted_at timestamptz NOT NULL,
 PRIMARY KEY(cycle_id,client_id),CHECK(otp_required=(challenge_id IS NOT NULL)),
 FOREIGN KEY(cycle_id,client_id,packet_hash) REFERENCES lending_group_shared_opened(cycle_id,client_id,packet_hash),
 FOREIGN KEY(cycle_id,sacco_id,station_id) REFERENCES lending_group_shared_document(cycle_id,sacco_id,station_id)
);
CREATE TRIGGER shared_payout_immutable BEFORE UPDATE OR DELETE ON lending_group_shared_payout FOR EACH ROW EXECUTE FUNCTION gl_protect_evidence();
CREATE FUNCTION lending_group_shared_payout_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE c lending_group_cycle;s lending_group_shared_allocation;m members;q lending_group_shared_payout_challenge;d lending_group_shared_document;
BEGIN
 IF TG_TABLE_NAME='lending_group_shared_payout_challenge' AND TG_OP IN ('UPDATE','DELETE') THEN
  IF TG_OP='DELETE' OR (to_jsonb(NEW)-ARRAY['attempts','delivered_at','consumed_at']) IS DISTINCT FROM (to_jsonb(OLD)-ARRAY['attempts','delivered_at','consumed_at'])
    OR NEW.attempts<OLD.attempts OR NEW.attempts>OLD.attempts+1 OR (OLD.consumed_at IS NOT NULL AND NEW IS DISTINCT FROM OLD)
    OR (OLD.delivered_at IS NOT NULL AND NEW.delivered_at IS DISTINCT FROM OLD.delivered_at)
  THEN RAISE EXCEPTION 'Shared payout challenge evidence is retained'; END IF;
  RETURN NEW;
 END IF;
 SELECT * INTO c FROM lending_group_cycle WHERE id=NEW.cycle_id FOR SHARE;
 SELECT * INTO s FROM lending_group_shared_allocation WHERE cycle_id=c.id AND client_id=NEW.client_id;
 SELECT * INTO m FROM members WHERE id=NEW.client_id;
 SELECT * INTO d FROM lending_group_shared_document WHERE cycle_id=c.id;
 IF c.state<>'SUBMITTED' OR s.client_id IS NULL OR d.id IS NULL
  OR ROW(NEW.sacco_id,NEW.station_id,m.sacco_id,m.station_id,m.status,m.is_member)
    IS DISTINCT FROM ROW(c.sacco_id,c.station_id,c.sacco_id,c.station_id,'ACTIVE'::varchar,true)
  OR NOT EXISTS(SELECT 1 FROM lending_group_consent_evidence e WHERE e.cycle_id=c.id AND e.client_id=m.id)
 THEN RAISE EXCEPTION 'Only a current scoped joint borrower may authorize their exact payout'; END IF;
 IF TG_TABLE_NAME='lending_group_shared_payout' THEN
  IF NEW.document_id<>d.id OR NEW.net_amount<>s.net OR NEW.signature_text IS DISTINCT FROM m.signature_text
   OR NEW.packet_hash IS DISTINCT FROM lending_group_shared_packet(c.id,m.id,NEW.destination)
   OR NOT coalesce((NEW.destination->>'available')::boolean,false)
   OR NEW.destination->>'destinationType' NOT IN ('BANK_ACCOUNT','MOBILE_MONEY','OTHER')
   OR length(trim(coalesce(NEW.destination->>'provider','')))=0 OR length(trim(coalesce(NEW.destination->>'accountHolderName','')))=0
   OR length(trim(coalesce(NEW.destination->>'accountIdentifier','')))=0
   OR NEW.accepted_at<d.recorded_at
   OR NOT EXISTS(SELECT 1 FROM lending_group_shared_opened o WHERE o.cycle_id=c.id AND o.client_id=m.id AND o.packet_hash=NEW.packet_hash AND o.opened_at<=NEW.accepted_at)
  THEN RAISE EXCEPTION 'Shared signature must bind the full document and exact personal payout'; END IF;
  IF NEW.otp_required THEN
   SELECT * INTO q FROM lending_group_shared_payout_challenge WHERE id=NEW.challenge_id;
   IF ROW(q.cycle_id,q.client_id,q.packet_hash,q.sacco_id,q.station_id,q.consumed_at)
     IS DISTINCT FROM ROW(c.id,m.id,NEW.packet_hash,c.sacco_id,c.station_id,NEW.accepted_at)
     OR q.delivered_at IS NULL OR q.expires_at<NEW.accepted_at
   THEN RAISE EXCEPTION 'Shared payout signature requires its delivered personal challenge'; END IF;
  END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER shared_payout_challenge_guard BEFORE INSERT OR UPDATE OR DELETE ON lending_group_shared_payout_challenge FOR EACH ROW EXECUTE FUNCTION lending_group_shared_payout_guard();
CREATE TRIGGER shared_payout_guard BEFORE INSERT ON lending_group_shared_payout FOR EACH ROW EXECUTE FUNCTION lending_group_shared_payout_guard();
-- Payout authority is evidence, not cash. The joint funding and ledger gates stay closed here.
