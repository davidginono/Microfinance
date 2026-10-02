CREATE TABLE accounting_supplier (
 id uuid PRIMARY KEY, sacco_id varchar(255) NOT NULL REFERENCES registered_saccos(sacco_id),
 station_id varchar(255) NOT NULL, name varchar(160) NOT NULL, evidence_reference varchar(500) NOT NULL,
 maker_id uuid NOT NULL REFERENCES members(id), active boolean NOT NULL DEFAULT true, created_at timestamptz NOT NULL,
 UNIQUE(id,sacco_id,station_id)
);
CREATE TABLE accounting_business_document (
 id uuid PRIMARY KEY, sacco_id varchar(255) NOT NULL REFERENCES registered_saccos(sacco_id), station_id varchar(255) NOT NULL,
 maker_id uuid NOT NULL REFERENCES members(id), checker_id uuid REFERENCES members(id), request_key uuid NOT NULL,
 kind varchar(40) NOT NULL, effective_date date NOT NULL, amount numeric NOT NULL CHECK(amount>0 AND amount=round(amount,2) AND amount<10000000000000000),
 loan_id uuid REFERENCES loan_applications(id), related_document_id uuid REFERENCES accounting_business_document(id),
 supplier_id uuid, description varchar(500) NOT NULL, evidence_reference varchar(500) NOT NULL,
 channel_reference varchar(100), money_account_key varchar(80), destination_branch varchar(255),
 command_json text NOT NULL, payload_hash varchar(64) NOT NULL, state varchar(24) NOT NULL CHECK(state IN('DRAFT','SUBMITTED','POSTING','POSTED','REJECTED')),
 journal_id uuid UNIQUE REFERENCES gl_journal(id), loan_transaction_id uuid REFERENCES loan_repayment_transactions(id),
 approval_evidence varchar(500), created_at timestamptz NOT NULL, posted_at timestamptz,
 principal numeric(18,2) NOT NULL DEFAULT 0, interest numeric(18,2) NOT NULL DEFAULT 0, fees numeric(18,2) NOT NULL DEFAULT 0,
 CHECK(checker_id IS NULL OR checker_id<>maker_id), CHECK((state='POSTED')=(posted_at IS NOT NULL)),
 FOREIGN KEY(supplier_id,sacco_id,station_id) REFERENCES accounting_supplier(id,sacco_id,station_id),
 UNIQUE(sacco_id,station_id,request_key), UNIQUE(id,sacco_id,station_id)
);
CREATE INDEX ix_business_branch_queue ON accounting_business_document(sacco_id,station_id,created_at DESC,id);
CREATE INDEX ix_business_loan_sources ON accounting_business_document(sacco_id,station_id,loan_id,kind,state,effective_date);
CREATE UNIQUE INDEX ux_business_money_reference ON accounting_business_document(sacco_id,station_id,money_account_key,channel_reference)
 WHERE channel_reference IS NOT NULL AND kind IN ('LOAN_DISBURSEMENT','LOAN_REPAYMENT','UNMATCHED_RECEIPT','LOAN_ADVANCE','DIRECT_EXPENSE','PAYABLE_PAYMENT','CAPITAL_RECEIPT','FUNDING_RECEIPT','RECOVERY','REFUND');
CREATE UNIQUE INDEX ux_business_disbursement_source ON accounting_business_document(loan_id) WHERE kind='LOAN_DISBURSEMENT' AND state<>'REJECTED';
CREATE UNIQUE INDEX ux_business_reversal ON accounting_business_document(related_document_id) WHERE kind IN('BUSINESS_REVERSAL','LOAN_REPAYMENT_REVERSAL') AND state<>'REJECTED';
CREATE TABLE accounting_business_control_entry (
 id uuid PRIMARY KEY, document_id uuid NOT NULL REFERENCES accounting_business_document(id),
 root_document_id uuid NOT NULL REFERENCES accounting_business_document(id), sacco_id varchar(255) NOT NULL,
 station_id varchar(255) NOT NULL, amount numeric NOT NULL CHECK(amount<>0 AND amount=round(amount,2) AND abs(amount)<10000000000000000),
 created_at timestamptz NOT NULL, UNIQUE(document_id,root_document_id),
 FOREIGN KEY(document_id,sacco_id,station_id) REFERENCES accounting_business_document(id,sacco_id,station_id)
);
CREATE INDEX ix_business_control_root ON accounting_business_control_entry(root_document_id,id);
CREATE TABLE accounting_fixed_asset (
 id uuid PRIMARY KEY REFERENCES accounting_business_document(id), sacco_id varchar(255) NOT NULL,
 station_id varchar(255) NOT NULL, description varchar(500) NOT NULL, acquired_on date NOT NULL,
 cost numeric(18,2) NOT NULL CHECK(cost>0), accumulated_depreciation numeric(18,2) NOT NULL DEFAULT 0 CHECK(accumulated_depreciation>=0 AND accumulated_depreciation<=cost),
 disposed boolean NOT NULL DEFAULT false, FOREIGN KEY(id,sacco_id,station_id) REFERENCES accounting_business_document(id,sacco_id,station_id)
);
CREATE INDEX ix_fixed_asset_branch ON accounting_fixed_asset(sacco_id,station_id,acquired_on DESC,id);
CREATE FUNCTION protect_business_document() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Accounting source documents cannot be deleted'; END IF;
 IF OLD.state IN('POSTED','REJECTED') THEN RAISE EXCEPTION 'Final accounting source documents are immutable'; END IF;
 IF ROW(NEW.id,NEW.sacco_id,NEW.station_id,NEW.maker_id,NEW.request_key,NEW.kind,NEW.effective_date,NEW.amount,NEW.loan_id,NEW.related_document_id,NEW.supplier_id,NEW.description,NEW.evidence_reference,NEW.channel_reference,NEW.money_account_key,NEW.destination_branch,NEW.command_json,NEW.payload_hash,NEW.created_at)
 IS DISTINCT FROM ROW(OLD.id,OLD.sacco_id,OLD.station_id,OLD.maker_id,OLD.request_key,OLD.kind,OLD.effective_date,OLD.amount,OLD.loan_id,OLD.related_document_id,OLD.supplier_id,OLD.description,OLD.evidence_reference,OLD.channel_reference,OLD.money_account_key,OLD.destination_branch,OLD.command_json,OLD.payload_hash,OLD.created_at)
 THEN RAISE EXCEPTION 'Source evidence and financial commands are immutable'; END IF;
 IF NOT ((OLD.state='DRAFT' AND NEW.state IN('SUBMITTED','REJECTED')) OR (OLD.state='SUBMITTED' AND NEW.state IN('POSTING','REJECTED')) OR (OLD.state='POSTING' AND NEW.state='POSTED')) THEN RAISE EXCEPTION 'Invalid source accounting transition'; END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER business_document_immutable BEFORE UPDATE OR DELETE ON accounting_business_document FOR EACH ROW EXECUTE FUNCTION protect_business_document();
CREATE TRIGGER business_control_immutable BEFORE UPDATE OR DELETE ON accounting_business_control_entry FOR EACH ROW EXECUTE FUNCTION protect_loan_financial_history();
CREATE FUNCTION validate_business_posted_source() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.state='POSTED' AND NOT EXISTS(SELECT 1 FROM gl_journal j WHERE j.id=NEW.journal_id AND j.state='POSTED' AND j.sacco_id=NEW.sacco_id AND j.station_id=NEW.station_id AND j.checker_id=NEW.checker_id AND j.source_reference=NEW.id::text)
 THEN RAISE EXCEPTION 'Posted business source requires its scoped independently posted GL journal'; END IF;
 RETURN NEW;
END; $$;
CREATE CONSTRAINT TRIGGER business_source_gl_agreement AFTER INSERT OR UPDATE ON accounting_business_document DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION validate_business_posted_source();
