-- Employer withholding is evidence, not cash. Only linked loan receipts post money.
CREATE TABLE payroll_feedback (
 id uuid PRIMARY KEY, batch_id uuid NOT NULL REFERENCES payroll_batch(id),
 sacco_id varchar(255) NOT NULL, station_id varchar(255) NOT NULL,
 filename varchar(150) NOT NULL, checksum char(64) NOT NULL, semantic_hash char(64) NOT NULL,
 content bytea NOT NULL CHECK(octet_length(content) BETWEEN 1 AND 2097152),
 evidence varchar(500) NOT NULL CHECK(length(trim(evidence))>0),
 actor_id uuid NOT NULL REFERENCES members(id), actor_name varchar(255) NOT NULL, created_at timestamptz NOT NULL,
 row_count integer NOT NULL CHECK(row_count BETWEEN 1 AND 1000), total numeric(18,2) NOT NULL CHECK(total>=0),
 UNIQUE(batch_id,checksum),UNIQUE(batch_id,semantic_hash),UNIQUE(id,batch_id),
 FOREIGN KEY(sacco_id,station_id) REFERENCES sacco_stations(sacco_id,station_id)
);
CREATE INDEX payroll_feedback_batch ON payroll_feedback(batch_id,created_at DESC,id);
CREATE TABLE payroll_feedback_row (
 feedback_id uuid NOT NULL, batch_id uuid NOT NULL, line_number integer NOT NULL CHECK(line_number BETWEEN 2 AND 1001),
 client_id uuid NOT NULL REFERENCES members(id), client_name varchar(255) NOT NULL, employee_number varchar(50) NOT NULL,
 deduction_reference uuid NOT NULL, status varchar(10) NOT NULL CHECK(status IN('SUCCESS','FAILED')),
 amount numeric(18,2) NOT NULL CHECK(amount>=0), requested numeric(18,2) NOT NULL CHECK(requested>0 AND amount<=requested),
 deduction_date date, employer_reference varchar(250) NOT NULL CHECK(length(trim(employer_reference))>0),
 PRIMARY KEY(feedback_id,line_number),UNIQUE(feedback_id,client_id),
 FOREIGN KEY(feedback_id,batch_id) REFERENCES payroll_feedback(id,batch_id),
 CHECK((status='SUCCESS' AND amount>0 AND deduction_date IS NOT NULL) OR (status='FAILED' AND amount=0))
);
-- A payroll request accepts one final positive result per employee, including a partial deduction.
CREATE UNIQUE INDEX payroll_feedback_success ON payroll_feedback_row(batch_id,client_id) WHERE status='SUCCESS';
CREATE TABLE payroll_remittance (
 feedback_id uuid PRIMARY KEY REFERENCES payroll_feedback(id),receipt_batch_id uuid NOT NULL UNIQUE REFERENCES receipt_batch(id),
 sacco_id varchar(255) NOT NULL,station_id varchar(255) NOT NULL,money_account_id uuid NOT NULL,
 received_date date NOT NULL,received_amount numeric(18,2) NOT NULL CHECK(received_amount>0),
 reference varchar(80) NOT NULL CHECK(length(trim(reference))>0),evidence varchar(500) NOT NULL CHECK(length(trim(evidence))>0),
 actor_id uuid NOT NULL REFERENCES members(id),created_at timestamptz NOT NULL,
 FOREIGN KEY(money_account_id,sacco_id) REFERENCES gl_account(id,sacco_id),
 UNIQUE(sacco_id,station_id,money_account_id,reference)
);
CREATE TABLE payroll_feedback_receipt (
 feedback_id uuid NOT NULL,line_number integer NOT NULL,item_id uuid NOT NULL UNIQUE REFERENCES receipt_batch_item(id),
 PRIMARY KEY(feedback_id,line_number), FOREIGN KEY(feedback_id,line_number) REFERENCES payroll_feedback_row(feedback_id,line_number)
);
CREATE TRIGGER payroll_feedback_immutable BEFORE UPDATE OR DELETE ON payroll_feedback FOR EACH ROW EXECUTE FUNCTION protect_loan_financial_history();
CREATE TRIGGER payroll_feedback_row_immutable BEFORE UPDATE OR DELETE ON payroll_feedback_row FOR EACH ROW EXECUTE FUNCTION protect_loan_financial_history();
CREATE TRIGGER payroll_remittance_immutable BEFORE UPDATE OR DELETE ON payroll_remittance FOR EACH ROW EXECUTE FUNCTION protect_loan_financial_history();
CREATE TRIGGER payroll_feedback_receipt_immutable BEFORE UPDATE OR DELETE ON payroll_feedback_receipt FOR EACH ROW EXECUTE FUNCTION protect_loan_financial_history();

CREATE FUNCTION payroll_feedback_scope() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE b payroll_batch; total_amount numeric; cnt integer;
BEGIN
 IF TG_TABLE_NAME='payroll_feedback' THEN
  SELECT * INTO b FROM payroll_batch WHERE id=NEW.batch_id;
  IF (NEW.sacco_id,NEW.station_id) IS DISTINCT FROM (b.sacco_id,b.station_id) THEN RAISE EXCEPTION 'Payroll feedback scope mismatch'; END IF;
 ELSE
  SELECT sum(amount),count(*) INTO total_amount,cnt FROM payroll_batch_line WHERE batch_id=NEW.batch_id AND client_id=NEW.client_id AND amount>0 AND employee_number=NEW.employee_number AND client_name=NEW.client_name;
  IF cnt=0 OR total_amount IS DISTINCT FROM NEW.requested THEN RAISE EXCEPTION 'Payroll feedback source mismatch'; END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER payroll_feedback_scope_check BEFORE INSERT ON payroll_feedback FOR EACH ROW EXECUTE FUNCTION payroll_feedback_scope();
CREATE TRIGGER payroll_feedback_row_scope_check BEFORE INSERT ON payroll_feedback_row FOR EACH ROW EXECUTE FUNCTION payroll_feedback_scope();

CREATE FUNCTION payroll_feedback_complete() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE f payroll_feedback; m payroll_remittance; cnt integer; amount numeric;
BEGIN
 IF TG_TABLE_NAME='payroll_feedback' THEN SELECT * INTO f FROM payroll_feedback WHERE id=NEW.id;
 ELSE SELECT * INTO f FROM payroll_feedback WHERE id=NEW.feedback_id; END IF;
 SELECT count(*),sum(r.amount) INTO cnt,amount FROM payroll_feedback_row r WHERE r.feedback_id=f.id;
 IF cnt<>f.row_count OR amount IS DISTINCT FROM f.total THEN RAISE EXCEPTION 'Incomplete payroll feedback'; END IF;
 SELECT * INTO m FROM payroll_remittance WHERE feedback_id=f.id;
 IF FOUND THEN
  IF (m.sacco_id,m.station_id,m.received_amount) IS DISTINCT FROM (f.sacco_id,f.station_id,f.total)
    OR NOT EXISTS(SELECT 1 FROM receipt_batch b WHERE b.id=m.receipt_batch_id AND b.sacco_id=f.sacco_id AND b.station_id=f.station_id AND b.created_by=m.actor_id)
    OR (SELECT count(*) FROM payroll_feedback_receipt l WHERE l.feedback_id=f.id)<>(SELECT count(*) FROM payroll_feedback_row r WHERE r.feedback_id=f.id AND r.status='SUCCESS')
    OR (SELECT count(*) FROM receipt_batch_item i WHERE i.batch_id=m.receipt_batch_id)<>(SELECT count(*) FROM payroll_feedback_receipt l WHERE l.feedback_id=f.id)
    OR EXISTS(SELECT 1 FROM payroll_feedback_receipt l JOIN payroll_feedback_row r USING(feedback_id,line_number) JOIN receipt_batch_item i ON i.id=l.item_id
       WHERE l.feedback_id=f.id AND (r.status<>'SUCCESS' OR i.batch_id<>m.receipt_batch_id OR i.total<>r.amount OR i.payment_date<>m.received_date
       OR i.sacco_id<>f.sacco_id OR i.station_id<>f.station_id OR i.form_json->>'clientId'<>r.client_id::text OR i.form_json->>'moneyAccountId'<>m.money_account_id::text))
  THEN RAISE EXCEPTION 'Payroll remittance receipt mismatch'; END IF;
 END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER payroll_feedback_complete_header AFTER INSERT ON payroll_feedback DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION payroll_feedback_complete();
CREATE CONSTRAINT TRIGGER payroll_feedback_complete_row AFTER INSERT ON payroll_feedback_row DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION payroll_feedback_complete();
CREATE CONSTRAINT TRIGGER payroll_remittance_complete AFTER INSERT ON payroll_remittance DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION payroll_feedback_complete();
CREATE CONSTRAINT TRIGGER payroll_feedback_receipt_complete AFTER INSERT ON payroll_feedback_receipt DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION payroll_feedback_complete();

-- Receipt worker state/preview/retry updates remain possible; imported source facts cannot be edited/deleted.
CREATE FUNCTION payroll_receipt_source_protection() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='INSERT' THEN
  IF EXISTS(SELECT 1 FROM payroll_remittance WHERE receipt_batch_id=NEW.batch_id) THEN RAISE EXCEPTION 'Payroll receipt batch is sealed'; END IF;
  RETURN NEW;
 END IF;
 IF EXISTS(SELECT 1 FROM payroll_feedback_receipt WHERE item_id=OLD.id) THEN
  IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Payroll receipt source is immutable'; END IF;
  IF (NEW.id,NEW.batch_id,NEW.sacco_id,NEW.station_id,NEW.line_no,NEW.form_json,NEW.payment_date,NEW.total,NEW.client_label,NEW.loan_labels)
    IS DISTINCT FROM (OLD.id,OLD.batch_id,OLD.sacco_id,OLD.station_id,OLD.line_no,OLD.form_json,OLD.payment_date,OLD.total,OLD.client_label,OLD.loan_labels)
  THEN RAISE EXCEPTION 'Payroll receipt source is immutable'; END IF;
 END IF;
 IF TG_OP='DELETE' THEN RETURN OLD; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER payroll_receipt_source_guard BEFORE INSERT OR UPDATE OR DELETE ON receipt_batch_item FOR EACH ROW EXECUTE FUNCTION payroll_receipt_source_protection();
