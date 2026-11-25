-- Both reserved staff vouchers and automatically issued documents draw from the same sequence.
CREATE FUNCTION accounting_next_voucher_number(kind text, effective_day date)
RETURNS text LANGUAGE plpgsql VOLATILE STRICT AS $$
DECLARE prefix text; serial text;
BEGIN
 CASE kind
  WHEN 'RECEIPT' THEN prefix := 'RV';
  WHEN 'PAYMENT' THEN prefix := 'PV';
  WHEN 'JOURNAL' THEN prefix := 'JV';
  ELSE RAISE EXCEPTION 'Unknown voucher type %', kind;
 END CASE;
 serial := nextval('accounting_voucher_number_seq')::text;
 RETURN prefix || '-' || extract(year FROM effective_day)::integer || '-' || lpad(serial, greatest(8,length(serial)), '0');
END $$;

-- These documents use the original source's posted journal and its immutable lines.
CREATE TABLE accounting_system_voucher (
 journal_id uuid PRIMARY KEY,
 sacco_id varchar(255) NOT NULL, station_id varchar(255) NOT NULL,
 number varchar(40) NOT NULL UNIQUE, type varchar(10) NOT NULL CHECK(type IN ('RECEIPT','PAYMENT','JOURNAL')),
 effective_date date NOT NULL, money_account_id uuid, total numeric NOT NULL CHECK(total>0),
 issued_at timestamptz NOT NULL,
 FOREIGN KEY(journal_id,sacco_id,station_id) REFERENCES gl_journal(id,sacco_id,station_id),
 FOREIGN KEY(money_account_id,sacco_id) REFERENCES gl_account(id,sacco_id),
 CHECK((type='JOURNAL')=(money_account_id IS NULL))
);
CREATE INDEX ix_system_voucher_register ON accounting_system_voucher(sacco_id,station_id,type,effective_date DESC,journal_id DESC);
CREATE TRIGGER system_voucher_immutable BEFORE UPDATE OR DELETE ON accounting_system_voucher
 FOR EACH ROW EXECUTE FUNCTION gl_protect_evidence();

CREATE FUNCTION accounting_system_voucher_header() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE j gl_journal; p accountant_loan_post; cash_amount numeric; journal_amount numeric;
BEGIN
 SELECT * INTO STRICT j FROM gl_journal WHERE id=NEW.journal_id AND direct_post AND state='POSTED';
 SELECT * INTO p FROM accountant_loan_post WHERE journal_id=j.id;

 IF p.id IS NOT NULL AND
    (EXISTS(SELECT 1 FROM accountant_loan_receipt_row WHERE posting_id=p.id) OR
     EXISTS(SELECT 1 FROM lending_group_receipt_loan WHERE posting_id=p.id)) THEN
  RETURN NULL; -- The existing, possibly multi-loan, receipt is already the document.
 END IF;

 NEW.sacco_id := j.sacco_id;
 NEW.station_id := j.station_id;
 NEW.effective_date := j.effective_date;
 NEW.money_account_id := NULL;
 CASE
  WHEN j.source_type IN ('LOAN_RECORD_PAYMENT','LOAN_RECORD_REVERSAL','LOAN_CHARGE_RECEIPT','LOAN_CHARGE_REVERSAL') THEN
   NEW.type := 'RECEIPT';
   NEW.money_account_id := p.money_account_id;
  WHEN j.source_type IN ('LOAN_RECORD_DISBURSEMENT','LOAN_CHARGE_FUNDING') THEN
   NEW.type := 'PAYMENT';
   NEW.money_account_id := p.money_account_id;
  WHEN j.source_type IN ('LOAN_TOPUP','LOAN_TOPUP_UNDO') THEN
   NEW.type := 'PAYMENT';
   SELECT original.money_account_id INTO NEW.money_account_id
    FROM accountant_loan_topup t JOIN accountant_loan_post original ON original.id=t.posting_id
    WHERE t.journal_id=j.id;
  WHEN j.source_type='LOAN_ADJUSTMENT' THEN
   -- Only a funding correction has a money account. Provision and write-off corrections are journals.
   SELECT original.money_account_id INTO NEW.money_account_id
    FROM accountant_loan_post original WHERE original.journal_id=j.reverses_id AND original.kind='DISBURSEMENT';
   NEW.type := CASE WHEN NEW.money_account_id IS NULL THEN 'JOURNAL' ELSE 'PAYMENT' END;
  WHEN j.source_type IN ('LOAN_CHARGE_ASSESS','LOAN_CHARGE_UNDO','LOAN_FEE_CUTOVER') THEN
   NEW.type := 'JOURNAL';
  ELSE RAISE EXCEPTION 'Missing automatic voucher classification for %', j.source_type;
 END CASE;

 SELECT sum(debit), abs(sum(debit-credit) FILTER(WHERE account_id=NEW.money_account_id))
 INTO journal_amount,cash_amount FROM gl_journal_line WHERE journal_id=j.id;
 IF NEW.type<>'JOURNAL' AND NEW.money_account_id IS NULL THEN
  RAISE EXCEPTION 'Missing source money account for voucher on journal %',j.id;
 END IF;
 -- A fully offset refinancing or funding journal has no cash to pay.
 IF NEW.type='PAYMENT' AND coalesce(cash_amount,0)=0 THEN
  NEW.type := 'JOURNAL';
  NEW.money_account_id := NULL;
 END IF;
 NEW.total := CASE WHEN NEW.type='JOURNAL' THEN journal_amount ELSE cash_amount END;
 NEW.number := accounting_next_voucher_number(NEW.type, j.effective_date);
 RETURN NEW;
END $$;
CREATE TRIGGER system_voucher_header BEFORE INSERT ON accounting_system_voucher
 FOR EACH ROW EXECUTE FUNCTION accounting_system_voucher_header();

CREATE FUNCTION accounting_issue_system_voucher() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 INSERT INTO accounting_system_voucher(journal_id,issued_at) VALUES(NEW.id,NEW.posted_at);
 RETURN NULL;
END $$;
-- Loan receipts attach their rows after the source journal posts, in the same transaction.
CREATE CONSTRAINT TRIGGER system_voucher_on_post AFTER INSERT OR UPDATE OF state ON gl_journal
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW
 WHEN(NEW.state='POSTED' AND NEW.direct_post AND left(NEW.source_type,5)='LOAN_')
 EXECUTE FUNCTION accounting_issue_system_voucher();

CREATE FUNCTION accounting_receipt_document_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF EXISTS(SELECT 1 FROM accountant_loan_post p JOIN accounting_system_voucher v ON v.journal_id=p.journal_id
           WHERE p.id=NEW.posting_id) THEN
  RAISE EXCEPTION 'This posting already has an issued voucher';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER system_voucher_receipt_guard BEFORE INSERT ON accountant_loan_receipt_row
 FOR EACH ROW EXECUTE FUNCTION accounting_receipt_document_guard();
CREATE TRIGGER system_voucher_group_receipt_guard BEFORE INSERT ON lending_group_receipt_loan
 FOR EACH ROW EXECUTE FUNCTION accounting_receipt_document_guard();

-- Record when historical documents were actually issued; leave originals and frozen reports intact.
INSERT INTO accounting_system_voucher(journal_id,issued_at)
 SELECT id,statement_timestamp() FROM gl_journal
 WHERE state='POSTED' AND direct_post AND left(source_type,5)='LOAN_'
 ORDER BY posted_at,id;
