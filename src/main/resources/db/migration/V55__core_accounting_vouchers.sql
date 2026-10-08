-- Core vouchers post directly into the existing GL. Historical approval workflows retain their protections.
-- Direct posting and PDF/print access are part of the user-requested accountant capability.
INSERT INTO member_access_claims(member_id,claim_name)
SELECT m.id,c.claim_name FROM members m
CROSS JOIN (VALUES ('ACCOUNTING_BUSINESS_VIEW'),('ACCOUNTING_BUSINESS_CREATE'),('ACCOUNTING_BUSINESS_REVERSE'),
 ('ACCOUNTING_JOURNALS_VIEW'),('ACCOUNTING_JOURNALS_CREATE'),('ACCOUNTING_JOURNALS_REVERSE'),('FINANCIAL_REPORTS_EXPORT')) c(claim_name)
WHERE m.status='ACTIVE' AND m.sacco_id IS NOT NULL AND m.station_id IS NOT NULL
 AND (m.staff_access_status='ACTIVE' OR (m.staff_access_status='NONE' AND coalesce(m.staff_no,'')='' AND m.staff_access_assigned_at IS NULL AND m.staff_access_activated_at IS NULL))
 AND (m.position='ACCOUNTANT' OR EXISTS(SELECT 1 FROM member_staff_roles r WHERE r.member_id=m.id AND r.role_name='ACCOUNTANT'))
 AND m.position IS DISTINCT FROM 'ADMIN' AND NOT EXISTS(SELECT 1 FROM member_staff_roles r WHERE r.member_id=m.id AND r.role_name='ADMIN')
 AND EXISTS(SELECT 1 FROM member_access_claims old WHERE old.member_id=m.id)
ON CONFLICT DO NOTHING;
ALTER TABLE accounting_period ALTER COLUMN policy_id DROP NOT NULL;
ALTER TABLE gl_journal ALTER COLUMN policy_id DROP NOT NULL;
ALTER TABLE gl_journal ADD COLUMN direct_post boolean NOT NULL DEFAULT false;
DO $$
DECLARE c record; expression text;
BEGIN
 FOR c IN SELECT conname,pg_get_constraintdef(oid) definition FROM pg_constraint
 WHERE conrelid='gl_journal'::regclass AND contype='c' AND pg_get_constraintdef(oid) LIKE '%checker_id%' LOOP
  expression:=regexp_replace(c.definition,'^CHECK \((.*)\)$','\1');
  EXECUTE format('ALTER TABLE gl_journal DROP CONSTRAINT %I',c.conname);
  EXECUTE format('ALTER TABLE gl_journal ADD CONSTRAINT %I CHECK (direct_post OR (%s))',c.conname,expression);
 END LOOP;
END $$;
ALTER TABLE gl_journal ADD CONSTRAINT gl_core_identity CHECK
 ((NOT direct_post AND policy_id IS NOT NULL) OR
  (direct_post AND policy_id IS NULL AND checker_id IS NULL AND source_type IN ('CORE_RECEIPT','CORE_PAYMENT','CORE_JOURNAL','CORE_REVERSAL')));
CREATE SEQUENCE accounting_voucher_number_seq;
CREATE TABLE accounting_voucher (
 id uuid PRIMARY KEY, sacco_id varchar(255) NOT NULL REFERENCES registered_saccos(sacco_id), station_id varchar(255) NOT NULL,
 number varchar(40) NOT NULL UNIQUE, type varchar(10) NOT NULL CHECK(type IN ('RECEIPT','PAYMENT','JOURNAL')),
 request_key uuid NOT NULL, payload_hash varchar(64) NOT NULL,
 effective_date date NOT NULL, party varchar(160) NOT NULL CHECK(length(trim(party))>0),
 reference varchar(160) NOT NULL, description varchar(500) NOT NULL, evidence varchar(500) NOT NULL,
 money_account_id uuid, total numeric(18,2) NOT NULL CHECK(total>0),
 transaction_count integer NOT NULL CHECK(transaction_count BETWEEN 1 AND 50),
 journal_id uuid NOT NULL UNIQUE, posted_by uuid NOT NULL REFERENCES members(id), posted_at timestamptz NOT NULL,
 reverses_id uuid UNIQUE, UNIQUE(sacco_id,station_id,request_key), UNIQUE(id,sacco_id,station_id),
 FOREIGN KEY(journal_id,sacco_id,station_id) REFERENCES gl_journal(id,sacco_id,station_id),
 FOREIGN KEY(money_account_id,sacco_id) REFERENCES gl_account(id,sacco_id),
 FOREIGN KEY(reverses_id,sacco_id,station_id) REFERENCES accounting_voucher(id,sacco_id,station_id),
 CHECK((type='JOURNAL')=(money_account_id IS NULL))
);
CREATE UNIQUE INDEX ux_core_money_reference ON accounting_voucher(sacco_id,station_id,money_account_id,reference)
 WHERE type<>'JOURNAL' AND reverses_id IS NULL;
CREATE INDEX ix_core_voucher_register ON accounting_voucher(sacco_id,station_id,type,effective_date DESC,id DESC);
CREATE TABLE accounting_voucher_transaction (
 id uuid PRIMARY KEY, voucher_id uuid NOT NULL, sacco_id varchar(255) NOT NULL, station_id varchar(255) NOT NULL,
 line_no integer NOT NULL CHECK(line_no BETWEEN 1 AND 50),
 transaction_id uuid, template_key uuid, component varchar(20) NOT NULL CHECK(component IN ('TOTAL','PRINCIPAL','INTEREST','FEES','TAX')),
 description varchar(500) NOT NULL, amount numeric NOT NULL CHECK(amount>0 AND scale(amount)<=2 AND amount<10000000000000000),
 debit_account_id uuid NOT NULL, credit_account_id uuid NOT NULL,
 debit_code varchar(40) NOT NULL, debit_name varchar(160) NOT NULL, credit_code varchar(40) NOT NULL, credit_name varchar(160) NOT NULL,
 transaction_name varchar(240) NOT NULL, UNIQUE(voucher_id,line_no), CHECK(debit_account_id<>credit_account_id),
 FOREIGN KEY(voucher_id,sacco_id,station_id) REFERENCES accounting_voucher(id,sacco_id,station_id),
 FOREIGN KEY(debit_account_id,sacco_id) REFERENCES gl_account(id,sacco_id),
 FOREIGN KEY(credit_account_id,sacco_id) REFERENCES gl_account(id,sacco_id),
 FOREIGN KEY(transaction_id,sacco_id) REFERENCES gl_transaction_code(id,sacco_id)
);
CREATE TRIGGER core_voucher_immutable BEFORE UPDATE OR DELETE ON accounting_voucher FOR EACH ROW EXECUTE FUNCTION gl_protect_evidence();
CREATE TRIGGER core_transaction_immutable BEFORE UPDATE OR DELETE ON accounting_voucher_transaction FOR EACH ROW EXECUTE FUNCTION gl_protect_evidence();
CREATE FUNCTION core_transaction_insert_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF EXISTS(SELECT 1 FROM accounting_voucher v JOIN gl_journal j ON j.id=v.journal_id WHERE v.id=NEW.voucher_id AND j.state='POSTED')
 THEN RAISE EXCEPTION 'Cannot append to posted voucher'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER core_transaction_insert BEFORE INSERT ON accounting_voucher_transaction FOR EACH ROW EXECUTE FUNCTION core_transaction_insert_guard();
CREATE FUNCTION core_post_commit_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE journal uuid;
BEGIN
 IF TG_TABLE_NAME='gl_journal' THEN journal:=NEW.id; ELSE journal:=NEW.journal_id; END IF;
 IF NOT EXISTS(SELECT 1 FROM gl_journal j JOIN accounting_voucher v ON v.journal_id=j.id
  WHERE j.id=journal AND j.direct_post AND j.state='POSTED')
 THEN RAISE EXCEPTION 'Core vouchers must commit as posted documents'; END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER core_voucher_commit AFTER INSERT ON accounting_voucher DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION core_post_commit_guard();
CREATE CONSTRAINT TRIGGER core_journal_commit AFTER INSERT ON gl_journal DEFERRABLE INITIALLY DEFERRED FOR EACH ROW WHEN(NEW.direct_post) EXECUTE FUNCTION core_post_commit_guard();
CREATE OR REPLACE FUNCTION gl_protect_journal() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' OR OLD.state='POSTED' THEN RAISE EXCEPTION 'Journal history is immutable'; END IF;
 IF NEW.direct_post IS DISTINCT FROM OLD.direct_post OR
 ROW(NEW.id,NEW.sacco_id,NEW.station_id,NEW.policy_id,NEW.policy_version,NEW.period_id,NEW.source_type,NEW.source_reference,
  NEW.request_key,NEW.payload_hash,NEW.currency,NEW.evidence_reference,NEW.reason,NEW.effective_date,NEW.maker_id,NEW.recorded_at,NEW.reverses_id)
 IS DISTINCT FROM ROW(OLD.id,OLD.sacco_id,OLD.station_id,OLD.policy_id,OLD.policy_version,OLD.period_id,OLD.source_type,OLD.source_reference,
  OLD.request_key,OLD.payload_hash,OLD.currency,OLD.evidence_reference,OLD.reason,OLD.effective_date,OLD.maker_id,OLD.recorded_at,OLD.reverses_id)
 THEN RAISE EXCEPTION 'Journal payload is immutable'; END IF;
 IF OLD.direct_post THEN
  IF OLD.state<>'DRAFT' OR NEW.state<>'POSTED' OR NEW.checker_id IS NOT NULL THEN RAISE EXCEPTION 'Invalid direct posting transition'; END IF;
 ELSE
  IF NOT ((OLD.state='DRAFT' AND NEW.state='APPROVED') OR (OLD.state='APPROVED' AND NEW.state='POSTED'))
  THEN RAISE EXCEPTION 'Invalid journal transition'; END IF;
  IF NEW.state='POSTED' AND (NEW.checker_id<>OLD.checker_id OR NEW.checked_at<>OLD.checked_at OR NEW.approval_evidence_reference<>OLD.approval_evidence_reference)
  THEN RAISE EXCEPTION 'Journal approval is immutable'; END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE OR REPLACE FUNCTION gl_validate_post() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE p accounting_period; cnt integer; difference numeric; original gl_journal; opening date; posting_event text; account_row gl_account;
BEGIN
 IF NEW.state<>'POSTED' THEN RETURN NEW; END IF;
 SELECT * INTO p FROM accounting_period WHERE id=NEW.period_id FOR SHARE;
 IF NEW.direct_post THEN
  IF p.id IS NULL OR p.state<>'OPEN' OR NEW.effective_date NOT BETWEEN p.starts_on AND p.ends_on THEN RAISE EXCEPTION 'Open period required'; END IF;
  IF NEW.effective_date>CURRENT_DATE THEN RAISE EXCEPTION 'Future posting date is invalid'; END IF;
  IF EXISTS(SELECT 1 FROM gl_journal_line l JOIN gl_account a ON a.id=l.account_id WHERE l.journal_id=NEW.id AND (NOT a.active OR a.kind<>'POSTING'))
  THEN RAISE EXCEPTION 'Core vouchers require ordinary active posting accounts'; END IF;
  SELECT count(*),sum(debit-credit) INTO cnt,difference FROM gl_journal_line WHERE journal_id=NEW.id;
  IF cnt NOT BETWEEN 2 AND 500 OR difference<>0 THEN RAISE EXCEPTION 'Balanced lines required'; END IF;
  IF NOT EXISTS(SELECT 1 FROM accounting_voucher v WHERE v.journal_id=NEW.id AND v.sacco_id=NEW.sacco_id AND v.station_id=NEW.station_id
    AND v.posted_by=NEW.maker_id AND v.number=NEW.source_reference AND v.effective_date=NEW.effective_date
    AND v.posted_at=NEW.posted_at AND v.request_key=NEW.request_key AND v.payload_hash=NEW.payload_hash
    AND ((v.reverses_id IS NULL AND NEW.reverses_id IS NULL AND NEW.source_type='CORE_'||v.type)
      OR (v.reverses_id IS NOT NULL AND NEW.reverses_id IS NOT NULL AND NEW.source_type='CORE_REVERSAL'))
    AND v.transaction_count=(SELECT count(*) FROM accounting_voucher_transaction t WHERE t.voucher_id=v.id)
    AND v.total=(SELECT sum(t.amount) FROM accounting_voucher_transaction t WHERE t.voucher_id=v.id))
  THEN RAISE EXCEPTION 'Voucher and journal must agree'; END IF;
  IF NEW.reverses_id IS NULL AND EXISTS(SELECT 1 FROM accounting_voucher v JOIN accounting_voucher_transaction t ON t.voucher_id=v.id
    JOIN gl_account m ON m.id=v.money_account_id WHERE v.journal_id=NEW.id AND v.type<>'JOURNAL'
    AND (m.purpose NOT IN ('CASH','BANK','MOBILE_MONEY') OR (v.type='RECEIPT' AND t.debit_account_id<>m.id) OR (v.type='PAYMENT' AND t.credit_account_id<>m.id)))
  THEN RAISE EXCEPTION 'Voucher money account direction must agree'; END IF;
  IF EXISTS(
    (SELECT account_id,debit,credit FROM gl_journal_line WHERE journal_id=NEW.id)
    EXCEPT ALL
    (SELECT t.debit_account_id,t.amount,0 FROM accounting_voucher_transaction t JOIN accounting_voucher v ON v.id=t.voucher_id WHERE v.journal_id=NEW.id
     UNION ALL SELECT t.credit_account_id,0,t.amount FROM accounting_voucher_transaction t JOIN accounting_voucher v ON v.id=t.voucher_id WHERE v.journal_id=NEW.id))
   OR cnt<>(SELECT count(*)*2 FROM accounting_voucher_transaction t JOIN accounting_voucher v ON v.id=t.voucher_id WHERE v.journal_id=NEW.id)
  THEN RAISE EXCEPTION 'Voucher transaction lines must match journal'; END IF;
  IF NEW.reverses_id IS NOT NULL THEN
   SELECT * INTO original FROM gl_journal WHERE id=NEW.reverses_id FOR UPDATE;
   IF NOT original.direct_post OR original.state<>'POSTED' OR original.reverses_id IS NOT NULL OR NEW.effective_date<original.effective_date
    OR NOT EXISTS(SELECT 1 FROM accounting_voucher r JOIN accounting_voucher o ON o.id=r.reverses_id WHERE r.journal_id=NEW.id AND o.journal_id=original.id)
   THEN RAISE EXCEPTION 'Invalid core reversal'; END IF;
   IF EXISTS((SELECT account_id,debit,credit FROM gl_journal_line WHERE journal_id=NEW.id)
    EXCEPT ALL(SELECT account_id,credit,debit FROM gl_journal_line WHERE journal_id=original.id))
   OR cnt<>(SELECT count(*) FROM gl_journal_line WHERE journal_id=original.id) THEN RAISE EXCEPTION 'Exact reversal required'; END IF;
  END IF;
  IF NOT EXISTS(SELECT 1 FROM accounting_outbox WHERE journal_id=NEW.id AND sacco_id=NEW.sacco_id AND station_id=NEW.station_id)
  THEN RAISE EXCEPTION 'Accounting outbox required'; END IF;
  RETURN NEW;
 END IF;
 IF p.state<>'OPEN' OR NEW.effective_date NOT BETWEEN p.starts_on AND p.ends_on THEN RAISE EXCEPTION 'Accounting period is closed or invalid'; END IF;
 IF NOT EXISTS(SELECT 1 FROM accounting_policies ap JOIN accounting_policy_approvals aa ON aa.policy_id=ap.id
 WHERE ap.id=NEW.policy_id AND ap.sacco_id=NEW.sacco_id AND aa.sacco_id=NEW.sacco_id AND aa.decision='APPROVED'
 AND ap.authoritative_ledger='LOCAL_GL' AND ap.effective_from<=NEW.effective_date AND ap.policy_version=NEW.policy_version) THEN
 RAISE EXCEPTION 'Approved local accounting policy is required'; END IF;
 SELECT ap.opening_date INTO opening FROM accounting_policies ap WHERE ap.id=NEW.policy_id;
 posting_event := CASE NEW.source_type WHEN 'OPENING' THEN 'OPENING_BALANCE' WHEN 'MANUAL' THEN 'MANUAL_JOURNAL' WHEN 'SOURCE_REVERSAL' THEN 'REVERSAL' ELSE NEW.source_type END;
 IF NOT EXISTS(SELECT 1 FROM accounting_policies ap WHERE ap.id=NEW.policy_id AND ap.posting_matrix_json::jsonb->posting_event->>'permission'='ALLOWED') THEN
 RAISE EXCEPTION 'Posting event is disabled by policy'; END IF;
 IF NEW.source_type='OPENING' THEN
 IF NEW.effective_date<>opening OR NOT EXISTS(SELECT 1 FROM gl_cutover_coverage c WHERE c.opening_journal_id=NEW.id AND c.sacco_id=NEW.sacco_id AND c.station_id=NEW.station_id AND c.complete AND c.maker_id=NEW.maker_id AND c.checker_id=NEW.checker_id) THEN
 RAISE EXCEPTION 'Independently reconciled opening evidence is required'; END IF;
 ELSE
 IF NEW.effective_date<=opening OR NOT EXISTS(SELECT 1 FROM gl_cutover_coverage c JOIN gl_journal j ON j.id=c.opening_journal_id WHERE c.sacco_id=NEW.sacco_id AND c.station_id=NEW.station_id AND c.complete AND j.state='POSTED') THEN
 RAISE EXCEPTION 'Reviewed opening boundary is required'; END IF;
 END IF;
 FOR account_row IN SELECT a.* FROM gl_account a JOIN gl_journal_line l ON l.account_id=a.id WHERE l.journal_id=NEW.id FOR SHARE OF a LOOP
 IF NOT account_row.active OR account_row.kind='HEADING' OR (NEW.source_type='MANUAL' AND account_row.kind='CONTROL') THEN
 RAISE EXCEPTION 'Active valid posting accounts are required'; END IF;
 END LOOP;
 SELECT count(*),sum(debit-credit) INTO cnt,difference FROM gl_journal_line WHERE journal_id=NEW.id;
 IF cnt<2 OR difference<>0 THEN RAISE EXCEPTION 'Journal must contain balanced lines'; END IF;
 IF NEW.reverses_id IS NOT NULL THEN
 SELECT * INTO original FROM gl_journal WHERE id=NEW.reverses_id FOR UPDATE;
 IF original.state<>'POSTED' OR NEW.maker_id IN(original.maker_id,original.checker_id) OR NEW.checker_id IN(original.maker_id,original.checker_id)
 THEN RAISE EXCEPTION 'Independent reversal is required'; END IF;
 IF EXISTS((SELECT account_id,sum(debit) debit,sum(credit) credit FROM gl_journal_line WHERE journal_id=NEW.id GROUP BY account_id)
 EXCEPT (SELECT account_id,sum(credit),sum(debit) FROM gl_journal_line WHERE journal_id=NEW.reverses_id GROUP BY account_id))
 OR EXISTS((SELECT account_id,sum(credit) debit,sum(debit) credit FROM gl_journal_line WHERE journal_id=NEW.reverses_id GROUP BY account_id)
 EXCEPT(SELECT account_id,sum(debit),sum(credit) FROM gl_journal_line WHERE journal_id=NEW.id GROUP BY account_id))
 THEN RAISE EXCEPTION 'Reversal must exactly reverse original lines'; END IF;
 END IF;
 IF NOT EXISTS(SELECT 1 FROM accounting_outbox WHERE journal_id=NEW.id AND sacco_id=NEW.sacco_id AND station_id=NEW.station_id)
 THEN RAISE EXCEPTION 'Transactional accounting outbox is required'; END IF;
 RETURN NEW;
END; $$;
