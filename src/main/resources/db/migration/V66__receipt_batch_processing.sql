-- Durable receipt work, not another loan ledger or general ledger.
ALTER TABLE gl_activity ADD COLUMN system_key varchar(40);
ALTER TABLE gl_transaction_code ADD COLUMN system_key varchar(40);
ALTER TABLE gl_activity ALTER COLUMN created_by DROP NOT NULL;
ALTER TABLE gl_transaction_code ALTER COLUMN created_by DROP NOT NULL;
CREATE UNIQUE INDEX ux_activity_system ON gl_activity(sacco_id,system_key) WHERE system_key IS NOT NULL;
CREATE UNIQUE INDEX ux_transaction_system ON gl_transaction_code(sacco_id,system_key) WHERE system_key IS NOT NULL;
ALTER TABLE gl_activity ADD CHECK(created_by IS NOT NULL OR system_key IS NOT NULL);
ALTER TABLE gl_transaction_code ADD CHECK(created_by IS NOT NULL OR system_key IS NOT NULL);

CREATE FUNCTION seed_loan_repayment_activity(institution varchar) RETURNS void LANGUAGE plpgsql AS $$
DECLARE activity uuid:=gen_random_uuid();
BEGIN
 INSERT INTO gl_activity(id,sacco_id,code,name,name_sw,description,created_at,system_key)
 VALUES(activity,institution,'SYSTEM.LOAN_REPAYMENT','Loan repayment','Marejesho ya mkopo','',now(),'LOAN_REPAYMENT');
 INSERT INTO gl_transaction_code(id,sacco_id,activity_id,code,name,name_sw,description,source_event,created_at,system_key)
 VALUES(gen_random_uuid(),institution,activity,'SYSTEM.REPAYMENT_PRINCIPAL','Principal repayment','Marejesho ya mtaji','','REPAYMENT',now(),'REPAYMENT_PRINCIPAL'),
       (gen_random_uuid(),institution,activity,'SYSTEM.REPAYMENT_INTEREST','Interest repayment','Malipo ya riba','','REPAYMENT',now(),'REPAYMENT_INTEREST');
END $$;
SELECT seed_loan_repayment_activity(sacco_id) FROM registered_saccos;
CREATE FUNCTION seed_institution_repayment_activity() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN PERFORM seed_loan_repayment_activity(NEW.sacco_id); RETURN NEW; END $$;
CREATE TRIGGER institution_repayment_activity AFTER INSERT ON registered_saccos FOR EACH ROW EXECUTE FUNCTION seed_institution_repayment_activity();
CREATE FUNCTION protect_repayment_definitions() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF OLD.system_key IS NOT NULL OR (TG_OP='UPDATE' AND NEW.system_key IS DISTINCT FROM OLD.system_key)
 THEN RAISE EXCEPTION 'System repayment definitions cannot be edited or removed'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER system_activity_guard BEFORE UPDATE OR DELETE ON gl_activity FOR EACH ROW EXECUTE FUNCTION protect_repayment_definitions();
CREATE TRIGGER system_transaction_guard BEFORE UPDATE OR DELETE ON gl_transaction_code FOR EACH ROW EXECUTE FUNCTION protect_repayment_definitions();
CREATE FUNCTION protect_repayment_templates() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF EXISTS(SELECT 1 FROM gl_transaction_code WHERE id=NEW.transaction_id AND system_key IS NOT NULL)
 THEN RAISE EXCEPTION 'System repayments use repayment policy and loan-owned account mappings'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER system_template_guard BEFORE INSERT OR UPDATE ON gl_transaction_template FOR EACH ROW EXECUTE FUNCTION protect_repayment_templates();

CREATE TABLE repayment_accounting_policy (
 id uuid PRIMARY KEY, sacco_id varchar(255) NOT NULL REFERENCES registered_saccos(sacco_id),
 version integer NOT NULL CHECK(version>0), effective_from date NOT NULL,
 allocation_order varchar(24) NOT NULL CHECK(allocation_order IN('INTEREST_FIRST','PRINCIPAL_FIRST')),
 interest_account_id uuid NOT NULL, reason varchar(500) NOT NULL CHECK(length(trim(reason))>0),
 request_key uuid NOT NULL, created_by uuid NOT NULL REFERENCES members(id), created_at timestamptz NOT NULL,
 UNIQUE(sacco_id,version), UNIQUE(sacco_id,effective_from), UNIQUE(sacco_id,request_key), UNIQUE(id,sacco_id),
 FOREIGN KEY(interest_account_id,sacco_id) REFERENCES gl_account(id,sacco_id)
);
CREATE TRIGGER repayment_policy_immutable BEFORE UPDATE OR DELETE ON repayment_accounting_policy FOR EACH ROW EXECUTE FUNCTION gl_protect_evidence();
ALTER TABLE loan_repayment_transactions ADD COLUMN allocation_policy_id uuid;
ALTER TABLE loan_repayment_transactions ADD FOREIGN KEY(allocation_policy_id,sacco_id) REFERENCES repayment_accounting_policy(id,sacco_id);
CREATE FUNCTION validate_repayment_policy_snapshot() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE p repayment_accounting_policy;
BEGIN
 IF NEW.kind='REVERSAL' THEN
   IF NEW.allocation_policy_id IS DISTINCT FROM (SELECT allocation_policy_id FROM loan_repayment_transactions WHERE id=NEW.reverses_transaction_id)
   THEN RAISE EXCEPTION 'A reversal must retain its original allocation policy'; END IF;
 ELSIF NEW.allocation_policy_id IS NOT NULL THEN
   SELECT * INTO STRICT p FROM repayment_accounting_policy WHERE id=NEW.allocation_policy_id AND sacco_id=NEW.sacco_id;
   IF p.effective_from>NEW.payment_date OR NOT EXISTS(SELECT 1 FROM accountant_loan_record WHERE loan_id=NEW.loan_application_id AND sacco_id=NEW.sacco_id AND station_id=NEW.station_id)
   THEN RAISE EXCEPTION 'Invalid repayment policy source or effective date'; END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER repayment_policy_snapshot BEFORE INSERT ON loan_repayment_transactions FOR EACH ROW EXECUTE FUNCTION validate_repayment_policy_snapshot();

CREATE TABLE receipt_batch (
 id uuid PRIMARY KEY, sacco_id varchar(255) NOT NULL REFERENCES registered_saccos(sacco_id), station_id varchar(255) NOT NULL,
 created_by uuid NOT NULL REFERENCES members(id), title varchar(120) NOT NULL, version integer NOT NULL DEFAULT 0,
 state varchar(24) NOT NULL DEFAULT 'DRAFT' CHECK(state IN('DRAFT','VALIDATING','READY','POSTING','COMPLETED','PARTIAL','CANCELLED')),
 worker_token uuid, lease_until timestamptz, cancel_requested boolean NOT NULL DEFAULT false,
 created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL, UNIQUE(id,sacco_id,station_id),
 FOREIGN KEY(sacco_id,station_id) REFERENCES sacco_stations(sacco_id,station_id)
);
CREATE INDEX ix_receipt_batch_scope ON receipt_batch(sacco_id,station_id,created_at DESC,id);
CREATE INDEX ix_receipt_batch_work ON receipt_batch(updated_at,id) WHERE state IN('VALIDATING','POSTING');
CREATE TABLE receipt_batch_item (
 id uuid PRIMARY KEY, batch_id uuid NOT NULL, sacco_id varchar(255) NOT NULL, station_id varchar(255) NOT NULL,
 line_no integer NOT NULL CHECK(line_no>0), version integer NOT NULL DEFAULT 0,
 state varchar(16) NOT NULL DEFAULT 'DRAFT' CHECK(state IN('DRAFT','READY','FAILED','POSTED')),
 form_json jsonb NOT NULL, payment_date date, total numeric(18,2) NOT NULL DEFAULT 0,
 client_label varchar(300) NOT NULL DEFAULT '', loan_labels jsonb NOT NULL DEFAULT '{}',
 preview_json jsonb, failure_key varchar(120), attempts integer NOT NULL DEFAULT 0, receipt_id uuid, posted_at timestamptz,
 UNIQUE(batch_id,line_no), FOREIGN KEY(batch_id,sacco_id,station_id) REFERENCES receipt_batch(id,sacco_id,station_id),
 FOREIGN KEY(receipt_id,sacco_id,station_id) REFERENCES accountant_loan_receipt(id,sacco_id,station_id),
 CHECK((state='POSTED')=(receipt_id IS NOT NULL)), CHECK(state<>'READY' OR preview_json IS NOT NULL)
);
CREATE INDEX ix_receipt_batch_items ON receipt_batch_item(batch_id,payment_date,line_no,id);
CREATE INDEX ix_receipt_batch_item_scope ON receipt_batch_item(sacco_id,station_id,batch_id,state);
CREATE FUNCTION protect_posted_batch_item() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF OLD.state='POSTED' THEN RAISE EXCEPTION 'Posted batch receipt links are immutable'; END IF;
 IF TG_OP='UPDATE' AND (OLD.id<>NEW.id OR OLD.batch_id<>NEW.batch_id OR OLD.sacco_id<>NEW.sacco_id OR OLD.station_id<>NEW.station_id OR OLD.line_no<>NEW.line_no)
 THEN RAISE EXCEPTION 'Batch item identity is immutable'; END IF;
 RETURN CASE WHEN TG_OP='DELETE' THEN OLD ELSE NEW END;
END $$;
CREATE TRIGGER batch_item_protection BEFORE UPDATE OR DELETE ON receipt_batch_item FOR EACH ROW EXECUTE FUNCTION protect_posted_batch_item();
CREATE FUNCTION receipt_batch_posted_source() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE r accountant_loan_receipt; b receipt_batch;
BEGIN
 IF NEW.state<>'POSTED' THEN RETURN NULL; END IF;
 SELECT * INTO STRICT r FROM accountant_loan_receipt WHERE id=NEW.receipt_id;
 SELECT * INTO STRICT b FROM receipt_batch WHERE id=NEW.batch_id;
 IF b.state<>'POSTING' OR r.request_key<>NEW.id OR r.posted_by<>b.created_by OR r.reverses_id IS NOT NULL OR r.total<>NEW.total
    OR r.client_id::text IS DISTINCT FROM (NEW.form_json->>'clientId') OR r.money_account_id::text IS DISTINCT FROM (NEW.form_json->>'moneyAccountId')
    OR r.effective_date IS DISTINCT FROM NEW.payment_date OR r.reference IS DISTINCT FROM btrim(NEW.form_json->>'reference')
    OR r.transaction_count<>jsonb_array_length(NEW.form_json->'rows')
 THEN RAISE EXCEPTION 'Batch result must link its complete original receipt'; END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER receipt_batch_source AFTER INSERT OR UPDATE ON receipt_batch_item DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION receipt_batch_posted_source();
