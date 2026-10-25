-- A group receipt owns only a document envelope and attribution. Loan services own its cash journals.
CREATE TABLE lending_group_receipt (
 id uuid PRIMARY KEY,sacco_id varchar(255) NOT NULL,station_id varchar(255) NOT NULL,cycle_id uuid NOT NULL,
 number varchar(60) NOT NULL UNIQUE,request_key uuid NOT NULL,payload_hash varchar(64) NOT NULL,
 effective_date date NOT NULL,money_account_id uuid NOT NULL,reference varchar(80) NOT NULL CHECK(length(trim(reference))>0),
 remitter varchar(255) NOT NULL CHECK(length(trim(remitter))>0),evidence varchar(500) NOT NULL CHECK(length(trim(evidence))>0),
 description varchar(500) NOT NULL,group_code varchar(40) NOT NULL,group_name varchar(255) NOT NULL,
 input_data jsonb NOT NULL,total numeric(18,2) NOT NULL CHECK(total>0),transaction_count integer NOT NULL CHECK(transaction_count BETWEEN 1 AND 50),
 posted_by uuid NOT NULL REFERENCES members(id),posted_by_name varchar(255) NOT NULL,posted_at timestamptz NOT NULL,
 reverses_id uuid UNIQUE,
 UNIQUE(id,sacco_id,station_id),UNIQUE(sacco_id,station_id,request_key),
 FOREIGN KEY(cycle_id,sacco_id,station_id) REFERENCES lending_group_cycle(id,sacco_id,station_id),
 FOREIGN KEY(money_account_id,sacco_id) REFERENCES gl_account(id,sacco_id),
 FOREIGN KEY(reverses_id,sacco_id,station_id) REFERENCES lending_group_receipt(id,sacco_id,station_id)
);
CREATE INDEX ix_group_receipt_cycle ON lending_group_receipt(sacco_id,station_id,cycle_id,effective_date DESC,id DESC);
CREATE INDEX ix_group_receipt_register ON lending_group_receipt(sacco_id,station_id,effective_date DESC,id DESC);
CREATE UNIQUE INDEX ux_group_receipt_reference ON lending_group_receipt(sacco_id,station_id,money_account_id,reference) WHERE reverses_id IS NULL;
CREATE TABLE lending_group_receipt_loan (
 receipt_id uuid NOT NULL,sacco_id varchar(255) NOT NULL,station_id varchar(255) NOT NULL,
 line_no integer NOT NULL CHECK(line_no BETWEEN 1 AND 50),posting_id uuid NOT NULL UNIQUE,
 PRIMARY KEY(receipt_id,line_no),
 FOREIGN KEY(receipt_id,sacco_id,station_id) REFERENCES lending_group_receipt(id,sacco_id,station_id),
 FOREIGN KEY(posting_id,sacco_id,station_id) REFERENCES accountant_loan_post(id,sacco_id,station_id)
);
CREATE TABLE lending_group_contribution (
 id uuid PRIMARY KEY,receipt_id uuid NOT NULL,sacco_id varchar(255) NOT NULL,station_id varchar(255) NOT NULL,
 line_no integer NOT NULL CHECK(line_no BETWEEN 1 AND 50),loan_id uuid NOT NULL REFERENCES loan_applications(id),loan_number varchar(40) NOT NULL,
 payer_id uuid NOT NULL REFERENCES members(id),payer_number varchar(255) NOT NULL,payer_name varchar(255) NOT NULL,
 beneficiary_id uuid NOT NULL REFERENCES members(id),beneficiary_number varchar(255) NOT NULL,beneficiary_name varchar(255) NOT NULL,
 description varchar(500) NOT NULL,amount numeric(18,2) NOT NULL CHECK(amount>0),
 principal numeric(18,2) NOT NULL CHECK(principal>=0),interest numeric(18,2) NOT NULL CHECK(interest>=0),charges numeric(18,2) NOT NULL CHECK(charges>=0),
 reverses_id uuid UNIQUE REFERENCES lending_group_contribution(id),
 CHECK(amount=principal+interest+charges),UNIQUE(receipt_id,line_no),
 FOREIGN KEY(receipt_id,sacco_id,station_id) REFERENCES lending_group_receipt(id,sacco_id,station_id)
);
CREATE INDEX ix_group_contribution_loan ON lending_group_contribution(sacco_id,station_id,loan_id,receipt_id);
CREATE INDEX ix_group_contribution_payer ON lending_group_contribution(sacco_id,station_id,payer_id,receipt_id);
CREATE TRIGGER group_receipt_immutable BEFORE UPDATE OR DELETE ON lending_group_receipt FOR EACH ROW EXECUTE FUNCTION gl_protect_evidence();
CREATE TRIGGER group_receipt_loan_immutable BEFORE UPDATE OR DELETE ON lending_group_receipt_loan FOR EACH ROW EXECUTE FUNCTION gl_protect_evidence();
CREATE TRIGGER group_contribution_immutable BEFORE UPDATE OR DELETE ON lending_group_contribution FOR EACH ROW EXECUTE FUNCTION gl_protect_evidence();

CREATE FUNCTION lending_group_receipt_commit() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE r lending_group_receipt;original lending_group_receipt;cnt integer;received numeric;contributions integer;
BEGIN
 IF TG_TABLE_NAME='lending_group_receipt' THEN SELECT * INTO r FROM lending_group_receipt WHERE id=NEW.id;
 ELSE SELECT * INTO r FROM lending_group_receipt WHERE id=NEW.receipt_id; END IF;
 SELECT count(*),sum(p.amount) INTO cnt,received FROM lending_group_receipt_loan x JOIN accountant_loan_post p ON p.id=x.posting_id WHERE x.receipt_id=r.id;
 SELECT count(*) INTO contributions FROM lending_group_contribution WHERE receipt_id=r.id;
 IF cnt<>r.transaction_count OR received IS DISTINCT FROM r.total OR contributions NOT BETWEEN 1 AND 50
 OR (SELECT max(line_no) FROM lending_group_receipt_loan WHERE receipt_id=r.id)<>cnt
 OR (SELECT max(line_no) FROM lending_group_contribution WHERE receipt_id=r.id)<>contributions
 OR NOT EXISTS(SELECT 1 FROM lending_group_cycle c WHERE c.id=r.cycle_id AND c.state='SUBMITTED')
 OR NOT EXISTS(SELECT 1 FROM members m WHERE m.id=r.posted_by AND m.sacco_id=r.sacco_id AND m.station_id=r.station_id)
 OR NOT EXISTS(SELECT 1 FROM accounting_voucher_number_reservation n WHERE n.request_key=r.request_key AND n.sacco_id=r.sacco_id AND n.station_id=r.station_id
   AND n.voucher_type='RECEIPT' AND n.effective_date=r.effective_date AND n.voucher_number=r.number AND n.consumed_at IS NOT NULL)
 OR (SELECT count(DISTINCT p.loan_id) FROM lending_group_receipt_loan x JOIN accountant_loan_post p ON p.id=x.posting_id WHERE x.receipt_id=r.id)<>cnt
 OR EXISTS(SELECT 1 FROM lending_group_receipt_loan x JOIN accountant_loan_post p ON p.id=x.posting_id
   WHERE x.receipt_id=r.id AND (p.state<>'POSTED' OR p.actor_id<>r.posted_by OR p.effective_date<>r.effective_date OR p.money_account_id<>r.money_account_id
    OR (r.reverses_id IS NULL AND p.kind<>'PAYMENT') OR (r.reverses_id IS NOT NULL AND p.kind<>'REVERSAL')
    OR NOT EXISTS(SELECT 1 FROM lending_group_participant a WHERE a.cycle_id=r.cycle_id AND a.loan_id=p.loan_id)
    OR p.amount IS DISTINCT FROM (SELECT sum(c.amount) FROM lending_group_contribution c WHERE c.receipt_id=r.id AND c.loan_id=p.loan_id)
    OR p.principal_amount IS DISTINCT FROM (SELECT sum(c.principal) FROM lending_group_contribution c WHERE c.receipt_id=r.id AND c.loan_id=p.loan_id)
    OR p.interest_amount IS DISTINCT FROM (SELECT sum(c.interest) FROM lending_group_contribution c WHERE c.receipt_id=r.id AND c.loan_id=p.loan_id)
    OR p.amount-p.principal_amount-p.interest_amount IS DISTINCT FROM (SELECT sum(c.charges) FROM lending_group_contribution c WHERE c.receipt_id=r.id AND c.loan_id=p.loan_id)))
 OR EXISTS(SELECT 1 FROM lending_group_contribution c WHERE c.receipt_id=r.id AND
   (NOT EXISTS(SELECT 1 FROM lending_group_participant p WHERE p.cycle_id=r.cycle_id AND p.client_id=c.payer_id)
    OR NOT EXISTS(SELECT 1 FROM lending_group_participant b JOIN loan_applications l ON l.id=b.loan_id WHERE b.cycle_id=r.cycle_id
       AND b.loan_id=c.loan_id AND b.client_id=c.beneficiary_id AND coalesce(l.loan_id,l.application_number::text)=c.loan_number)
    OR NOT EXISTS(SELECT 1 FROM lending_group_receipt_loan x JOIN accountant_loan_post p ON p.id=x.posting_id WHERE x.receipt_id=r.id AND p.loan_id=c.loan_id)))
 THEN RAISE EXCEPTION 'Group receipt, loan postings and contributions must agree'; END IF;
 IF r.reverses_id IS NULL THEN
  IF EXISTS(SELECT 1 FROM lending_group_contribution WHERE receipt_id=r.id AND reverses_id IS NOT NULL) THEN RAISE EXCEPTION 'New contributions cannot reverse history'; END IF;
 ELSE
  SELECT * INTO original FROM lending_group_receipt WHERE id=r.reverses_id;
  IF original.reverses_id IS NOT NULL OR original.posted_by=r.posted_by OR original.total<>r.total OR original.cycle_id<>r.cycle_id
   OR original.transaction_count<>cnt OR original.money_account_id<>r.money_account_id OR r.effective_date<original.effective_date
   OR ROW(r.remitter,r.evidence,r.group_code,r.group_name,r.reference) IS DISTINCT FROM ROW(original.remitter,original.evidence,original.group_code,original.group_name,original.reference)
   OR contributions<>(SELECT count(*) FROM lending_group_contribution WHERE receipt_id=original.id)
   OR EXISTS(SELECT 1 FROM lending_group_receipt_loan x JOIN accountant_loan_post p ON p.id=x.posting_id WHERE x.receipt_id=r.id
      AND NOT EXISTS(SELECT 1 FROM lending_group_receipt_loan y WHERE y.receipt_id=original.id AND y.posting_id=p.reverses_id))
   OR EXISTS(SELECT 1 FROM lending_group_contribution c LEFT JOIN lending_group_contribution o ON o.id=c.reverses_id
      WHERE c.receipt_id=r.id AND (o.receipt_id IS DISTINCT FROM original.id OR
       ROW(c.line_no,c.loan_id,c.loan_number,c.payer_id,c.payer_number,c.payer_name,c.beneficiary_id,c.beneficiary_number,c.beneficiary_name,c.description,c.amount,c.principal,c.interest,c.charges) IS DISTINCT FROM
       ROW(o.line_no,o.loan_id,o.loan_number,o.payer_id,o.payer_number,o.payer_name,o.beneficiary_id,o.beneficiary_number,o.beneficiary_name,o.description,o.amount,o.principal,o.interest,o.charges)))
  THEN RAISE EXCEPTION 'Group correction must independently reverse every original contribution and loan posting'; END IF;
 END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER group_receipt_commit AFTER INSERT ON lending_group_receipt DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION lending_group_receipt_commit();
CREATE CONSTRAINT TRIGGER group_receipt_loan_commit AFTER INSERT ON lending_group_receipt_loan DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION lending_group_receipt_commit();
CREATE CONSTRAINT TRIGGER group_contribution_commit AFTER INSERT ON lending_group_contribution DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION lending_group_receipt_commit();

CREATE FUNCTION lending_group_receipt_correction() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF NEW.kind='REVERSAL' AND EXISTS(SELECT 1 FROM lending_group_receipt_loan WHERE posting_id=NEW.reverses_id)
 AND NOT EXISTS(SELECT 1 FROM lending_group_receipt_loan x JOIN lending_group_receipt r ON r.id=x.receipt_id
   JOIN lending_group_receipt_loan original ON original.posting_id=NEW.reverses_id WHERE x.posting_id=NEW.id AND r.reverses_id=original.receipt_id)
 THEN RAISE EXCEPTION 'Group collections require a complete linked receipt correction'; END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER group_receipt_correction AFTER INSERT ON accountant_loan_post DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION lending_group_receipt_correction();

-- Share the existing reference lock with ordinary and single-client receipts.
CREATE OR REPLACE FUNCTION receipt_reference_guard() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF NEW.reverses_id IS NOT NULL THEN RETURN NEW; END IF;
 IF TG_TABLE_NAME='accounting_voucher' THEN IF NEW.type<>'RECEIPT' THEN RETURN NEW; END IF; END IF;
 PERFORM pg_advisory_xact_lock(hashtextextended('RECEIPT_REFERENCE/'||NEW.sacco_id||'/'||NEW.station_id||'/'||NEW.money_account_id||'/'||NEW.reference,0));
 IF EXISTS(SELECT 1 FROM accounting_voucher WHERE sacco_id=NEW.sacco_id AND station_id=NEW.station_id AND money_account_id=NEW.money_account_id AND reference=NEW.reference AND type='RECEIPT' AND reverses_id IS NULL)
 OR EXISTS(SELECT 1 FROM accountant_loan_receipt WHERE sacco_id=NEW.sacco_id AND station_id=NEW.station_id AND money_account_id=NEW.money_account_id AND reference=NEW.reference AND reverses_id IS NULL)
 OR EXISTS(SELECT 1 FROM lending_group_receipt WHERE sacco_id=NEW.sacco_id AND station_id=NEW.station_id AND money_account_id=NEW.money_account_id AND reference=NEW.reference AND reverses_id IS NULL)
 OR EXISTS(SELECT 1 FROM accountant_loan_post WHERE sacco_id=NEW.sacco_id AND station_id=NEW.station_id AND money_account_id=NEW.money_account_id AND reference=NEW.reference AND kind='PAYMENT')
 THEN RAISE EXCEPTION 'Receipt payment reference is already reserved' USING ERRCODE='23505'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER group_receipt_reference BEFORE INSERT ON lending_group_receipt FOR EACH ROW EXECUTE FUNCTION receipt_reference_guard();

CREATE FUNCTION lending_group_receipt_request() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 PERFORM pg_advisory_xact_lock(hashtextextended('CORE/'||NEW.sacco_id||'/'||NEW.station_id||'/'||NEW.request_key,0));
 IF (TG_TABLE_NAME<>'accounting_voucher' AND EXISTS(SELECT 1 FROM accounting_voucher WHERE sacco_id=NEW.sacco_id AND station_id=NEW.station_id AND request_key=NEW.request_key))
 OR (TG_TABLE_NAME<>'accountant_loan_receipt' AND EXISTS(SELECT 1 FROM accountant_loan_receipt WHERE sacco_id=NEW.sacco_id AND station_id=NEW.station_id AND request_key=NEW.request_key))
 OR (TG_TABLE_NAME<>'lending_group_receipt' AND EXISTS(SELECT 1 FROM lending_group_receipt WHERE sacco_id=NEW.sacco_id AND station_id=NEW.station_id AND request_key=NEW.request_key))
 THEN RAISE EXCEPTION 'Receipt request already belongs to another document' USING ERRCODE='23505'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER group_receipt_request BEFORE INSERT ON lending_group_receipt FOR EACH ROW EXECUTE FUNCTION lending_group_receipt_request();
CREATE TRIGGER core_group_receipt_request BEFORE INSERT ON accounting_voucher FOR EACH ROW EXECUTE FUNCTION lending_group_receipt_request();
CREATE TRIGGER client_group_receipt_request BEFORE INSERT ON accountant_loan_receipt FOR EACH ROW EXECUTE FUNCTION lending_group_receipt_request();

CREATE FUNCTION loan_receipt_single_envelope() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 PERFORM pg_advisory_xact_lock(hashtextextended('LOAN_RECEIPT_OWNER/'||NEW.posting_id,0));
 IF (TG_TABLE_NAME='lending_group_receipt_loan' AND EXISTS(SELECT 1 FROM accountant_loan_receipt_row WHERE posting_id=NEW.posting_id))
 OR (TG_TABLE_NAME='accountant_loan_receipt_row' AND EXISTS(SELECT 1 FROM lending_group_receipt_loan WHERE posting_id=NEW.posting_id))
 THEN RAISE EXCEPTION 'A loan posting may belong to only one receipt envelope'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER group_receipt_single_envelope BEFORE INSERT ON lending_group_receipt_loan FOR EACH ROW EXECUTE FUNCTION loan_receipt_single_envelope();
CREATE TRIGGER client_receipt_single_envelope BEFORE INSERT ON accountant_loan_receipt_row FOR EACH ROW EXECUTE FUNCTION loan_receipt_single_envelope();
