-- Definition library only; no balances or journal postings are created.
CREATE TABLE gl_activity (
 id uuid PRIMARY KEY, sacco_id varchar(255) NOT NULL REFERENCES registered_saccos(sacco_id),
 code varchar(40) NOT NULL CHECK(code ~ '^[A-Z0-9][A-Z0-9_.-]{0,39}$'), name varchar(160) NOT NULL CHECK(length(trim(name))>0),
 name_sw varchar(160), description varchar(500), active boolean NOT NULL DEFAULT true,
 created_by uuid NOT NULL REFERENCES members(id), created_at timestamptz NOT NULL,
 UNIQUE(sacco_id,code), UNIQUE(id,sacco_id)
);
CREATE TABLE gl_transaction_code (
 id uuid PRIMARY KEY, sacco_id varchar(255) NOT NULL, activity_id uuid NOT NULL,
 code varchar(40) NOT NULL CHECK(code ~ '^[A-Z0-9][A-Z0-9_.-]{0,39}$'), name varchar(160) NOT NULL CHECK(length(trim(name))>0),
 name_sw varchar(160), description varchar(500), source_event varchar(40) NOT NULL,
 active boolean NOT NULL DEFAULT true, revision integer NOT NULL DEFAULT 0 CHECK(revision>=0), current_template_id uuid,
 created_by uuid NOT NULL REFERENCES members(id), created_at timestamptz NOT NULL,
 UNIQUE(sacco_id,code), UNIQUE(id,sacco_id), FOREIGN KEY(activity_id,sacco_id) REFERENCES gl_activity(id,sacco_id),
 CHECK(source_event IN ('OPENING_BALANCE','DISBURSEMENT','REPAYMENT','INTEREST_ACCRUAL','FEE','REFUND','ADVANCE','SETTLEMENT','TOP_UP','EXPENSE','FUNDING','CAPITAL','PROVISION','WRITE_OFF','RECOVERY','REVERSAL','MANUAL_JOURNAL','OPERATIONAL_BRIDGE')),
 CHECK((revision=0 AND current_template_id IS NULL) OR (revision>0 AND current_template_id IS NOT NULL))
);
CREATE INDEX ix_gl_transaction_activity ON gl_transaction_code(sacco_id,activity_id,code,id);
CREATE TABLE gl_template_version (
 id uuid PRIMARY KEY, sacco_id varchar(255) NOT NULL, transaction_id uuid NOT NULL,
 version integer NOT NULL CHECK(version>0), source_event varchar(40) NOT NULL,
 reason varchar(500) NOT NULL CHECK(length(trim(reason))>0), request_key uuid NOT NULL, payload_hash varchar(64) NOT NULL,
 created_by uuid NOT NULL REFERENCES members(id), created_at timestamptz NOT NULL,
 creation_xid bigint NOT NULL DEFAULT txid_current(),
 UNIQUE(transaction_id,version), UNIQUE(sacco_id,transaction_id,request_key), UNIQUE(id,sacco_id), UNIQUE(id,transaction_id,sacco_id),
 FOREIGN KEY(transaction_id,sacco_id) REFERENCES gl_transaction_code(id,sacco_id)
);
ALTER TABLE gl_transaction_code ADD FOREIGN KEY(current_template_id,id,sacco_id) REFERENCES gl_template_version(id,transaction_id,sacco_id);
CREATE TABLE gl_template_line (
 template_id uuid NOT NULL, sacco_id varchar(255) NOT NULL, component varchar(20) NOT NULL,
 side varchar(6) NOT NULL CHECK(side IN ('DEBIT','CREDIT')), account_id uuid NOT NULL,
 PRIMARY KEY(template_id,component,side), FOREIGN KEY(template_id,sacco_id) REFERENCES gl_template_version(id,sacco_id),
 FOREIGN KEY(account_id,sacco_id) REFERENCES gl_account(id,sacco_id),
 CHECK(component IN ('TOTAL','PRINCIPAL','INTEREST','FEES','TAX'))
);
CREATE FUNCTION gl_library_immutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Accounting library history is immutable'; END $$;
CREATE TRIGGER gl_template_immutable BEFORE UPDATE OR DELETE ON gl_template_version FOR EACH ROW EXECUTE FUNCTION gl_library_immutable();
CREATE TRIGGER gl_template_line_immutable BEFORE UPDATE OR DELETE ON gl_template_line FOR EACH ROW EXECUTE FUNCTION gl_library_immutable();
CREATE FUNCTION gl_library_identity_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Deactivate library definitions instead of deleting them'; END IF;
 IF OLD.id<>NEW.id OR OLD.sacco_id<>NEW.sacco_id OR OLD.code<>NEW.code THEN RAISE EXCEPTION 'Library identity is immutable'; END IF;
 IF TG_TABLE_NAME='gl_transaction_code' AND (to_jsonb(OLD)->>'activity_id' IS DISTINCT FROM to_jsonb(NEW)->>'activity_id'
    OR to_jsonb(OLD)->>'source_event' IS DISTINCT FROM to_jsonb(NEW)->>'source_event') THEN RAISE EXCEPTION 'Transaction ownership and event are immutable'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER gl_activity_identity BEFORE UPDATE OR DELETE ON gl_activity FOR EACH ROW EXECUTE FUNCTION gl_library_identity_guard();
CREATE TRIGGER gl_transaction_identity BEFORE UPDATE OR DELETE ON gl_transaction_code FOR EACH ROW EXECUTE FUNCTION gl_library_identity_guard();
CREATE FUNCTION gl_template_line_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE v gl_template_version; a gl_account;
BEGIN
 SELECT * INTO STRICT v FROM gl_template_version WHERE id=NEW.template_id AND sacco_id=NEW.sacco_id;
 IF v.creation_xid<>txid_current() THEN RAISE EXCEPTION 'Cannot append lines to a saved template'; END IF;
 SELECT * INTO STRICT a FROM gl_account WHERE id=NEW.account_id AND sacco_id=NEW.sacco_id FOR SHARE;
 IF NOT a.active OR a.kind='HEADING' OR (v.source_event='MANUAL_JOURNAL' AND a.kind='CONTROL') THEN RAISE EXCEPTION 'Invalid template posting account'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER gl_template_line_valid BEFORE INSERT ON gl_template_line FOR EACH ROW EXECUTE FUNCTION gl_template_line_guard();
CREATE FUNCTION gl_template_balance_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE t uuid;
BEGIN
 IF TG_TABLE_NAME='gl_template_version' THEN t:=NEW.id; ELSE t:=NEW.template_id; END IF;
 IF (SELECT count(*) FROM gl_template_line WHERE template_id=t) NOT BETWEEN 2 AND 10
    OR EXISTS(SELECT 1 FROM gl_template_line WHERE template_id=t GROUP BY component HAVING count(*)<>2 OR count(DISTINCT account_id)<>2)
    OR EXISTS(SELECT 1 FROM gl_template_line WHERE template_id=t AND component='TOTAL') AND (SELECT count(DISTINCT component) FROM gl_template_line WHERE template_id=t)>1
    OR NOT EXISTS(SELECT 1 FROM gl_template_version v JOIN gl_transaction_code c ON c.id=v.transaction_id AND c.sacco_id=v.sacco_id WHERE v.id=t AND c.source_event=v.source_event)
 THEN RAISE EXCEPTION 'Template must balance independently for each amount component'; END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER gl_template_balanced AFTER INSERT ON gl_template_version DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION gl_template_balance_guard();
CREATE CONSTRAINT TRIGGER gl_template_lines_balanced AFTER INSERT ON gl_template_line DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION gl_template_balance_guard();
CREATE FUNCTION gl_library_current_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM gl_transaction_code c WHERE c.id=NEW.id AND c.sacco_id=NEW.sacco_id
   AND (c.revision=0 OR EXISTS(SELECT 1 FROM gl_template_version v WHERE v.id=c.current_template_id AND v.transaction_id=c.id AND v.sacco_id=c.sacco_id AND v.version=c.revision)))
 THEN RAISE EXCEPTION 'Current template must match its version number'; END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER gl_library_current_valid AFTER INSERT OR UPDATE ON gl_transaction_code DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION gl_library_current_guard();
