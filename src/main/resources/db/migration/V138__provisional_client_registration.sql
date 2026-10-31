-- Client identity is independent of portal credentials. Existing users retain their access.
ALTER TABLE members ADD COLUMN client_portal_enabled boolean NOT NULL DEFAULT true;
ALTER TABLE members ADD CONSTRAINT ck_provisional_client_credentials CHECK
 (client_portal_enabled OR (username IS NULL AND password_hash='!NO_PORTAL_ACCESS'
   AND is_member IS TRUE AND position='MEMBER' AND staff_access_status='NONE'));

-- Pre-application intake never posts money or substitutes for the approved lending workflow.
CREATE TABLE client_onboarding_loan_request (
 id uuid PRIMARY KEY, client_id uuid NOT NULL REFERENCES members(id), sacco_id text NOT NULL, station_id text NOT NULL,
 sequence integer NOT NULL CHECK(sequence>0), mode text NOT NULL CHECK(mode IN ('DRAFT','SUBMITTED')),
 details jsonb NOT NULL CHECK(jsonb_typeof(details)='object'), request_key uuid NOT NULL,
 payload_hash varchar(64) NOT NULL, actor_id uuid NOT NULL REFERENCES members(id), recorded_at timestamptz NOT NULL,
 UNIQUE(client_id,sequence), UNIQUE(sacco_id,station_id,request_key)
);
CREATE INDEX ix_onboarding_loan_scope ON client_onboarding_loan_request(sacco_id,station_id,client_id,sequence DESC);
CREATE FUNCTION client_loan_request_scope_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM members WHERE id=NEW.client_id AND sacco_id=NEW.sacco_id AND station_id=NEW.station_id
   AND status='ACTIVE' AND (is_member OR (is_member IS NULL AND position='MEMBER')))
 OR NOT EXISTS(SELECT 1 FROM members WHERE id=NEW.actor_id AND sacco_id=NEW.sacco_id AND station_id=NEW.station_id AND status='ACTIVE')
 THEN RAISE EXCEPTION 'Client request scope mismatch'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER onboarding_loan_scope BEFORE INSERT ON client_onboarding_loan_request FOR EACH ROW EXECUTE FUNCTION client_loan_request_scope_guard();
CREATE TRIGGER onboarding_loan_immutable BEFORE UPDATE OR DELETE ON client_onboarding_loan_request FOR EACH ROW EXECUTE FUNCTION gl_protect_evidence();
CREATE TRIGGER onboarding_loan_no_truncate BEFORE TRUNCATE ON client_onboarding_loan_request FOR EACH STATEMENT EXECUTE FUNCTION gl_protect_evidence();
