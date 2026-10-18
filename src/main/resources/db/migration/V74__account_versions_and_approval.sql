-- Institution-wide COA metadata versions. No claims or independent approvals are seeded.
CREATE TABLE gl_account_approval_policy (
 sacco_id varchar(255) NOT NULL REFERENCES registered_saccos(sacco_id), version integer NOT NULL CHECK(version>0),
 create_automatic boolean NOT NULL, update_automatic boolean NOT NULL,
 deactivate_automatic boolean NOT NULL, reactivate_automatic boolean NOT NULL,
 reason varchar(500) NOT NULL CHECK(length(trim(reason))>0),
 created_by uuid NOT NULL REFERENCES members(id), created_at timestamptz NOT NULL,
 PRIMARY KEY(sacco_id,version)
);
CREATE TRIGGER gl_account_policy_immutable BEFORE UPDATE OR DELETE ON gl_account_approval_policy FOR EACH ROW EXECUTE FUNCTION gl_protect_evidence();

CREATE TABLE gl_account_version (
 id uuid PRIMARY KEY, sacco_id varchar(255) NOT NULL REFERENCES registered_saccos(sacco_id), account_id uuid NOT NULL,
 number integer NOT NULL CHECK(number>0), base_id uuid,
 operation varchar(12) NOT NULL CHECK(operation IN ('BASELINE','CREATE','UPDATE','DEACTIVATE','REACTIVATE')),
 state varchar(12) NOT NULL CHECK(state IN ('BASELINE','DRAFT','PENDING','APPROVED','REJECTED','WITHDRAWN')),
 before_snapshot jsonb, snapshot jsonb NOT NULL,
 creator_id uuid NOT NULL REFERENCES members(id), station_id varchar(255),
 reason varchar(500) NOT NULL, revision integer NOT NULL DEFAULT 0 CHECK(revision>=0),
 policy_version integer NOT NULL DEFAULT 0 CHECK(policy_version>=0),
 mode varchar(12) NOT NULL DEFAULT 'DRAFT' CHECK(mode IN ('DRAFT','BASELINE','AUTOMATIC','INDEPENDENT')),
 reviewer_id uuid REFERENCES members(id), decision_reason varchar(500) NOT NULL DEFAULT '',
 created_at timestamptz NOT NULL, submitted_at timestamptz, decided_at timestamptz,
 request_key uuid NOT NULL, request_hash varchar(64) NOT NULL,
 UNIQUE(id,sacco_id,account_id), UNIQUE(sacco_id,account_id,number), UNIQUE(sacco_id,request_key),
 FOREIGN KEY(base_id,sacco_id,account_id) REFERENCES gl_account_version(id,sacco_id,account_id),
 CHECK(snapshot->>'id'=account_id::text),
 CHECK(reviewer_id IS NULL OR reviewer_id<>creator_id),
 CHECK(state NOT IN ('PENDING','APPROVED','REJECTED') OR submitted_at IS NOT NULL),
 CHECK(state NOT IN ('APPROVED','REJECTED') OR decided_at IS NOT NULL),
 CHECK(state NOT IN ('APPROVED','REJECTED') OR mode<>'INDEPENDENT' OR reviewer_id IS NOT NULL),
 CHECK(mode<>'AUTOMATIC' OR reviewer_id IS NULL),
 CHECK((operation='BASELINE')=(state='BASELINE')),
 CHECK(state NOT IN ('APPROVED','REJECTED','PENDING') OR (length(trim(reason))>0 AND mode IN ('AUTOMATIC','INDEPENDENT')))
);
CREATE UNIQUE INDEX ix_gl_account_open_version ON gl_account_version(sacco_id,account_id) WHERE state IN ('DRAFT','PENDING');
CREATE UNIQUE INDEX ix_gl_account_pending_code ON gl_account_version(sacco_id,(snapshot->>'code')) WHERE operation='CREATE' AND state IN ('DRAFT','PENDING');
CREATE INDEX ix_gl_account_version_queue ON gl_account_version(sacco_id,state,created_at DESC,id);
CREATE INDEX ix_gl_account_version_owner ON gl_account_version(sacco_id,station_id,creator_id,created_at DESC,id);
CREATE INDEX ix_gl_account_version_creator ON gl_account_version(creator_id);
CREATE INDEX ix_gl_account_version_reviewer ON gl_account_version(reviewer_id) WHERE reviewer_id IS NOT NULL;
CREATE INDEX ix_gl_account_policy_creator ON gl_account_approval_policy(created_by);
ALTER TABLE gl_account ADD COLUMN current_version_id uuid;
ALTER TABLE gl_account ADD CONSTRAINT gl_account_current_version_fk FOREIGN KEY(current_version_id,sacco_id,id) REFERENCES gl_account_version(id,sacco_id,account_id);

CREATE FUNCTION gl_account_snapshot(a gl_account) RETURNS jsonb LANGUAGE sql IMMUTABLE AS $$
 SELECT jsonb_build_object('id',a.id,'code',a.code,'name',a.name,'nameSw',a.name_sw,'description',a.description,
  'type',a.type,'normalBalance',a.normal_balance,'kind',a.kind,'purpose',a.purpose,'parentId',a.parent_id,'active',a.active)
$$;
-- This records migration-time facts, not a fabricated historical approval.
INSERT INTO gl_account_version(id,sacco_id,account_id,number,operation,state,snapshot,creator_id,reason,mode,created_at,request_key,request_hash)
 SELECT gen_random_uuid(),sacco_id,id,1,'BASELINE','BASELINE',gl_account_snapshot(a),maker_id,'Existing account baseline','BASELINE',now(),gen_random_uuid(),repeat('0',64) FROM gl_account a;
UPDATE gl_account a SET current_version_id=v.id FROM gl_account_version v WHERE v.account_id=a.id AND v.sacco_id=a.sacco_id;

CREATE TABLE gl_account_version_event (
 id uuid PRIMARY KEY, version_id uuid NOT NULL REFERENCES gl_account_version(id), sacco_id varchar(255) NOT NULL,
 action varchar(32) NOT NULL, actor_id uuid NOT NULL REFERENCES members(id), station_id varchar(255) NOT NULL,
 reason varchar(500) NOT NULL, state varchar(12) NOT NULL, policy_version integer NOT NULL, mode varchar(12) NOT NULL, revision integer NOT NULL,
 before_snapshot jsonb, snapshot jsonb NOT NULL, recorded_at timestamptz NOT NULL
);
CREATE INDEX ix_gl_account_version_event ON gl_account_version_event(version_id,recorded_at,id);
CREATE INDEX ix_gl_account_version_event_actor ON gl_account_version_event(actor_id);
CREATE TRIGGER gl_account_event_immutable BEFORE UPDATE OR DELETE ON gl_account_version_event FOR EACH ROW EXECUTE FUNCTION gl_protect_evidence();
CREATE FUNCTION gl_protect_account_version() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' OR OLD.state IN ('BASELINE','APPROVED','REJECTED','WITHDRAWN') THEN RAISE EXCEPTION 'Account version evidence is immutable'; END IF;
 IF ROW(OLD.id,OLD.sacco_id,OLD.account_id,OLD.number,OLD.base_id,OLD.operation,OLD.before_snapshot,OLD.creator_id,OLD.station_id,OLD.created_at,OLD.request_key,OLD.request_hash)
  IS DISTINCT FROM ROW(NEW.id,NEW.sacco_id,NEW.account_id,NEW.number,NEW.base_id,NEW.operation,NEW.before_snapshot,NEW.creator_id,NEW.station_id,NEW.created_at,NEW.request_key,NEW.request_hash)
  THEN RAISE EXCEPTION 'Account version identity is immutable'; END IF;
 IF OLD.state='PENDING' AND (NEW.state NOT IN ('APPROVED','REJECTED','WITHDRAWN') OR
  ROW(OLD.snapshot,OLD.reason,OLD.mode,OLD.policy_version,OLD.submitted_at) IS DISTINCT FROM ROW(NEW.snapshot,NEW.reason,NEW.mode,NEW.policy_version,NEW.submitted_at))
  THEN RAISE EXCEPTION 'Submitted account versions are frozen'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER gl_account_version_immutable BEFORE UPDATE OR DELETE ON gl_account_version FOR EACH ROW EXECUTE FUNCTION gl_protect_account_version();
CREATE FUNCTION gl_account_version_has_evidence() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE v gl_account_version;
BEGIN
 SELECT * INTO STRICT v FROM gl_account_version WHERE id=NEW.id;
 IF v.state<>'BASELINE' AND NOT EXISTS(SELECT 1 FROM gl_account_version_event WHERE version_id=v.id AND sacco_id=v.sacco_id AND revision=v.revision AND state=v.state AND mode=v.mode AND policy_version=v.policy_version AND snapshot=v.snapshot AND before_snapshot IS NOT DISTINCT FROM v.before_snapshot)
  THEN RAISE EXCEPTION 'Account version requires its permanent audit event'; END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER gl_account_version_evidence_commit AFTER INSERT OR UPDATE ON gl_account_version DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION gl_account_version_has_evidence();

-- Compatibility for internal provisioning and imported pre-version account fixtures.
-- Public creation commands always supply their approved version explicitly.
CREATE FUNCTION gl_account_baseline() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE vid uuid:=gen_random_uuid();
BEGIN
 IF NEW.current_version_id IS NULL THEN
  IF EXISTS(SELECT 1 FROM gl_account_approval_policy WHERE sacco_id=NEW.sacco_id AND version=(SELECT max(version) FROM gl_account_approval_policy WHERE sacco_id=NEW.sacco_id) AND NOT create_automatic)
   THEN RAISE EXCEPTION 'Account creation requires an approved version'; END IF;
  INSERT INTO gl_account_version(id,sacco_id,account_id,number,operation,state,snapshot,creator_id,reason,mode,created_at,request_key,request_hash)
   VALUES(vid,NEW.sacco_id,NEW.id,1,'BASELINE','BASELINE',gl_account_snapshot(NEW),NEW.maker_id,'Provisioned account baseline','BASELINE',now(),gen_random_uuid(),repeat('0',64));
  UPDATE gl_account SET current_version_id=vid WHERE id=NEW.id;
 END IF;
 RETURN NULL;
END $$;
CREATE TRIGGER gl_account_baseline_insert AFTER INSERT ON gl_account FOR EACH ROW EXECUTE FUNCTION gl_account_baseline();
CREATE FUNCTION gl_account_version_matches() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE a gl_account; v gl_account_version;
BEGIN
 SELECT * INTO STRICT a FROM gl_account WHERE id=NEW.id;
 SELECT * INTO STRICT v FROM gl_account_version WHERE id=a.current_version_id AND sacco_id=a.sacco_id AND account_id=a.id;
 IF v.state NOT IN ('BASELINE','APPROVED') OR v.snapshot IS DISTINCT FROM gl_account_snapshot(a)
 THEN RAISE EXCEPTION 'Account changes require an approved matching version'; END IF;
 IF TG_OP='UPDATE' AND OLD.current_version_id IS NOT NULL AND OLD.current_version_id IS DISTINCT FROM a.current_version_id
  AND (v.state<>'APPROVED' OR v.number<=(SELECT number FROM gl_account_version WHERE id=OLD.current_version_id))
  THEN RAISE EXCEPTION 'Account versions must advance without rewriting history'; END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER gl_account_version_commit AFTER INSERT OR UPDATE ON gl_account DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION gl_account_version_matches();

-- Existing posted lines retain NULL: their historical labels cannot be reconstructed.
ALTER TABLE gl_journal_line ADD COLUMN account_version_id uuid;
ALTER TABLE gl_journal_line ADD CONSTRAINT gl_line_account_version_fk FOREIGN KEY(account_version_id,sacco_id,account_id) REFERENCES gl_account_version(id,sacco_id,account_id);
CREATE FUNCTION gl_journal_account_version() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE vid uuid;
BEGIN
 SELECT current_version_id INTO STRICT vid FROM gl_account WHERE id=NEW.account_id AND sacco_id=NEW.sacco_id FOR SHARE;
 IF NEW.account_version_id IS NOT NULL AND NEW.account_version_id IS DISTINCT FROM vid THEN RAISE EXCEPTION 'Account version has changed'; END IF;
 NEW.account_version_id:=vid;
 IF vid IS NULL THEN RAISE EXCEPTION 'An account version is required'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER gl_journal_line_version BEFORE INSERT ON gl_journal_line FOR EACH ROW EXECUTE FUNCTION gl_journal_account_version();
