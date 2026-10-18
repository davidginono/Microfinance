-- Preserve the policy behind an approval after administrators change the current rules.
CREATE TABLE voucher_approval_configuration_history (
 sacco_id varchar(255) NOT NULL, station_id varchar(255) NOT NULL, type varchar(10) NOT NULL,
 version integer NOT NULL, definition jsonb NOT NULL, updated_by uuid NOT NULL REFERENCES members(id), updated_at timestamptz NOT NULL,
 PRIMARY KEY(sacco_id,station_id,type,version),
 FOREIGN KEY(sacco_id,station_id,type) REFERENCES voucher_approval_configuration(sacco_id,station_id,type)
);
-- Copy only definitions that actually exist; never infer superseded policy versions.
INSERT INTO voucher_approval_configuration_history SELECT sacco_id,station_id,type,version,definition,updated_by,updated_at FROM voucher_approval_configuration;
CREATE TRIGGER voucher_approval_revision_immutable BEFORE UPDATE OR DELETE ON voucher_approval_configuration_history FOR EACH ROW EXECUTE FUNCTION gl_protect_evidence();
CREATE FUNCTION record_voucher_approval_configuration() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='UPDATE' AND (NEW.version<>OLD.version+1 OR
    ROW(NEW.sacco_id,NEW.station_id,NEW.type) IS DISTINCT FROM ROW(OLD.sacco_id,OLD.station_id,OLD.type))
    THEN RAISE EXCEPTION 'Approval configuration requires a new immutable revision'; END IF;
 IF (NEW.definition->>'version')::integer IS DISTINCT FROM NEW.version OR NEW.definition->>'type' IS DISTINCT FROM NEW.type
    THEN RAISE EXCEPTION 'Approval configuration identity must match its definition'; END IF;
 INSERT INTO voucher_approval_configuration_history VALUES(NEW.sacco_id,NEW.station_id,NEW.type,NEW.version,NEW.definition,NEW.updated_by,NEW.updated_at);
 RETURN NEW;
END $$;
CREATE TRIGGER voucher_approval_revision AFTER INSERT OR UPDATE ON voucher_approval_configuration FOR EACH ROW EXECUTE FUNCTION record_voucher_approval_configuration();
ALTER TABLE voucher_workflow_history ADD COLUMN context jsonb NOT NULL DEFAULT '{}';
