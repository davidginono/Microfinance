-- Configuration is opt-in, separately claim-protected, and branch scoped. No grants or policy thresholds are seeded.
CREATE SEQUENCE voucher_workflow_number_seq;
CREATE TABLE voucher_approval_configuration (
 sacco_id varchar(255) NOT NULL, station_id varchar(255) NOT NULL,
 type varchar(10) NOT NULL CHECK(type IN ('RECEIPT','PAYMENT','JOURNAL')),
 version integer NOT NULL CHECK(version>0), definition jsonb NOT NULL,
 updated_by uuid NOT NULL REFERENCES members(id), updated_at timestamptz NOT NULL,
 PRIMARY KEY(sacco_id,station_id,type), FOREIGN KEY(sacco_id,station_id) REFERENCES sacco_stations(sacco_id,station_id)
);
CREATE TABLE voucher_workflow (
 id uuid PRIMARY KEY, number varchar(40) NOT NULL UNIQUE,
 sacco_id varchar(255) NOT NULL, station_id varchar(255) NOT NULL,
 type varchar(10) NOT NULL CHECK(type IN ('RECEIPT','PAYMENT','JOURNAL')),
 scope varchar(8) NOT NULL CHECK(scope IN ('SINGLE','BATCH')), title varchar(160) NOT NULL,
 owner_id uuid NOT NULL REFERENCES members(id), request_key uuid NOT NULL,
 version integer NOT NULL DEFAULT 0, approval varchar(32) NOT NULL DEFAULT 'REQUIRED'
 CHECK(approval IN ('NOT_REQUIRED','REQUIRED','INITIATED','WITHDRAWN','HELD_FROM_APPROVAL','REJECTED','RESUBMIT_FOR_APPROVAL','WORKFLOW_APPROVED','MANUALLY_APPROVED')),
 validation varchar(20) NOT NULL DEFAULT 'INCOMPLETE' CHECK(validation IN ('INCOMPLETE','NOT_VALIDATED','VALIDATED','ERROR')),
 posting varchar(16) NOT NULL DEFAULT 'NOT_POSTED' CHECK(posting IN ('NOT_POSTED','POSTING','FAILED','POSTED')),
 total numeric(18,2) NOT NULL DEFAULT 0 CHECK(total>=0), assessment_amount numeric(18,2) NOT NULL DEFAULT 0,
 item_count integer NOT NULL DEFAULT 0 CHECK(item_count BETWEEN 0 AND 50),
 stage integer NOT NULL DEFAULT 0, round integer NOT NULL DEFAULT 0, config_version integer NOT NULL DEFAULT 0, stages jsonb NOT NULL DEFAULT '[]',
 force_allowed boolean NOT NULL DEFAULT false, failure varchar(160), created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
 UNIQUE(id,sacco_id,station_id), UNIQUE(sacco_id,station_id,request_key),
 FOREIGN KEY(sacco_id,station_id) REFERENCES sacco_stations(sacco_id,station_id),
 CHECK(posting NOT IN ('POSTING','POSTED') OR (validation='VALIDATED' AND approval IN ('NOT_REQUIRED','WORKFLOW_APPROVED','MANUALLY_APPROVED')))
);
CREATE INDEX ix_voucher_workflow_type ON voucher_workflow(sacco_id,station_id,type,updated_at DESC,id);
CREATE INDEX ix_voucher_workflow_queue ON voucher_workflow(sacco_id,station_id,approval,updated_at DESC,id);
CREATE INDEX ix_voucher_workflow_owner ON voucher_workflow(sacco_id,station_id,owner_id,created_at DESC,id);
CREATE TABLE voucher_workflow_item (
 id uuid PRIMARY KEY, workflow_id uuid NOT NULL, sacco_id varchar(255) NOT NULL, station_id varchar(255) NOT NULL,
 line integer NOT NULL CHECK(line BETWEEN 1 AND 50), version integer NOT NULL DEFAULT 0,
 form_json jsonb NOT NULL, issues jsonb NOT NULL DEFAULT '[]', plan_hash varchar(64), voucher_id uuid UNIQUE,
 UNIQUE(workflow_id,line), FOREIGN KEY(workflow_id,sacco_id,station_id) REFERENCES voucher_workflow(id,sacco_id,station_id),
 FOREIGN KEY(voucher_id,sacco_id,station_id) REFERENCES accounting_voucher(id,sacco_id,station_id)
);
CREATE TABLE voucher_workflow_history (
 id uuid PRIMARY KEY, workflow_id uuid NOT NULL, sacco_id varchar(255) NOT NULL, station_id varchar(255) NOT NULL,
 action varchar(64) NOT NULL, round integer NOT NULL, actor_id uuid NOT NULL REFERENCES members(id), comments varchar(1000) NOT NULL DEFAULT '', recorded_at timestamptz NOT NULL,
 FOREIGN KEY(workflow_id,sacco_id,station_id) REFERENCES voucher_workflow(id,sacco_id,station_id)
);
CREATE INDEX ix_voucher_workflow_history ON voucher_workflow_history(workflow_id,recorded_at,id);
CREATE TABLE voucher_workflow_document (
 id uuid PRIMARY KEY, workflow_id uuid NOT NULL, sacco_id varchar(255) NOT NULL, station_id varchar(255) NOT NULL,
 filename varchar(160) NOT NULL, media_type varchar(40) NOT NULL CHECK(media_type IN ('application/pdf','image/png','image/jpeg')),
 content bytea NOT NULL CHECK(octet_length(content) BETWEEN 1 AND 5242880), uploaded_by uuid NOT NULL REFERENCES members(id), uploaded_at timestamptz NOT NULL,
 FOREIGN KEY(workflow_id,sacco_id,station_id) REFERENCES voucher_workflow(id,sacco_id,station_id)
);
CREATE TRIGGER voucher_document_immutable BEFORE UPDATE OR DELETE ON voucher_workflow_document FOR EACH ROW EXECUTE FUNCTION gl_protect_evidence();
CREATE TRIGGER voucher_workflow_history_immutable BEFORE UPDATE OR DELETE ON voucher_workflow_history FOR EACH ROW EXECUTE FUNCTION gl_protect_evidence();
CREATE FUNCTION protect_voucher_workflow() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE w voucher_workflow;
BEGIN
 IF TG_TABLE_NAME='voucher_workflow' THEN
  IF TG_OP='DELETE' OR OLD.posting='POSTED' THEN RAISE EXCEPTION 'Posted workflow history is immutable'; END IF;
  IF ROW(OLD.id,OLD.sacco_id,OLD.station_id,OLD.type,OLD.scope,OLD.owner_id,OLD.request_key,OLD.created_at)
    IS DISTINCT FROM ROW(NEW.id,NEW.sacco_id,NEW.station_id,NEW.type,NEW.scope,NEW.owner_id,NEW.request_key,NEW.created_at)
    THEN RAISE EXCEPTION 'Workflow identity is immutable'; END IF;
 ELSE
  SELECT * INTO STRICT w FROM voucher_workflow WHERE id=COALESCE(NEW.workflow_id,OLD.workflow_id) FOR UPDATE;
  IF w.posting='POSTED' OR (TG_OP<>'INSERT' AND OLD.voucher_id IS NOT NULL) THEN RAISE EXCEPTION 'Posted workflow items are immutable'; END IF;
  IF TG_OP='UPDATE' AND ROW(OLD.id,OLD.workflow_id,OLD.sacco_id,OLD.station_id,OLD.line)
    IS DISTINCT FROM ROW(NEW.id,NEW.workflow_id,NEW.sacco_id,NEW.station_id,NEW.line)
    THEN RAISE EXCEPTION 'Workflow item identity is immutable'; END IF;
  IF TG_OP='UPDATE' AND OLD.form_json IS DISTINCT FROM NEW.form_json AND w.approval='INITIATED'
    THEN RAISE EXCEPTION 'Withdraw approval before changing its content'; END IF;
 END IF;
 RETURN CASE WHEN TG_OP='DELETE' THEN OLD ELSE NEW END;
END $$;
CREATE TRIGGER voucher_workflow_protection BEFORE UPDATE OR DELETE ON voucher_workflow FOR EACH ROW EXECUTE FUNCTION protect_voucher_workflow();
CREATE TRIGGER voucher_workflow_item_protection BEFORE INSERT OR UPDATE OR DELETE ON voucher_workflow_item FOR EACH ROW EXECUTE FUNCTION protect_voucher_workflow();
CREATE FUNCTION validate_voucher_workflow_post() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE w voucher_workflow; i voucher_workflow_item; c voucher_approval_configuration;
BEGIN
 SELECT * INTO i FROM voucher_workflow_item WHERE id=NEW.request_key AND sacco_id=NEW.sacco_id AND station_id=NEW.station_id;
 IF FOUND THEN
  SELECT * INTO STRICT w FROM voucher_workflow WHERE id=i.workflow_id;
  IF w.posting<>'POSTED' OR w.validation<>'VALIDATED' OR w.approval NOT IN ('NOT_REQUIRED','WORKFLOW_APPROVED','MANUALLY_APPROVED')
    OR i.voucher_id IS DISTINCT FROM NEW.id OR w.type<>NEW.type OR i.plan_hash IS NULL
    THEN RAISE EXCEPTION 'Voucher requires its completed approval envelope'; END IF;
 ELSE
  SELECT * INTO c FROM voucher_approval_configuration WHERE sacco_id=NEW.sacco_id AND station_id=NEW.station_id AND type=NEW.type;
  IF coalesce((c.definition->>'individuals')::boolean,false) AND NEW.reverses_id IS NULL
    THEN RAISE EXCEPTION 'Individual voucher approval is required'; END IF;
 END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER configured_voucher_commit AFTER INSERT ON accounting_voucher DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION validate_voucher_workflow_post();

-- Validate the envelope at commit, after every child voucher has been linked.
CREATE FUNCTION validate_voucher_workflow_envelope() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE w voucher_workflow; current_version integer;
BEGIN
 SELECT * INTO STRICT w FROM voucher_workflow WHERE id=NEW.id;
 IF w.posting='POSTED' THEN
  SELECT version INTO current_version FROM voucher_approval_configuration WHERE sacco_id=w.sacco_id AND station_id=w.station_id AND type=w.type;
  IF w.item_count=0 OR w.item_count<>(SELECT count(*) FROM voucher_workflow_item WHERE workflow_id=w.id)
    OR (w.scope='SINGLE' AND w.item_count<>1) OR w.config_version<>coalesce(current_version,0)
    OR EXISTS(SELECT 1 FROM voucher_workflow_item WHERE workflow_id=w.id AND (voucher_id IS NULL OR plan_hash IS NULL OR issues<>'[]'::jsonb))
    THEN RAISE EXCEPTION 'Every approved voucher must commit together'; END IF;
 END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER voucher_workflow_commit AFTER UPDATE ON voucher_workflow DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION validate_voucher_workflow_envelope();
