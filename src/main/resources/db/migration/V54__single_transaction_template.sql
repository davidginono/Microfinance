-- Keep only the current configuration; this library has never posted journals.
CREATE TABLE gl_transaction_template (
 transaction_id uuid PRIMARY KEY, sacco_id varchar(255) NOT NULL,
 request_key uuid NOT NULL, payload_hash varchar(64) NOT NULL,
 saved_by uuid NOT NULL REFERENCES members(id),
 UNIQUE(transaction_id,sacco_id),
 FOREIGN KEY(transaction_id,sacco_id) REFERENCES gl_transaction_code(id,sacco_id)
);
CREATE TABLE gl_transaction_template_line (
 transaction_id uuid NOT NULL, sacco_id varchar(255) NOT NULL,
 component varchar(20) NOT NULL CHECK(component IN ('TOTAL','PRINCIPAL','INTEREST','FEES','TAX')),
 side varchar(6) NOT NULL CHECK(side IN ('DEBIT','CREDIT')), account_id uuid NOT NULL,
 PRIMARY KEY(transaction_id,component,side),
 FOREIGN KEY(transaction_id,sacco_id) REFERENCES gl_transaction_template(transaction_id,sacco_id),
 FOREIGN KEY(account_id,sacco_id) REFERENCES gl_account(id,sacco_id)
);
INSERT INTO gl_transaction_template(transaction_id,sacco_id,request_key,payload_hash,saved_by)
 SELECT c.id,c.sacco_id,v.request_key,v.payload_hash,v.created_by
 FROM gl_transaction_code c JOIN gl_template_version v ON v.id=c.current_template_id AND v.sacco_id=c.sacco_id;
INSERT INTO gl_transaction_template_line(transaction_id,sacco_id,component,side,account_id)
 SELECT c.id,c.sacco_id,l.component,l.side,l.account_id
 FROM gl_transaction_code c JOIN gl_template_line l ON l.template_id=c.current_template_id AND l.sacco_id=c.sacco_id;

DROP TRIGGER gl_library_current_valid ON gl_transaction_code;
DROP FUNCTION gl_library_current_guard();
ALTER TABLE gl_transaction_code DROP COLUMN current_template_id, DROP COLUMN revision;
DROP TABLE gl_template_line;
DROP FUNCTION gl_template_line_guard();
DROP TABLE gl_template_version;
DROP FUNCTION gl_template_balance_guard();
DROP FUNCTION gl_library_immutable();

CREATE FUNCTION gl_transaction_template_identity_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF OLD.transaction_id<>NEW.transaction_id OR OLD.sacco_id<>NEW.sacco_id
 THEN RAISE EXCEPTION 'Template ownership is immutable'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER gl_transaction_template_identity BEFORE UPDATE ON gl_transaction_template
 FOR EACH ROW EXECUTE FUNCTION gl_transaction_template_identity_guard();
CREATE TRIGGER gl_transaction_template_line_identity BEFORE UPDATE ON gl_transaction_template_line
 FOR EACH ROW EXECUTE FUNCTION gl_transaction_template_identity_guard();

CREATE FUNCTION gl_transaction_template_line_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE a gl_account; event varchar(40);
BEGIN
 SELECT source_event INTO STRICT event FROM gl_transaction_code
 WHERE id=NEW.transaction_id AND sacco_id=NEW.sacco_id;
 SELECT * INTO STRICT a FROM gl_account WHERE id=NEW.account_id AND sacco_id=NEW.sacco_id FOR SHARE;
 IF NOT a.active OR a.kind='HEADING' OR (event='MANUAL_JOURNAL' AND a.kind='CONTROL')
 THEN RAISE EXCEPTION 'Invalid template posting account'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER gl_transaction_template_line_valid BEFORE INSERT OR UPDATE ON gl_transaction_template_line
 FOR EACH ROW EXECUTE FUNCTION gl_transaction_template_line_guard();

CREATE FUNCTION gl_transaction_template_balance_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE t uuid;
BEGIN
 IF TG_OP='DELETE' THEN t:=OLD.transaction_id; ELSE t:=NEW.transaction_id; END IF;
 IF NOT EXISTS(SELECT 1 FROM gl_transaction_template WHERE transaction_id=t) THEN RETURN NULL; END IF;
 IF (SELECT count(*) FROM gl_transaction_template_line WHERE transaction_id=t) NOT BETWEEN 2 AND 10
 OR EXISTS(SELECT 1 FROM gl_transaction_template_line WHERE transaction_id=t GROUP BY component HAVING count(*)<>2 OR count(DISTINCT account_id)<>2)
 OR (EXISTS(SELECT 1 FROM gl_transaction_template_line WHERE transaction_id=t AND component='TOTAL')
     AND (SELECT count(DISTINCT component) FROM gl_transaction_template_line WHERE transaction_id=t)>1)
 THEN RAISE EXCEPTION 'Template must balance independently for each amount component'; END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER gl_transaction_template_balanced AFTER INSERT OR UPDATE ON gl_transaction_template
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION gl_transaction_template_balance_guard();
CREATE CONSTRAINT TRIGGER gl_transaction_template_lines_balanced AFTER INSERT OR UPDATE OR DELETE ON gl_transaction_template_line
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION gl_transaction_template_balance_guard();
