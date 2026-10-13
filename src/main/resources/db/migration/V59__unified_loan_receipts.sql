-- A receipt groups loan-owned postings. It never creates a second collection journal.
CREATE TABLE accountant_loan_receipt (
 id uuid PRIMARY KEY,sacco_id varchar(255) NOT NULL,station_id varchar(255) NOT NULL,
 number varchar(60) NOT NULL UNIQUE,request_key uuid NOT NULL,payload_hash varchar(64) NOT NULL,
 effective_date date NOT NULL,client_id uuid NOT NULL REFERENCES members(id),party varchar(255) NOT NULL,
 client_number varchar(255) NOT NULL,reference varchar(80) NOT NULL CHECK(length(trim(reference))>0),
 description varchar(500) NOT NULL,evidence varchar(500) NOT NULL,money_account_id uuid NOT NULL,
 total numeric(18,2) NOT NULL CHECK(total>0),transaction_count integer NOT NULL CHECK(transaction_count BETWEEN 1 AND 50),
 posted_by uuid NOT NULL REFERENCES members(id),posted_by_name varchar(255) NOT NULL,posted_at timestamptz NOT NULL,
 reverses_id uuid UNIQUE,
 UNIQUE(id,sacco_id,station_id),UNIQUE(sacco_id,station_id,request_key),
 FOREIGN KEY(sacco_id,station_id) REFERENCES sacco_stations(sacco_id,station_id),
 FOREIGN KEY(money_account_id,sacco_id) REFERENCES gl_account(id,sacco_id),
 FOREIGN KEY(reverses_id,sacco_id,station_id) REFERENCES accountant_loan_receipt(id,sacco_id,station_id)
);
CREATE UNIQUE INDEX ux_loan_receipt_reference ON accountant_loan_receipt(sacco_id,station_id,money_account_id,reference) WHERE reverses_id IS NULL;
CREATE INDEX ix_loan_receipt_register ON accountant_loan_receipt(sacco_id,station_id,effective_date DESC,id DESC);
CREATE TABLE accountant_loan_receipt_row (
 receipt_id uuid NOT NULL,sacco_id varchar(255) NOT NULL,station_id varchar(255) NOT NULL,
 line_no integer NOT NULL CHECK(line_no BETWEEN 1 AND 50),posting_id uuid NOT NULL UNIQUE,
 description varchar(500) NOT NULL,
 PRIMARY KEY(receipt_id,line_no),
 FOREIGN KEY(receipt_id,sacco_id,station_id) REFERENCES accountant_loan_receipt(id,sacco_id,station_id),
 FOREIGN KEY(posting_id,sacco_id,station_id) REFERENCES accountant_loan_post(id,sacco_id,station_id)
);
CREATE TRIGGER loan_receipt_immutable BEFORE UPDATE OR DELETE ON accountant_loan_receipt FOR EACH ROW EXECUTE FUNCTION gl_protect_evidence();
CREATE TRIGGER loan_receipt_row_immutable BEFORE UPDATE OR DELETE ON accountant_loan_receipt_row FOR EACH ROW EXECUTE FUNCTION gl_protect_evidence();
CREATE FUNCTION accountant_loan_receipt_commit() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE r accountant_loan_receipt; original accountant_loan_receipt; cnt integer; amount numeric;
BEGIN
 IF TG_TABLE_NAME='accountant_loan_receipt' THEN
  SELECT * INTO r FROM accountant_loan_receipt WHERE id=NEW.id;
 ELSE
  SELECT * INTO r FROM accountant_loan_receipt WHERE id=NEW.receipt_id;
 END IF;
 SELECT count(*),sum(p.amount) INTO cnt,amount FROM accountant_loan_receipt_row x JOIN accountant_loan_post p ON p.id=x.posting_id WHERE x.receipt_id=r.id;
 IF cnt<>r.transaction_count OR amount IS DISTINCT FROM r.total OR
 (SELECT count(DISTINCT p.loan_id) FROM accountant_loan_receipt_row x JOIN accountant_loan_post p ON p.id=x.posting_id WHERE x.receipt_id=r.id)<>cnt OR
 (SELECT max(line_no) FROM accountant_loan_receipt_row WHERE receipt_id=r.id)<>cnt OR
 EXISTS(SELECT 1 FROM accountant_loan_receipt_row x JOIN accountant_loan_post p ON p.id=x.posting_id
 JOIN loan_applications a ON a.id=p.loan_id WHERE x.receipt_id=r.id AND
 (p.state<>'POSTED' OR p.actor_id<>r.posted_by OR p.effective_date<>r.effective_date OR p.money_account_id<>r.money_account_id
 OR a.applicant_member_id<>r.client_id OR (r.reverses_id IS NULL AND p.kind<>'PAYMENT') OR (r.reverses_id IS NOT NULL AND p.kind<>'REVERSAL')))
 THEN RAISE EXCEPTION 'Receipt and loan postings must agree'; END IF;
 IF r.reverses_id IS NOT NULL THEN
  SELECT * INTO original FROM accountant_loan_receipt WHERE id=r.reverses_id;
  IF original.reverses_id IS NOT NULL OR original.posted_by=r.posted_by OR original.total<>r.total
   OR original.transaction_count<>cnt OR original.client_id<>r.client_id OR original.money_account_id<>r.money_account_id
   OR EXISTS(SELECT 1 FROM accountant_loan_receipt_row x JOIN accountant_loan_post p ON p.id=x.posting_id
      WHERE x.receipt_id=r.id AND NOT EXISTS(SELECT 1 FROM accountant_loan_receipt_row y WHERE y.receipt_id=original.id AND y.posting_id=p.reverses_id))
  THEN RAISE EXCEPTION 'Receipt reversal must reverse the original collection completely'; END IF;
 END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER loan_receipt_commit AFTER INSERT ON accountant_loan_receipt DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION accountant_loan_receipt_commit();
CREATE CONSTRAINT TRIGGER loan_receipt_row_commit AFTER INSERT ON accountant_loan_receipt_row DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION accountant_loan_receipt_commit();
CREATE FUNCTION loan_receipt_correction_commit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF NEW.kind='REVERSAL' AND EXISTS(SELECT 1 FROM accountant_loan_receipt_row WHERE posting_id=NEW.reverses_id)
 AND NOT EXISTS(SELECT 1 FROM accountant_loan_receipt_row x JOIN accountant_loan_receipt r ON r.id=x.receipt_id
   JOIN accountant_loan_receipt_row original ON original.posting_id=NEW.reverses_id
   WHERE x.posting_id=NEW.id AND r.reverses_id=original.receipt_id)
 THEN RAISE EXCEPTION 'Grouped loan payments require a complete receipt correction'; END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER loan_receipt_correction_commit AFTER INSERT ON accountant_loan_post DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION loan_receipt_correction_commit();
CREATE FUNCTION receipt_reference_guard() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF NEW.reverses_id IS NOT NULL THEN RETURN NEW; END IF;
 IF TG_TABLE_NAME='accounting_voucher' THEN IF NEW.type<>'RECEIPT' THEN RETURN NEW; END IF; END IF;
 PERFORM pg_advisory_xact_lock(hashtextextended('RECEIPT_REFERENCE/'||NEW.sacco_id||'/'||NEW.station_id||'/'||NEW.money_account_id||'/'||NEW.reference,0));
 IF EXISTS(SELECT 1 FROM accounting_voucher WHERE sacco_id=NEW.sacco_id AND station_id=NEW.station_id AND money_account_id=NEW.money_account_id AND reference=NEW.reference AND type='RECEIPT' AND reverses_id IS NULL)
 OR EXISTS(SELECT 1 FROM accountant_loan_receipt WHERE sacco_id=NEW.sacco_id AND station_id=NEW.station_id AND money_account_id=NEW.money_account_id AND reference=NEW.reference AND reverses_id IS NULL)
 OR EXISTS(SELECT 1 FROM accountant_loan_post WHERE sacco_id=NEW.sacco_id AND station_id=NEW.station_id AND money_account_id=NEW.money_account_id AND reference=NEW.reference AND kind='PAYMENT')
 THEN RAISE EXCEPTION 'Receipt payment reference is already reserved' USING ERRCODE='23505'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER receipt_reference_guard BEFORE INSERT ON accountant_loan_receipt FOR EACH ROW EXECUTE FUNCTION receipt_reference_guard();
CREATE TRIGGER core_receipt_reference_guard BEFORE INSERT ON accounting_voucher FOR EACH ROW EXECUTE FUNCTION receipt_reference_guard();
