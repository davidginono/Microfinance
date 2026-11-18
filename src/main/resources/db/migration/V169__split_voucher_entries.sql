-- Keep posted pair evidence intact; new split transactions carry their own immutable entries.
ALTER TABLE accounting_voucher_transaction ADD COLUMN entry_format smallint NOT NULL DEFAULT 1 CHECK(entry_format IN (1,2));
ALTER TABLE accounting_voucher_transaction ADD CONSTRAINT ux_voucher_transaction_scope UNIQUE(id,sacco_id,station_id);
ALTER TABLE accounting_voucher_transaction
 ALTER COLUMN debit_account_id DROP NOT NULL, ALTER COLUMN credit_account_id DROP NOT NULL,
 ALTER COLUMN debit_code DROP NOT NULL, ALTER COLUMN debit_name DROP NOT NULL,
 ALTER COLUMN credit_code DROP NOT NULL, ALTER COLUMN credit_name DROP NOT NULL;
ALTER TABLE accounting_voucher_transaction ADD CONSTRAINT voucher_entry_shape CHECK
 ((entry_format=1 AND debit_account_id IS NOT NULL AND credit_account_id IS NOT NULL AND debit_code IS NOT NULL AND debit_name IS NOT NULL AND credit_code IS NOT NULL AND credit_name IS NOT NULL)
 OR (entry_format=2 AND debit_account_id IS NULL AND credit_account_id IS NULL AND debit_code IS NULL AND debit_name IS NULL AND credit_code IS NULL AND credit_name IS NULL AND transaction_id IS NULL AND template_key IS NULL AND component='TOTAL'));

CREATE TABLE accounting_voucher_entry (
 transaction_id uuid NOT NULL, sacco_id varchar(255) NOT NULL, station_id varchar(255) NOT NULL,
 entry_no smallint NOT NULL CHECK(entry_no BETWEEN 1 AND 50), side varchar(6) NOT NULL CHECK(side IN ('DEBIT','CREDIT')),
 account_id uuid NOT NULL, account_code varchar(40) NOT NULL, account_name varchar(160) NOT NULL,
 amount numeric NOT NULL CHECK(amount>0 AND scale(amount)<=2 AND amount<10000000000000000),
 PRIMARY KEY(transaction_id,entry_no), UNIQUE(transaction_id,account_id),
 FOREIGN KEY(transaction_id,sacco_id,station_id) REFERENCES accounting_voucher_transaction(id,sacco_id,station_id),
 FOREIGN KEY(account_id,sacco_id) REFERENCES gl_account(id,sacco_id)
);
CREATE VIEW accounting_voucher_posting AS
 SELECT t.id transaction_id,t.voucher_id,t.sacco_id,t.station_id,1 entry_no,'DEBIT'::text side,
 t.debit_account_id account_id,t.debit_code account_code,t.debit_name account_name,t.amount
 FROM accounting_voucher_transaction t WHERE entry_format=1
 UNION ALL
 SELECT t.id,t.voucher_id,t.sacco_id,t.station_id,2,'CREDIT',t.credit_account_id,t.credit_code,t.credit_name,t.amount
 FROM accounting_voucher_transaction t WHERE entry_format=1
 UNION ALL
 SELECT t.id,t.voucher_id,e.sacco_id,e.station_id,e.entry_no,e.side,e.account_id,e.account_code,e.account_name,e.amount
 FROM accounting_voucher_entry e JOIN accounting_voucher_transaction t ON t.id=e.transaction_id WHERE t.entry_format=2;

-- One reservation per money account, including all historical ordinary vouchers.
CREATE TABLE accounting_voucher_money_reference (
 voucher_id uuid NOT NULL, sacco_id varchar(255) NOT NULL, station_id varchar(255) NOT NULL,
 account_id uuid NOT NULL, reference varchar(160) NOT NULL,
 PRIMARY KEY(sacco_id,account_id,reference), UNIQUE(voucher_id,account_id),
 FOREIGN KEY(voucher_id,sacco_id,station_id) REFERENCES accounting_voucher(id,sacco_id,station_id),
 FOREIGN KEY(account_id,sacco_id) REFERENCES gl_account(id,sacco_id)
);
INSERT INTO accounting_voucher_money_reference
 SELECT id,sacco_id,station_id,money_account_id,reference FROM accounting_voucher WHERE type<>'JOURNAL' AND reverses_id IS NULL;

CREATE FUNCTION core_entry_insert_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE voucher uuid; valid boolean;
BEGIN
 IF TG_TABLE_NAME='accounting_voucher_entry' THEN
  SELECT voucher_id,entry_format=2 INTO voucher,valid FROM accounting_voucher_transaction WHERE id=NEW.transaction_id;
 ELSE
  voucher:=NEW.voucher_id;valid:=true;
 END IF;
 IF NOT coalesce(valid,false) THEN RAISE EXCEPTION 'Split transaction required'; END IF;
 PERFORM 1 FROM accounting_voucher v JOIN gl_journal j ON j.id=v.journal_id WHERE v.id=voucher AND j.state='DRAFT' FOR UPDATE OF j;
 IF NOT FOUND THEN RAISE EXCEPTION 'Cannot append to posted voucher'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER core_entry_insert BEFORE INSERT ON accounting_voucher_entry FOR EACH ROW EXECUTE FUNCTION core_entry_insert_guard();
CREATE TRIGGER core_reference_insert BEFORE INSERT ON accounting_voucher_money_reference FOR EACH ROW EXECUTE FUNCTION core_entry_insert_guard();
CREATE TRIGGER core_entry_immutable BEFORE UPDATE OR DELETE ON accounting_voucher_entry FOR EACH ROW EXECUTE FUNCTION gl_protect_evidence();
CREATE TRIGGER core_reference_immutable BEFORE UPDATE OR DELETE ON accounting_voucher_money_reference FOR EACH ROW EXECUTE FUNCTION gl_protect_evidence();

CREATE FUNCTION validate_core_voucher_post(j gl_journal) RETURNS void LANGUAGE plpgsql AS $$
DECLARE v accounting_voucher; p accounting_period; original gl_journal; cnt integer; difference numeric;
BEGIN
 SELECT * INTO p FROM accounting_period WHERE id=j.period_id;
 IF p.id IS NULL OR p.state<>'OPEN' OR j.effective_date NOT BETWEEN p.starts_on AND p.ends_on THEN RAISE EXCEPTION 'Open period required'; END IF;
 IF j.effective_date>CURRENT_DATE THEN RAISE EXCEPTION 'Future posting date is invalid'; END IF;
 IF EXISTS(SELECT 1 FROM gl_journal_line l JOIN gl_account a ON a.id=l.account_id WHERE l.journal_id=j.id
   AND (NOT a.active OR a.kind<>'POSTING' OR gl_account_requires_loan_source(a.id))) THEN RAISE EXCEPTION 'Core vouchers require ordinary active posting accounts'; END IF;
 SELECT count(*),sum(debit-credit) INTO cnt,difference FROM gl_journal_line WHERE journal_id=j.id;
 IF cnt NOT BETWEEN 2 AND 500 OR difference<>0 THEN RAISE EXCEPTION 'Balanced lines required'; END IF;
 SELECT * INTO v FROM accounting_voucher WHERE journal_id=j.id;
 IF v.id IS NULL OR ROW(v.sacco_id,v.station_id,v.posted_by,v.number,v.effective_date,v.posted_at,v.request_key,v.payload_hash)
   IS DISTINCT FROM ROW(j.sacco_id,j.station_id,j.maker_id,j.source_reference,j.effective_date,j.posted_at,j.request_key,j.payload_hash)
   OR NOT ((v.reverses_id IS NULL AND j.reverses_id IS NULL AND j.source_type='CORE_'||v.type)
     OR (v.reverses_id IS NOT NULL AND j.reverses_id IS NOT NULL AND j.source_type='CORE_REVERSAL'))
   OR v.transaction_count<>(SELECT count(*) FROM accounting_voucher_transaction WHERE voucher_id=v.id)
   OR v.total IS DISTINCT FROM (SELECT sum(amount) FROM accounting_voucher_transaction WHERE voucher_id=v.id)
 THEN RAISE EXCEPTION 'Voucher and journal must agree'; END IF;
 IF EXISTS(SELECT 1 FROM accounting_voucher_transaction t LEFT JOIN accounting_voucher_posting e ON e.transaction_id=t.id
   WHERE t.voucher_id=v.id GROUP BY t.id,t.amount
   HAVING count(e.transaction_id) NOT BETWEEN 2 AND 50
      OR coalesce(sum(e.amount) FILTER(WHERE e.side='DEBIT'),0)<>t.amount
      OR coalesce(sum(e.amount) FILTER(WHERE e.side='CREDIT'),0)<>t.amount)
 THEN RAISE EXCEPTION 'Each voucher transaction must balance'; END IF;
 IF j.reverses_id IS NULL AND v.type<>'JOURNAL' THEN
  IF NOT EXISTS(SELECT 1 FROM accounting_voucher_posting e WHERE e.voucher_id=v.id AND e.account_id=v.money_account_id AND e.side=CASE v.type WHEN 'RECEIPT' THEN 'DEBIT' ELSE 'CREDIT' END)
   OR EXISTS(SELECT 1 FROM accounting_voucher_posting e JOIN gl_account a ON a.id=e.account_id WHERE e.voucher_id=v.id
     AND ((e.side=CASE v.type WHEN 'RECEIPT' THEN 'DEBIT' ELSE 'CREDIT' END) IS DISTINCT FROM (a.type='ASSET' AND a.purpose IN ('CASH','BANK','MOBILE_MONEY'))))
  THEN RAISE EXCEPTION 'Voucher money account direction must agree'; END IF;
  IF EXISTS((SELECT DISTINCT e.account_id,v.reference FROM accounting_voucher_posting e WHERE e.voucher_id=v.id AND e.side=CASE v.type WHEN 'RECEIPT' THEN 'DEBIT' ELSE 'CREDIT' END)
      EXCEPT (SELECT account_id,reference FROM accounting_voucher_money_reference WHERE voucher_id=v.id))
    OR EXISTS((SELECT account_id,reference FROM accounting_voucher_money_reference WHERE voucher_id=v.id)
      EXCEPT (SELECT DISTINCT e.account_id,v.reference FROM accounting_voucher_posting e WHERE e.voucher_id=v.id AND e.side=CASE v.type WHEN 'RECEIPT' THEN 'DEBIT' ELSE 'CREDIT' END))
  THEN RAISE EXCEPTION 'Voucher money references must match'; END IF;
 END IF;
 IF EXISTS((SELECT account_id,debit,credit FROM gl_journal_line WHERE journal_id=j.id)
   EXCEPT ALL (SELECT account_id,CASE side WHEN 'DEBIT' THEN amount ELSE 0 END,CASE side WHEN 'CREDIT' THEN amount ELSE 0 END FROM accounting_voucher_posting WHERE voucher_id=v.id))
   OR cnt<>(SELECT count(*) FROM accounting_voucher_posting WHERE voucher_id=v.id)
 THEN RAISE EXCEPTION 'Voucher transaction lines must match journal'; END IF;
 IF j.reverses_id IS NOT NULL THEN
  SELECT * INTO original FROM gl_journal WHERE id=j.reverses_id FOR UPDATE;
  IF NOT original.direct_post OR original.state<>'POSTED' OR original.reverses_id IS NOT NULL OR j.effective_date<original.effective_date
    OR NOT EXISTS(SELECT 1 FROM accounting_voucher o WHERE o.id=v.reverses_id AND o.journal_id=original.id)
    OR EXISTS((SELECT account_id,debit,credit FROM gl_journal_line WHERE journal_id=j.id) EXCEPT ALL (SELECT account_id,credit,debit FROM gl_journal_line WHERE journal_id=original.id))
    OR cnt<>(SELECT count(*) FROM gl_journal_line WHERE journal_id=original.id)
  THEN RAISE EXCEPTION 'Exact linked reversal required'; END IF;
 END IF;
 IF NOT EXISTS(SELECT 1 FROM accounting_outbox WHERE journal_id=j.id AND sacco_id=j.sacco_id AND station_id=j.station_id) THEN RAISE EXCEPTION 'Accounting outbox required'; END IF;
END $$;

-- Replace only the ordinary core branch. Later loan-source protections remain installed.
DO $$
DECLARE definition text; start_at integer; end_at integer;
BEGIN
 SELECT pg_get_functiondef('gl_validate_post()'::regprocedure) INTO definition;
 start_at:=strpos(definition,' IF NEW.direct_post THEN');
 end_at:=strpos(definition,' RAISE EXCEPTION ''Legacy accounting workflows are retired'';');
 IF start_at=0 OR end_at<=start_at THEN RAISE EXCEPTION 'Core posting branch not found'; END IF;
 definition:=substring(definition,1,start_at-1)||E' IF NEW.direct_post THEN\n  PERFORM validate_core_voucher_post(NEW);\n  RETURN NEW;\n END IF;\n'||substring(definition,end_at);
 EXECUTE definition;
END $$;
