-- Preserve earlier checkbox events; only evidenced personal signatures authorize future funding.
CREATE TABLE lending_group_consent_challenge (
 id uuid PRIMARY KEY,cycle_id uuid NOT NULL,sacco_id varchar(255) NOT NULL,station_id varchar(255) NOT NULL,
 client_id uuid NOT NULL REFERENCES members(id),code_hash varchar(255) NOT NULL,
 issued_at timestamptz NOT NULL,expires_at timestamptz NOT NULL,delivered_at timestamptz,consumed_at timestamptz,
 attempts integer NOT NULL DEFAULT 0 CHECK(attempts BETWEEN 0 AND 5),
 CHECK(expires_at>issued_at),CHECK(delivered_at IS NULL OR delivered_at>=issued_at),
 CHECK(consumed_at IS NULL OR delivered_at IS NOT NULL),
 FOREIGN KEY(cycle_id,sacco_id,station_id) REFERENCES lending_group_cycle(id,sacco_id,station_id)
);
CREATE INDEX ix_group_consent_challenge ON lending_group_consent_challenge(cycle_id,client_id,issued_at DESC,id DESC);
CREATE TABLE lending_group_consent_evidence (
 cycle_id uuid NOT NULL,sacco_id varchar(255) NOT NULL,station_id varchar(255) NOT NULL,client_id uuid NOT NULL,
 policy_id uuid NOT NULL REFERENCES lending_group_policy(id),signature_text text NOT NULL CHECK(length(trim(signature_text))>0),
 signed_at timestamptz NOT NULL,otp_required boolean NOT NULL,challenge_id uuid UNIQUE REFERENCES lending_group_consent_challenge(id),
 PRIMARY KEY(cycle_id,client_id),CHECK(otp_required=(challenge_id IS NOT NULL)),
 FOREIGN KEY(cycle_id,client_id) REFERENCES lending_group_consent(cycle_id,client_id),
 FOREIGN KEY(cycle_id,sacco_id,station_id) REFERENCES lending_group_cycle(id,sacco_id,station_id)
);
CREATE TRIGGER lending_group_consent_evidence_immutable BEFORE UPDATE OR DELETE ON lending_group_consent_evidence FOR EACH ROW EXECUTE FUNCTION gl_protect_evidence();
CREATE FUNCTION lending_group_signed_consent_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE c lending_group_cycle; challenge lending_group_consent_challenge;
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Group signature evidence is retained'; END IF;
 IF TG_TABLE_NAME='lending_group_consent_challenge' AND TG_OP='UPDATE' THEN
  IF (to_jsonb(NEW)-ARRAY['delivered_at','consumed_at','attempts']) IS DISTINCT FROM (to_jsonb(OLD)-ARRAY['delivered_at','consumed_at','attempts'])
    OR NEW.attempts<OLD.attempts OR NEW.attempts>OLD.attempts+1
    OR (OLD.delivered_at IS NOT NULL AND NEW.delivered_at IS DISTINCT FROM OLD.delivered_at)
    OR (OLD.consumed_at IS NOT NULL AND NEW IS DISTINCT FROM OLD)
  THEN RAISE EXCEPTION 'Group challenges retain identity and verification history'; END IF;
  RETURN NEW;
 END IF;
 SELECT * INTO c FROM lending_group_cycle WHERE id=NEW.cycle_id;
 IF c.state<>'SUBMITTED' OR c.sacco_id<>NEW.sacco_id OR c.station_id<>NEW.station_id
   OR NOT EXISTS(SELECT 1 FROM lending_group_participant p WHERE p.cycle_id=c.id AND p.client_id=NEW.client_id)
 THEN RAISE EXCEPTION 'Only frozen participants may sign group terms'; END IF;
 IF TG_TABLE_NAME='lending_group_consent_evidence' THEN
  IF NEW.policy_id<>c.policy_id OR NOT EXISTS(SELECT 1 FROM members m WHERE m.id=NEW.client_id AND m.sacco_id=NEW.sacco_id AND m.station_id=NEW.station_id AND m.status='ACTIVE' AND m.is_member AND m.signature_text=NEW.signature_text)
  THEN RAISE EXCEPTION 'Group signatures require the current personal signature'; END IF;
  IF NEW.otp_required THEN
   SELECT * INTO challenge FROM lending_group_consent_challenge WHERE id=NEW.challenge_id;
   IF challenge.cycle_id<>NEW.cycle_id OR challenge.client_id<>NEW.client_id OR challenge.sacco_id<>NEW.sacco_id OR challenge.station_id<>NEW.station_id
     OR challenge.delivered_at IS NULL OR challenge.consumed_at IS DISTINCT FROM NEW.signed_at OR challenge.expires_at<NEW.signed_at
   THEN RAISE EXCEPTION 'Group signature requires its delivered, consumed challenge'; END IF;
  END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER group_consent_challenge_guard BEFORE INSERT OR UPDATE OR DELETE ON lending_group_consent_challenge FOR EACH ROW EXECUTE FUNCTION lending_group_signed_consent_guard();
CREATE TRIGGER group_signed_consent_guard BEFORE INSERT ON lending_group_consent_evidence FOR EACH ROW EXECUTE FUNCTION lending_group_signed_consent_guard();
CREATE OR REPLACE FUNCTION lending_group_funding_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE c lending_group_cycle;
BEGIN
 IF NEW.status='DISBURSED' AND OLD.status IS DISTINCT FROM NEW.status THEN
  SELECT g.* INTO c FROM lending_group_cycle g JOIN lending_group_participant x ON x.cycle_id=g.id WHERE x.loan_id=NEW.id;
  IF FOUND AND (NEW.first_repayment_date IS DISTINCT FROM OLD.first_repayment_date OR c.state<>'SUBMITTED' OR EXISTS(SELECT 1 FROM lending_group_participant x WHERE x.cycle_id=c.id AND NOT EXISTS(
   SELECT 1 FROM lending_group_consent_evidence consent WHERE consent.cycle_id=c.id AND consent.client_id=x.client_id)))
  THEN RAISE EXCEPTION 'All group participants must sign frozen liabilities before funding'; END IF;
 END IF;
 RETURN NEW;
END $$;
