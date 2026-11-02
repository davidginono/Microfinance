-- Immutable recording commands link assisted/OTP recording to the normal loan application.
CREATE TABLE client_application_command (
 id uuid PRIMARY KEY,application_id uuid NOT NULL REFERENCES loan_applications(id),client_id uuid NOT NULL REFERENCES members(id),
 sacco_id text NOT NULL,station_id text NOT NULL,version integer NOT NULL CHECK(version>0),request_key uuid NOT NULL,
 payload_hash varchar(64) NOT NULL,actor_id uuid NOT NULL REFERENCES members(id),details jsonb NOT NULL,recorded_at timestamptz NOT NULL,
 UNIQUE(application_id,version),UNIQUE(sacco_id,station_id,request_key)
);
CREATE INDEX ix_client_application_command_scope ON client_application_command(sacco_id,station_id,application_id,version DESC);
CREATE FUNCTION client_application_command_scope() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF NOT EXISTS(SELECT 1 FROM loan_applications l JOIN members m ON m.id=l.applicant_member_id WHERE l.id=NEW.application_id AND l.applicant_member_id=NEW.client_id AND l.sacco_id=NEW.sacco_id AND l.station_id=NEW.station_id AND NOT m.client_portal_enabled)
 OR NOT EXISTS(SELECT 1 FROM members WHERE id=NEW.actor_id AND sacco_id=NEW.sacco_id AND station_id=NEW.station_id AND status='ACTIVE') THEN RAISE EXCEPTION 'Client application scope mismatch'; END IF;RETURN NEW;
END $$;
CREATE TRIGGER client_application_command_scope BEFORE INSERT ON client_application_command FOR EACH ROW EXECUTE FUNCTION client_application_command_scope();
CREATE TRIGGER client_application_command_immutable BEFORE UPDATE OR DELETE ON client_application_command FOR EACH ROW EXECUTE FUNCTION gl_protect_evidence();
CREATE TRIGGER client_application_command_no_truncate BEFORE TRUNCATE ON client_application_command FOR EACH STATEMENT EXECUTE FUNCTION gl_protect_evidence();
