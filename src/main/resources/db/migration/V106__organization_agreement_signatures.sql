-- Organization acceptance is representation of the borrower, never a personal loan or guarantee.
CREATE TABLE lending_group_org_agreement (
 cycle_id uuid PRIMARY KEY REFERENCES lending_group_facility(cycle_id),loan_id uuid NOT NULL UNIQUE,
 sacco_id varchar(255) NOT NULL,station_id varchar(255) NOT NULL,authority_id uuid NOT NULL REFERENCES lending_group_authority(id),
 snapshot jsonb NOT NULL,packet_hash varchar(64) NOT NULL CHECK(packet_hash ~ '^[0-9a-f]{64}$'),
 prepared_by uuid REFERENCES members(id),prepared_at timestamptz NOT NULL,
 FOREIGN KEY(loan_id,sacco_id,station_id) REFERENCES loan_applications(id,sacco_id,station_id)
);
CREATE TRIGGER group_org_agreement_immutable BEFORE UPDATE OR DELETE ON lending_group_org_agreement FOR EACH ROW EXECUTE FUNCTION gl_protect_evidence();
CREATE FUNCTION lending_group_org_document(cycle uuid) RETURNS jsonb LANGUAGE sql STABLE AS $$
 SELECT jsonb_build_object(
  'organization',jsonb_build_object('partyId',l.borrower_party_id,'groupId',f.group_id,'name',h.group_name,'code',h.group_code,'legalForm',h.legal_form),
  'authority',jsonb_build_object('id',h.id,'revision',h.revision,'validFrom',h.valid_from,'validThrough',h.valid_through,
    'maxPrincipal',h.max_principal,'requiredSignatures',h.required_signatures,'capacityEvidence',h.capacity_evidence,'resolutionEvidence',h.resolution_evidence),
  'loan',jsonb_build_object('id',l.id,'number',l.application_number::text,'product',r.product_name,'principal',l.amount,
    'tenorMonths',l.tenor_months,'frequency',l.repayment_frequency,'firstPaymentDate',l.first_repayment_date,'purpose',l.form_data->>'purpose',
    'agreementReference',p.agreement_reference,'policyReference',p.policy_reference),
  'financial',l.financial_snapshot,'payout',l.payment_details_snapshot
 ) FROM lending_group_facility f JOIN loan_applications l ON l.id=f.loan_id JOIN accountant_loan_record r ON r.loan_id=l.id
 JOIN lending_group_cycle c ON c.id=f.cycle_id JOIN lending_group_policy p ON p.id=c.policy_id
 JOIN lending_group_authority h ON h.id=f.authority_id WHERE f.cycle_id=cycle AND f.model='ORGANIZATION';
$$;
CREATE FUNCTION lending_group_org_agreement_guard() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF NOT EXISTS(SELECT 1 FROM lending_group_facility f JOIN lending_group_cycle c ON c.id=f.cycle_id
   WHERE f.cycle_id=NEW.cycle_id AND ROW(f.loan_id,f.sacco_id,f.station_id,f.authority_id) IS NOT DISTINCT FROM ROW(NEW.loan_id,NEW.sacco_id,NEW.station_id,NEW.authority_id)
   AND c.state IN ('DRAFT','SUBMITTED') AND (NEW.prepared_by IS NULL OR NEW.prepared_by=f.created_by)
   AND NOT EXISTS(SELECT 1 FROM loan_ledgers b WHERE b.loan_application_id=f.loan_id))
  OR NEW.snapshot IS DISTINCT FROM lending_group_org_document(NEW.cycle_id)
  OR NEW.packet_hash IS DISTINCT FROM encode(sha256(convert_to(NEW.snapshot::text,'UTF8')),'hex')
 THEN RAISE EXCEPTION 'Organization agreement must be the canonical scoped loan contract'; END IF;RETURN NEW;
END $$;
CREATE TRIGGER group_org_agreement_guard BEFORE INSERT ON lending_group_org_agreement FOR EACH ROW EXECUTE FUNCTION lending_group_org_agreement_guard();
CREATE FUNCTION lending_group_org_agreement_prepare(cycle uuid,prepared timestamptz,actor uuid) RETURNS void LANGUAGE plpgsql AS $$
DECLARE document jsonb; f lending_group_facility;
BEGIN
 SELECT * INTO f FROM lending_group_facility WHERE cycle_id=cycle;
 IF NOT FOUND THEN RAISE EXCEPTION 'Organization facility unavailable'; END IF;
 document=lending_group_org_document(cycle);
 IF document IS NULL THEN RAISE EXCEPTION 'Organization recorded contract unavailable'; END IF;
 INSERT INTO lending_group_org_agreement(cycle_id,loan_id,sacco_id,station_id,authority_id,snapshot,packet_hash,prepared_by,prepared_at)
 VALUES(f.cycle_id,f.loan_id,f.sacco_id,f.station_id,f.authority_id,document,encode(sha256(convert_to(document::text,'UTF8')),'hex'),actor,prepared);
END $$;
CREATE FUNCTION lending_group_org_agreement_trigger() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 PERFORM lending_group_org_agreement_prepare(NEW.cycle_id,NEW.created_at,NEW.created_by);RETURN NEW;
END $$;
CREATE TRIGGER group_org_agreement_prepare AFTER INSERT ON lending_group_facility FOR EACH ROW EXECUTE FUNCTION lending_group_org_agreement_trigger();
-- Earlier unfunded applications receive a newly generated document, not historical approval/signature evidence.
DO $$ DECLARE f record; BEGIN
 FOR f IN SELECT x.cycle_id FROM lending_group_facility x JOIN lending_group_cycle c ON c.id=x.cycle_id
  WHERE c.state='SUBMITTED' AND NOT EXISTS(SELECT 1 FROM loan_ledgers b WHERE b.loan_application_id=x.loan_id)
 LOOP PERFORM lending_group_org_agreement_prepare(f.cycle_id,transaction_timestamp(),NULL); END LOOP;
END $$;
CREATE TABLE lending_group_org_challenge (
 id uuid PRIMARY KEY,cycle_id uuid NOT NULL REFERENCES lending_group_org_agreement(cycle_id),sacco_id varchar(255) NOT NULL,station_id varchar(255) NOT NULL,
 client_id uuid NOT NULL REFERENCES members(id),packet_hash varchar(64) NOT NULL,code_hash text NOT NULL,
 issued_at timestamptz NOT NULL,expires_at timestamptz NOT NULL,delivered_at timestamptz,consumed_at timestamptz,
 attempts integer NOT NULL DEFAULT 0 CHECK(attempts BETWEEN 0 AND 5),CHECK(expires_at>issued_at)
);
CREATE INDEX ix_group_org_challenge_person ON lending_group_org_challenge(cycle_id,client_id,issued_at DESC,id DESC);
CREATE INDEX ix_group_org_signer_requests ON lending_group_authority_signer(client_id,authority_id) WHERE may_sign;
CREATE TABLE lending_group_org_signature (
 cycle_id uuid NOT NULL REFERENCES lending_group_org_agreement(cycle_id),sacco_id varchar(255) NOT NULL,station_id varchar(255) NOT NULL,
 client_id uuid NOT NULL REFERENCES members(id),packet_hash varchar(64) NOT NULL,
 signature_text text NOT NULL CHECK(length(trim(signature_text))>0),signed_at timestamptz NOT NULL,
 otp_required boolean NOT NULL,challenge_id uuid UNIQUE REFERENCES lending_group_org_challenge(id),
 PRIMARY KEY(cycle_id,client_id),CHECK(otp_required=(challenge_id IS NOT NULL))
);
CREATE TRIGGER group_org_signature_immutable BEFORE UPDATE OR DELETE ON lending_group_org_signature FOR EACH ROW EXECUTE FUNCTION gl_protect_evidence();
CREATE FUNCTION lending_group_org_signing_scope(cycle uuid,client uuid,institution varchar,branch varchar) RETURNS void LANGUAGE plpgsql AS $$
DECLARE a lending_group_org_agreement; c lending_group_cycle; h lending_group_authority;
BEGIN
 SELECT * INTO c FROM lending_group_cycle WHERE id=cycle FOR SHARE;
 SELECT * INTO a FROM lending_group_org_agreement WHERE cycle_id=cycle;
 PERFORM id FROM lending_group WHERE id=c.group_id FOR SHARE;
 SELECT * INTO h FROM lending_group_authority WHERE id=a.authority_id;
 IF a.cycle_id IS NULL OR c.state<>'SUBMITTED' OR a.sacco_id<>institution OR a.station_id<>branch
  OR EXISTS(SELECT 1 FROM lending_group_authority_revocation r WHERE r.authority_id=h.id)
  OR EXISTS(SELECT 1 FROM lending_group_authority n WHERE n.group_id=h.group_id AND n.revision>h.revision)
  OR NOT EXISTS(SELECT 1 FROM lending_group g WHERE g.id=c.group_id AND g.status='ACTIVE')
  OR NOT EXISTS(SELECT 1 FROM lending_group_authority_signer s JOIN members m ON m.id=s.client_id
    WHERE s.authority_id=h.id AND s.client_id=client AND s.may_sign AND m.sacco_id=institution AND m.station_id=branch AND m.status='ACTIVE' AND m.is_member)
  OR EXISTS(SELECT 1 FROM loan_applications l WHERE l.id=a.loan_id AND l.status IN ('WITHDRAWN','DISBURSED','PAR','DEFAULTED','WRITTEN_OFF','PAID','DISBURSEMENT_REVERSED','MANAGER_REJECTED','LOAN_OFFICER_REJECTED','CHAIRPERSON_REJECTED','BOARD_REJECTED','CREDIT_COMMITTEE_REJECTED','ACCOUNTANT_REJECTED','REJECTED'))
 THEN RAISE EXCEPTION 'Current named organization signing authority required'; END IF;
END $$;
CREATE FUNCTION lending_group_org_challenge_guard() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Organization signing challenges are retained'; END IF;
 IF TG_OP='UPDATE' THEN
  IF (OLD.consumed_at IS NOT NULL AND NEW IS DISTINCT FROM OLD)
   OR (to_jsonb(NEW)-ARRAY['delivered_at','consumed_at','attempts']) IS DISTINCT FROM (to_jsonb(OLD)-ARRAY['delivered_at','consumed_at','attempts'])
   OR NEW.attempts<OLD.attempts OR NEW.attempts>OLD.attempts+1
   OR (OLD.delivered_at IS NOT NULL AND NEW.delivered_at IS DISTINCT FROM OLD.delivered_at)
   OR (OLD.consumed_at IS NOT NULL AND NEW.consumed_at IS DISTINCT FROM OLD.consumed_at)
   OR (NEW.delivered_at IS NOT NULL AND (NEW.delivered_at<NEW.issued_at OR NEW.delivered_at>=NEW.expires_at))
   OR (NEW.consumed_at IS NOT NULL AND (NEW.delivered_at IS NULL OR NEW.consumed_at<NEW.delivered_at OR NEW.consumed_at>=NEW.expires_at))
  THEN RAISE EXCEPTION 'Organization challenge evidence cannot be rewritten'; END IF;RETURN NEW;
 END IF;
 PERFORM lending_group_org_signing_scope(NEW.cycle_id,NEW.client_id,NEW.sacco_id,NEW.station_id);
 IF NEW.attempts<>0 OR NEW.delivered_at IS NOT NULL OR NEW.consumed_at IS NOT NULL
  OR NEW.packet_hash IS DISTINCT FROM (SELECT packet_hash FROM lending_group_org_agreement WHERE cycle_id=NEW.cycle_id)
 THEN RAISE EXCEPTION 'Organization challenge must bind the frozen contract'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER group_org_challenge_guard BEFORE INSERT OR UPDATE OR DELETE ON lending_group_org_challenge FOR EACH ROW EXECUTE FUNCTION lending_group_org_challenge_guard();
CREATE FUNCTION lending_group_org_signature_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE a lending_group_org_agreement; h lending_group_authority;
BEGIN
 PERFORM lending_group_org_signing_scope(NEW.cycle_id,NEW.client_id,NEW.sacco_id,NEW.station_id);
 SELECT * INTO a FROM lending_group_org_agreement WHERE cycle_id=NEW.cycle_id;
 SELECT * INTO h FROM lending_group_authority WHERE id=a.authority_id;
 IF NEW.packet_hash IS DISTINCT FROM a.packet_hash OR NEW.signed_at<a.prepared_at
  OR (NEW.signed_at AT TIME ZONE 'Africa/Dar_es_Salaam')::date NOT BETWEEN h.valid_from AND h.valid_through
  OR NEW.signature_text IS DISTINCT FROM (SELECT signature_text FROM members WHERE id=NEW.client_id)
  OR (NEW.otp_required AND NOT EXISTS(SELECT 1 FROM lending_group_org_challenge x WHERE x.id=NEW.challenge_id
    AND x.cycle_id=NEW.cycle_id AND x.client_id=NEW.client_id AND x.sacco_id=NEW.sacco_id AND x.station_id=NEW.station_id
    AND x.packet_hash=NEW.packet_hash AND x.delivered_at IS NOT NULL AND x.consumed_at=NEW.signed_at AND x.attempts BETWEEN 1 AND 5))
 THEN RAISE EXCEPTION 'Organization signature must bind its named person, contract and delivered code'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER group_org_signature_guard BEFORE INSERT ON lending_group_org_signature FOR EACH ROW EXECUTE FUNCTION lending_group_org_signature_guard();
CREATE FUNCTION lending_group_org_challenge_commit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF NEW.consumed_at IS NOT NULL AND NOT EXISTS(SELECT 1 FROM lending_group_org_signature s WHERE s.challenge_id=NEW.id
  AND s.cycle_id=NEW.cycle_id AND s.client_id=NEW.client_id AND s.packet_hash=NEW.packet_hash AND s.signed_at=NEW.consumed_at)
 THEN RAISE EXCEPTION 'Organization code consumption must commit with its signature'; END IF;RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER group_org_challenge_commit AFTER UPDATE ON lending_group_org_challenge DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION lending_group_org_challenge_commit();
