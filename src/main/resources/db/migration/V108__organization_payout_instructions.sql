-- A named borrowing-authority holder confirms an instruction; this is neither funding nor a cash receipt.
CREATE TABLE lending_group_org_payout_challenge (
 id uuid PRIMARY KEY,cycle_id uuid NOT NULL REFERENCES lending_group_org_agreement(cycle_id),
 sacco_id varchar(255) NOT NULL,station_id varchar(255) NOT NULL,client_id uuid NOT NULL REFERENCES members(id),
 packet_hash varchar(64) NOT NULL,code_hash text NOT NULL,issued_at timestamptz NOT NULL,expires_at timestamptz NOT NULL,
 delivered_at timestamptz,consumed_at timestamptz,attempts integer NOT NULL DEFAULT 0 CHECK(attempts BETWEEN 0 AND 5),
 CHECK(expires_at>issued_at AND expires_at<=issued_at+interval '30 minutes')
);
CREATE INDEX ix_group_org_payout_challenge_person ON lending_group_org_payout_challenge(cycle_id,client_id,issued_at DESC,id DESC);
CREATE INDEX ix_group_org_receiver_requests ON lending_group_authority_signer(client_id,authority_id) WHERE may_receive;
CREATE TABLE lending_group_org_payout (
 cycle_id uuid PRIMARY KEY REFERENCES lending_group_org_agreement(cycle_id),sacco_id varchar(255) NOT NULL,station_id varchar(255) NOT NULL,
 client_id uuid NOT NULL REFERENCES members(id),packet_hash varchar(64) NOT NULL,
 principal numeric(18,2) NOT NULL CHECK(principal>0),deductions numeric(18,2) NOT NULL CHECK(deductions>=0),
 net_amount numeric(18,2) NOT NULL CHECK(net_amount>0 AND net_amount=principal-deductions),destination jsonb NOT NULL,
 client_name varchar(255) NOT NULL,client_number varchar(255) NOT NULL,signature_text text NOT NULL CHECK(length(trim(signature_text))>0),
 authorized_at timestamptz NOT NULL,otp_required boolean NOT NULL,challenge_id uuid UNIQUE REFERENCES lending_group_org_payout_challenge(id),
 CHECK(otp_required=(challenge_id IS NOT NULL))
);
CREATE TRIGGER group_org_payout_immutable BEFORE UPDATE OR DELETE ON lending_group_org_payout FOR EACH ROW EXECUTE FUNCTION gl_protect_evidence();
CREATE FUNCTION lending_group_org_payout_scope(cycle uuid,client uuid,institution varchar,branch varchar,at_time timestamptz) RETURNS void LANGUAGE plpgsql AS $$
DECLARE c lending_group_cycle; p lending_group_org_agreement; h lending_group_authority;
BEGIN
 SELECT * INTO c FROM lending_group_cycle WHERE id=cycle FOR SHARE;
 SELECT * INTO p FROM lending_group_org_agreement WHERE cycle_id=cycle;
 PERFORM id FROM lending_group WHERE id=c.group_id FOR SHARE;
 SELECT * INTO h FROM lending_group_authority WHERE id=p.authority_id;
 IF p.cycle_id IS NULL OR c.state<>'SUBMITTED' OR p.sacco_id<>institution OR p.station_id<>branch
  OR p.snapshot->'document' IS NULL OR p.snapshot->'document'='null'::jsonb
  OR at_time<p.prepared_at OR at_time<h.recorded_at OR (at_time AT TIME ZONE 'Africa/Dar_es_Salaam')::date NOT BETWEEN h.valid_from AND h.valid_through
  OR EXISTS(SELECT 1 FROM lending_group_authority_revocation r WHERE r.authority_id=h.id)
  OR EXISTS(SELECT 1 FROM lending_group_authority n WHERE n.group_id=h.group_id AND n.revision>h.revision)
  OR NOT EXISTS(SELECT 1 FROM lending_group g WHERE g.id=c.group_id AND g.status='ACTIVE')
  OR NOT EXISTS(SELECT 1 FROM loan_applications l WHERE l.id=p.loan_id AND l.status='READY_FOR_DISBURSEMENT')
  OR (SELECT count(*) FROM lending_group_org_signature s WHERE s.cycle_id=cycle AND s.signed_at<=at_time)<h.required_signatures
  OR NOT EXISTS(SELECT 1 FROM lending_group_authority_signer s JOIN members m ON m.id=s.client_id
    WHERE s.authority_id=h.id AND s.client_id=client AND s.may_receive AND m.sacco_id=institution AND m.station_id=branch AND m.status='ACTIVE' AND m.is_member)
 THEN RAISE EXCEPTION 'Current named payout authority and complete agreement signatures required'; END IF;
END $$;
CREATE FUNCTION lending_group_org_payout_challenge_guard() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Organization payout challenges are retained'; END IF;
 IF TG_OP='UPDATE' THEN
  IF (OLD.consumed_at IS NOT NULL AND NEW IS DISTINCT FROM OLD)
   OR (to_jsonb(NEW)-ARRAY['delivered_at','consumed_at','attempts']) IS DISTINCT FROM (to_jsonb(OLD)-ARRAY['delivered_at','consumed_at','attempts'])
   OR NEW.attempts<OLD.attempts OR NEW.attempts>OLD.attempts+1
   OR (OLD.delivered_at IS NOT NULL AND NEW.delivered_at IS DISTINCT FROM OLD.delivered_at)
   OR (NEW.delivered_at IS NOT NULL AND (NEW.delivered_at<NEW.issued_at OR NEW.delivered_at>=NEW.expires_at))
   OR (NEW.consumed_at IS NOT NULL AND (NEW.delivered_at IS NULL OR NEW.consumed_at<NEW.delivered_at OR NEW.consumed_at>=NEW.expires_at))
  THEN RAISE EXCEPTION 'Organization payout challenge evidence cannot be rewritten'; END IF;RETURN NEW;
 END IF;
 PERFORM lending_group_org_payout_scope(NEW.cycle_id,NEW.client_id,NEW.sacco_id,NEW.station_id,NEW.issued_at);
 IF NEW.attempts<>0 OR NEW.delivered_at IS NOT NULL OR NEW.consumed_at IS NOT NULL
  OR EXISTS(SELECT 1 FROM lending_group_org_payout WHERE cycle_id=NEW.cycle_id)
  OR NEW.packet_hash IS DISTINCT FROM (SELECT packet_hash FROM lending_group_org_agreement WHERE cycle_id=NEW.cycle_id)
 THEN RAISE EXCEPTION 'Payout challenge must bind the current unconfirmed instruction'; END IF;RETURN NEW;
END $$;
CREATE TRIGGER group_org_payout_challenge_guard BEFORE INSERT OR UPDATE OR DELETE ON lending_group_org_payout_challenge FOR EACH ROW EXECUTE FUNCTION lending_group_org_payout_challenge_guard();
CREATE FUNCTION lending_group_org_payout_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE p lending_group_org_agreement; m members;
BEGIN
 PERFORM lending_group_org_payout_scope(NEW.cycle_id,NEW.client_id,NEW.sacco_id,NEW.station_id,NEW.authorized_at);
 SELECT * INTO p FROM lending_group_org_agreement WHERE cycle_id=NEW.cycle_id;
 SELECT * INTO m FROM members WHERE id=NEW.client_id;
 IF NEW.packet_hash IS DISTINCT FROM p.packet_hash OR NEW.destination IS DISTINCT FROM p.snapshot->'payout'
  OR NEW.principal IS DISTINCT FROM (p.snapshot->'loan'->>'principal')::numeric
  OR NEW.deductions IS DISTINCT FROM (p.snapshot->'financial'->>'recordedChargeDeductions')::numeric
  OR NEW.net_amount IS DISTINCT FROM (p.snapshot->'financial'->>'recordedNetCashEstimate')::numeric
  OR NEW.client_name IS DISTINCT FROM m.full_name OR NEW.client_number IS DISTINCT FROM m.member_no
  OR NEW.signature_text IS DISTINCT FROM m.signature_text
  OR (NEW.otp_required AND NOT EXISTS(SELECT 1 FROM lending_group_org_payout_challenge x WHERE x.id=NEW.challenge_id
    AND x.cycle_id=NEW.cycle_id AND x.client_id=NEW.client_id AND x.sacco_id=NEW.sacco_id AND x.station_id=NEW.station_id
    AND x.packet_hash=NEW.packet_hash AND x.delivered_at IS NOT NULL AND x.consumed_at=NEW.authorized_at AND x.attempts BETWEEN 1 AND 5))
 THEN RAISE EXCEPTION 'Payout authorization must bind its exact frozen destination, money and named person'; END IF;RETURN NEW;
END $$;
CREATE TRIGGER group_org_payout_guard BEFORE INSERT ON lending_group_org_payout FOR EACH ROW EXECUTE FUNCTION lending_group_org_payout_guard();
CREATE FUNCTION lending_group_org_payout_challenge_commit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF NEW.consumed_at IS NOT NULL AND NOT EXISTS(SELECT 1 FROM lending_group_org_payout p WHERE p.challenge_id=NEW.id
  AND p.cycle_id=NEW.cycle_id AND p.client_id=NEW.client_id AND p.packet_hash=NEW.packet_hash AND p.authorized_at=NEW.consumed_at)
 THEN RAISE EXCEPTION 'Payout code consumption must commit with its instruction'; END IF;RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER group_org_payout_challenge_commit AFTER UPDATE ON lending_group_org_payout_challenge DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION lending_group_org_payout_challenge_commit();
