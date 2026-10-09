-- Dedicated accountant loan sources. Existing lending and ordinary voucher guards remain intact.
INSERT INTO member_access_claims(member_id,claim_name)
SELECT m.id,c.claim_name FROM members m
CROSS JOIN (VALUES ('LOAN_RECORDING_VIEW'),('LOAN_RECORDING_CREATE'),('LOAN_RECORDING_DISBURSE'),('LOAN_RECORDING_POST'),('LOAN_RECORDING_REVERSE'),('LOAN_RECORDING_EXPORT')) c(claim_name)
WHERE m.status='ACTIVE' AND m.sacco_id IS NOT NULL AND m.station_id IS NOT NULL
 AND (m.staff_access_status='ACTIVE' OR (m.staff_access_status='NONE' AND coalesce(m.staff_no,'')='' AND m.staff_access_assigned_at IS NULL AND m.staff_access_activated_at IS NULL))
 AND (m.position='ACCOUNTANT' OR EXISTS(SELECT 1 FROM member_staff_roles r WHERE r.member_id=m.id AND r.role_name='ACCOUNTANT'))
 AND m.position IS DISTINCT FROM 'ADMIN' AND NOT EXISTS(SELECT 1 FROM member_staff_roles r WHERE r.member_id=m.id AND r.role_name='ADMIN')
 AND EXISTS(SELECT 1 FROM member_access_claims old WHERE old.member_id=m.id)
ON CONFLICT DO NOTHING;

ALTER TABLE gl_journal DROP CONSTRAINT gl_core_identity;
ALTER TABLE gl_journal ADD CONSTRAINT gl_core_identity CHECK
 ((NOT direct_post AND policy_id IS NOT NULL) OR
 (direct_post AND policy_id IS NULL AND checker_id IS NULL AND source_type IN
 ('CORE_RECEIPT','CORE_PAYMENT','CORE_JOURNAL','CORE_REVERSAL','LOAN_RECORD_DISBURSEMENT','LOAN_RECORD_PAYMENT','LOAN_RECORD_REVERSAL')));
DO $$ DECLARE definition text; BEGIN
 SELECT pg_get_constraintdef(oid) INTO definition FROM pg_constraint WHERE conrelid='loan_applications'::regclass AND conname='loan_applications_status_check';
 ALTER TABLE loan_applications DROP CONSTRAINT loan_applications_status_check;
 EXECUTE 'ALTER TABLE loan_applications ADD CONSTRAINT loan_applications_status_check CHECK (status=''RECORDED'' OR ('||regexp_replace(definition,'^CHECK \((.*)\)$','\1')||'))';
END $$;
CREATE TABLE accountant_loan_record (
 loan_id uuid PRIMARY KEY REFERENCES loan_applications(id),sacco_id varchar(255) NOT NULL REFERENCES registered_saccos(sacco_id),station_id varchar(255) NOT NULL,
 request_key uuid NOT NULL,payload_hash varchar(64) NOT NULL,actor_id uuid NOT NULL REFERENCES members(id),recorded_at timestamptz NOT NULL,
 application_date date NOT NULL,requested_principal numeric NOT NULL CHECK(requested_principal>0 AND scale(requested_principal)<=2 AND requested_principal<10000000000000000),
 first_payment_date date NOT NULL CHECK(first_payment_date>application_date),product_name varchar(160) NOT NULL,metadata jsonb NOT NULL,
 UNIQUE(sacco_id,station_id,request_key),UNIQUE(loan_id,sacco_id,station_id),
 FOREIGN KEY(sacco_id,station_id) REFERENCES sacco_stations(sacco_id,station_id)
);
CREATE INDEX ix_accountant_loan_register ON accountant_loan_record(sacco_id,station_id,application_date DESC,loan_id DESC);
CREATE TRIGGER accountant_loan_record_immutable BEFORE UPDATE OR DELETE ON accountant_loan_record FOR EACH ROW EXECUTE FUNCTION gl_protect_evidence();
CREATE TABLE accountant_loan_post (
 id uuid PRIMARY KEY,loan_id uuid NOT NULL,sacco_id varchar(255) NOT NULL,station_id varchar(255) NOT NULL,
 kind varchar(16) NOT NULL CHECK(kind IN ('DISBURSEMENT','PAYMENT','REVERSAL')),state varchar(16) NOT NULL CHECK(state IN ('POSTING','POSTED')),
 request_key uuid NOT NULL,payload_hash varchar(64) NOT NULL,effective_date date NOT NULL,reference varchar(100) NOT NULL CHECK(length(trim(reference))>0),
 amount numeric NOT NULL CHECK(amount>0 AND scale(amount)<=2 AND amount<10000000000000000),principal_amount numeric(18,2),interest_amount numeric(18,2),principal_balance numeric(18,2),
 money_account_id uuid NOT NULL,principal_account_id uuid NOT NULL,interest_account_id uuid,
 money_account_name varchar(240) NOT NULL,principal_account_name varchar(240) NOT NULL,interest_account_name varchar(240) NOT NULL,
 evidence varchar(500) NOT NULL,notes varchar(500) NOT NULL,actor_id uuid NOT NULL REFERENCES members(id),recorded_at timestamptz NOT NULL,
 journal_id uuid NOT NULL UNIQUE,transaction_id uuid UNIQUE REFERENCES loan_repayment_transactions(id),reverses_id uuid UNIQUE,
 UNIQUE(sacco_id,station_id,request_key),UNIQUE(id,sacco_id,station_id),
 FOREIGN KEY(loan_id,sacco_id,station_id) REFERENCES accountant_loan_record(loan_id,sacco_id,station_id),
 FOREIGN KEY(journal_id,sacco_id,station_id) REFERENCES gl_journal(id,sacco_id,station_id),
 FOREIGN KEY(money_account_id,sacco_id) REFERENCES gl_account(id,sacco_id),FOREIGN KEY(principal_account_id,sacco_id) REFERENCES gl_account(id,sacco_id),
 FOREIGN KEY(interest_account_id,sacco_id) REFERENCES gl_account(id,sacco_id),FOREIGN KEY(reverses_id,sacco_id,station_id) REFERENCES accountant_loan_post(id,sacco_id,station_id),
 CHECK((kind='REVERSAL')=(reverses_id IS NOT NULL)),
 CHECK(state='POSTING' OR (principal_amount>=0 AND interest_amount>=0 AND amount=principal_amount+interest_amount AND principal_balance>=0
  AND ((kind='DISBURSEMENT' AND transaction_id IS NULL AND interest_amount=0) OR (kind<>'DISBURSEMENT' AND transaction_id IS NOT NULL))))
);
CREATE UNIQUE INDEX ux_accountant_one_disbursement ON accountant_loan_post(loan_id) WHERE kind='DISBURSEMENT';
CREATE UNIQUE INDEX ux_accountant_money_reference ON accountant_loan_post(sacco_id,station_id,money_account_id,reference) WHERE kind<>'REVERSAL';
CREATE INDEX ix_accountant_loan_post_register ON accountant_loan_post(sacco_id,station_id,kind,effective_date DESC,id DESC);
CREATE INDEX ix_accountant_loan_post_history ON accountant_loan_post(loan_id,recorded_at,id);
CREATE FUNCTION accountant_loan_post_protect() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF TG_OP='DELETE' OR OLD.state='POSTED' OR NEW.state<>'POSTED' OR
 (to_jsonb(NEW)-ARRAY['state','transaction_id','principal_amount','interest_amount','principal_balance']) IS DISTINCT FROM
 (to_jsonb(OLD)-ARRAY['state','transaction_id','principal_amount','interest_amount','principal_balance'])
 THEN RAISE EXCEPTION 'Loan posting history is immutable'; END IF; RETURN NEW;
END $$;
CREATE TRIGGER accountant_loan_post_immutable BEFORE UPDATE OR DELETE ON accountant_loan_post FOR EACH ROW EXECUTE FUNCTION accountant_loan_post_protect();
CREATE FUNCTION accountant_loan_record_scope() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF NOT EXISTS(SELECT 1 FROM loan_applications a JOIN members m ON m.id=a.applicant_member_id JOIN members u ON u.id=NEW.actor_id
 WHERE a.id=NEW.loan_id AND a.sacco_id=NEW.sacco_id AND a.station_id=NEW.station_id AND a.status='RECORDED'
 AND m.sacco_id=NEW.sacco_id AND m.station_id=NEW.station_id AND m.is_member AND u.sacco_id=NEW.sacco_id AND u.station_id=NEW.station_id)
 THEN RAISE EXCEPTION 'Loan recording scope must agree'; END IF; RETURN NEW;
END $$;
CREATE TRIGGER accountant_loan_record_scope BEFORE INSERT ON accountant_loan_record FOR EACH ROW EXECUTE FUNCTION accountant_loan_record_scope();
CREATE FUNCTION accountant_loan_terms_protect() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF EXISTS(SELECT 1 FROM accountant_loan_record WHERE loan_id=OLD.id) THEN
  IF TG_OP='DELETE' OR ROW(NEW.sacco_id,NEW.station_id,NEW.applicant_member_id,NEW.loan_type,NEW.loan_product_setting_id,NEW.amount,NEW.tenor_months,NEW.repayment_frequency,NEW.first_repayment_date,NEW.form_data,NEW.policy_snapshot)
  IS DISTINCT FROM ROW(OLD.sacco_id,OLD.station_id,OLD.applicant_member_id,OLD.loan_type,OLD.loan_product_setting_id,OLD.amount,OLD.tenor_months,OLD.repayment_frequency,OLD.first_repayment_date,OLD.form_data,OLD.policy_snapshot)
  OR (NEW.financial_snapshot->>'interestRate') IS DISTINCT FROM (OLD.financial_snapshot->>'interestRate')
  OR (NEW.financial_snapshot->>'interestMethod') IS DISTINCT FROM (OLD.financial_snapshot->>'interestMethod')
  THEN RAISE EXCEPTION 'Recorded loan contract is immutable'; END IF;
  IF NEW.status IS DISTINCT FROM OLD.status AND NOT ((OLD.status='RECORDED' AND NEW.status='DISBURSED') OR (OLD.status IN ('DISBURSED','PAR','DEFAULTED','PAID') AND NEW.status IN ('DISBURSED','PAR','DEFAULTED','PAID')))
  THEN RAISE EXCEPTION 'Invalid recorded loan transition'; END IF;
 END IF; RETURN NEW;
END $$;
CREATE TRIGGER accountant_loan_terms BEFORE UPDATE OR DELETE ON loan_applications FOR EACH ROW EXECUTE FUNCTION accountant_loan_terms_protect();
CREATE FUNCTION accountant_loan_commit() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE p accountant_loan_post; j gl_journal; t loan_repayment_transactions; a loan_applications; l loan_ledgers;
BEGIN
 SELECT * INTO p FROM accountant_loan_post WHERE id=NEW.id; SELECT * INTO j FROM gl_journal WHERE id=p.journal_id;
 SELECT * INTO a FROM loan_applications WHERE id=p.loan_id; SELECT * INTO l FROM loan_ledgers WHERE loan_application_id=p.loan_id;
 IF p.state<>'POSTED' OR j.state<>'POSTED' OR l.loan_application_id IS NULL OR l.sacco_id<>p.sacco_id OR l.station_id<>p.station_id
 OR a.amount<>l.principal OR a.applicant_member_id<>l.applicant_member_id OR a.sacco_id<>p.sacco_id OR a.station_id<>p.station_id
 THEN RAISE EXCEPTION 'Loan posting must commit with matching ledger and GL'; END IF;
 IF p.kind='DISBURSEMENT' THEN
  IF a.disbursement_date<>p.effective_date OR a.amount<>p.amount OR l.disbursement_date<>p.effective_date
  THEN RAISE EXCEPTION 'Disbursement and contract must agree'; END IF;
 ELSE
  SELECT * INTO t FROM loan_repayment_transactions WHERE id=p.transaction_id;
  IF t.id IS NULL OR t.loan_application_id<>p.loan_id OR t.sacco_id<>p.sacco_id OR t.station_id<>p.station_id OR t.actor_member_id<>p.actor_id
   OR t.amount<>p.amount OR t.principal_amount<>p.principal_amount OR t.interest_amount<>p.interest_amount OR t.payment_date<>p.effective_date
   OR t.request_key<>p.request_key OR (p.kind='PAYMENT' AND (t.kind<>'PAYMENT' OR t.channel_reference<>p.reference))
   OR (p.kind='REVERSAL' AND (t.kind<>'REVERSAL' OR NOT EXISTS(SELECT 1 FROM accountant_loan_post o WHERE o.id=p.reverses_id AND o.transaction_id=t.reverses_transaction_id)))
  THEN RAISE EXCEPTION 'Repayment source and posting must agree'; END IF;
 END IF;
 IF l.principal_paid<>(SELECT coalesce(sum(CASE WHEN kind='PAYMENT' THEN principal_amount ELSE -principal_amount END),0) FROM loan_repayment_transactions WHERE loan_application_id=p.loan_id)
 OR l.interest_paid<>(SELECT coalesce(sum(CASE WHEN kind='PAYMENT' THEN interest_amount ELSE -interest_amount END),0) FROM loan_repayment_transactions WHERE loan_application_id=p.loan_id)
 THEN RAISE EXCEPTION 'Loan balances must equal append-only payments'; END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER accountant_loan_post_commit AFTER INSERT ON accountant_loan_post DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION accountant_loan_commit();
CREATE OR REPLACE FUNCTION gl_validate_post() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE p accounting_period; cnt integer; difference numeric; original gl_journal; opening date; posting_event text; account_row gl_account;
BEGIN
 IF NEW.state<>'POSTED' THEN RETURN NEW; END IF;
 SELECT * INTO p FROM accounting_period WHERE id=NEW.period_id FOR SHARE;
 IF NEW.source_type IN ('LOAN_RECORD_DISBURSEMENT','LOAN_RECORD_PAYMENT','LOAN_RECORD_REVERSAL') THEN
  IF NOT NEW.direct_post OR p.id IS NULL OR p.sacco_id<>NEW.sacco_id OR p.state<>'OPEN' OR NEW.effective_date NOT BETWEEN p.starts_on AND p.ends_on OR NEW.effective_date>CURRENT_DATE
  THEN RAISE EXCEPTION 'Open valid loan posting period required'; END IF;
  IF NOT EXISTS(SELECT 1 FROM accountant_loan_post x WHERE x.journal_id=NEW.id AND x.state='POSTED' AND x.sacco_id=NEW.sacco_id AND x.station_id=NEW.station_id
    AND x.actor_id=NEW.maker_id AND x.id::text=NEW.source_reference AND x.request_key=NEW.request_key AND x.payload_hash=NEW.payload_hash AND x.effective_date=NEW.effective_date
    AND NEW.source_type='LOAN_RECORD_'||x.kind AND x.recorded_at=NEW.posted_at)
  THEN RAISE EXCEPTION 'Direct loan source required'; END IF;
  IF EXISTS(SELECT 1 FROM gl_journal_line z JOIN gl_account a ON a.id=z.account_id WHERE z.journal_id=NEW.id AND NOT a.active)
  THEN RAISE EXCEPTION 'Active accounts required'; END IF;
  SELECT count(*),sum(debit-credit) INTO cnt,difference FROM gl_journal_line WHERE journal_id=NEW.id;
  IF cnt NOT BETWEEN 2 AND 3 OR difference<>0 THEN RAISE EXCEPTION 'Balanced loan lines required'; END IF;
  IF NOT EXISTS(SELECT 1 FROM accountant_loan_post x JOIN gl_account m ON m.id=x.money_account_id JOIN gl_account a ON a.id=x.principal_account_id
   WHERE x.journal_id=NEW.id AND m.kind='POSTING' AND m.type='ASSET' AND m.purpose IN ('CASH','BANK','MOBILE_MONEY')
    AND a.kind='CONTROL' AND a.type='ASSET' AND a.purpose='LOAN_PRINCIPAL'
    AND (x.interest_amount=0 OR EXISTS(SELECT 1 FROM gl_account i WHERE i.id=x.interest_account_id AND i.kind='POSTING' AND i.purpose='CLEARING')))
  THEN RAISE EXCEPTION 'Loan account classifications required'; END IF;
  IF EXISTS((SELECT account_id,debit,credit FROM gl_journal_line WHERE journal_id=NEW.id)
   EXCEPT ALL (SELECT x.money_account_id,CASE WHEN x.kind='PAYMENT' THEN x.amount ELSE 0 END,CASE WHEN x.kind='PAYMENT' THEN 0 ELSE x.amount END FROM accountant_loan_post x WHERE x.journal_id=NEW.id
    UNION ALL SELECT x.principal_account_id,CASE WHEN x.kind='PAYMENT' THEN 0 ELSE x.principal_amount END,CASE WHEN x.kind='PAYMENT' THEN x.principal_amount ELSE 0 END FROM accountant_loan_post x WHERE x.journal_id=NEW.id AND x.principal_amount>0
    UNION ALL SELECT x.interest_account_id,CASE WHEN x.kind='PAYMENT' THEN 0 ELSE x.interest_amount END,CASE WHEN x.kind='PAYMENT' THEN x.interest_amount ELSE 0 END FROM accountant_loan_post x WHERE x.journal_id=NEW.id AND x.interest_amount>0))
   OR cnt<>(SELECT 1+(CASE WHEN principal_amount>0 THEN 1 ELSE 0 END)+(CASE WHEN interest_amount>0 THEN 1 ELSE 0 END) FROM accountant_loan_post WHERE journal_id=NEW.id)
  THEN RAISE EXCEPTION 'Loan allocation lines must match source'; END IF;
  IF NEW.source_type='LOAN_RECORD_REVERSAL' THEN
   SELECT * INTO original FROM gl_journal WHERE id=NEW.reverses_id FOR UPDATE;
   IF original.state<>'POSTED' OR original.source_type<>'LOAN_RECORD_PAYMENT' OR NEW.effective_date<original.effective_date
    OR NOT EXISTS(SELECT 1 FROM accountant_loan_post x JOIN accountant_loan_post o ON o.id=x.reverses_id WHERE x.journal_id=NEW.id AND o.journal_id=original.id)
    OR EXISTS((SELECT account_id,debit,credit FROM gl_journal_line WHERE journal_id=NEW.id) EXCEPT ALL (SELECT account_id,credit,debit FROM gl_journal_line WHERE journal_id=original.id))
    OR cnt<>(SELECT count(*) FROM gl_journal_line WHERE journal_id=original.id)
   THEN RAISE EXCEPTION 'Exact linked repayment reversal required'; END IF;
  ELSIF NEW.reverses_id IS NOT NULL THEN RAISE EXCEPTION 'Unexpected reversal'; END IF;
  IF NOT EXISTS(SELECT 1 FROM accounting_outbox WHERE journal_id=NEW.id AND sacco_id=NEW.sacco_id AND station_id=NEW.station_id)
  THEN RAISE EXCEPTION 'Loan outbox required'; END IF;
  RETURN NEW;
 END IF;
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

CREATE OR REPLACE FUNCTION core_post_commit_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE journal uuid;
BEGIN
 IF TG_TABLE_NAME='gl_journal' THEN journal:=NEW.id; ELSE journal:=NEW.journal_id; END IF;
 IF EXISTS(SELECT 1 FROM gl_journal j JOIN accountant_loan_post x ON x.journal_id=j.id WHERE j.id=journal AND j.direct_post AND j.state='POSTED' AND x.state='POSTED' AND j.source_type LIKE 'LOAN_RECORD_%') THEN RETURN NULL; END IF;
 IF NOT EXISTS(SELECT 1 FROM gl_journal j JOIN accounting_voucher v ON v.journal_id=j.id
  WHERE j.id=journal AND j.direct_post AND j.state='POSTED')
 THEN RAISE EXCEPTION 'Core vouchers must commit as posted documents'; END IF;
 RETURN NULL;
END $$;
