-- Source-owned lending adjustments. Contracts, receipts and original journals remain immutable.
CREATE TABLE loan_adjustment_request (
 id uuid PRIMARY KEY,loan_id uuid NOT NULL,sacco_id varchar(255) NOT NULL,station_id varchar(255) NOT NULL,
 request_key uuid NOT NULL,payload_hash varchar(64) NOT NULL,kind varchar(32) NOT NULL
 CHECK(kind IN ('PROVISION','WRITE_OFF','WRITE_BACK','DISBURSEMENT_REVERSAL')),
 effective_date date NOT NULL,amount numeric NOT NULL CHECK(amount>0 AND scale(amount)<=2 AND amount<10000000000000000),
 allowance_change numeric(18,2) NOT NULL,principal_change numeric(18,2) NOT NULL,
 debit_account_id uuid NOT NULL,credit_account_id uuid NOT NULL CHECK(debit_account_id<>credit_account_id),
 outstanding_before numeric(18,2) NOT NULL CHECK(outstanding_before>=0),allowance_before numeric(18,2) NOT NULL CHECK(allowance_before>=0),
 written_off_before numeric(18,2) NOT NULL CHECK(written_off_before>=0),repayment_sequence bigint NOT NULL,last_adjustment uuid REFERENCES loan_adjustment_request(id),
 reason varchar(500) NOT NULL CHECK(length(trim(reason))>0),evidence varchar(500) NOT NULL CHECK(length(trim(evidence))>0),
 actor_id uuid NOT NULL REFERENCES members(id),recorded_at timestamptz NOT NULL,reverses_journal_id uuid REFERENCES gl_journal(id),
 UNIQUE(id,sacco_id,station_id),UNIQUE(sacco_id,station_id,request_key),
 FOREIGN KEY(loan_id,sacco_id,station_id) REFERENCES accountant_loan_record(loan_id,sacco_id,station_id),
 FOREIGN KEY(debit_account_id,sacco_id) REFERENCES gl_account(id,sacco_id),FOREIGN KEY(credit_account_id,sacco_id) REFERENCES gl_account(id,sacco_id),
 CHECK((kind='PROVISION' AND principal_change=0 AND abs(allowance_change)=amount AND reverses_journal_id IS NULL)
 OR (kind='WRITE_OFF' AND principal_change=-amount AND allowance_change=-amount AND reverses_journal_id IS NULL)
 OR (kind='WRITE_BACK' AND principal_change=amount AND allowance_change=amount AND reverses_journal_id IS NOT NULL)
 OR (kind='DISBURSEMENT_REVERSAL' AND principal_change=-amount AND allowance_change=0 AND reverses_journal_id IS NOT NULL))
);
CREATE INDEX ix_loan_adjustment_branch ON loan_adjustment_request(sacco_id,station_id,effective_date DESC,id);
CREATE INDEX ix_loan_adjustment_history ON loan_adjustment_request(loan_id,recorded_at,id);
CREATE TABLE loan_adjustment_decision (
 request_id uuid PRIMARY KEY,sacco_id varchar(255) NOT NULL,station_id varchar(255) NOT NULL,
 event_sequence bigint GENERATED ALWAYS AS IDENTITY UNIQUE,decision varchar(16) NOT NULL CHECK(decision IN ('APPROVED','REJECTED')),
 actor_id uuid NOT NULL REFERENCES members(id),request_key uuid NOT NULL,effective_date date NOT NULL,
 reason varchar(500) NOT NULL CHECK(length(trim(reason))>0),journal_id uuid UNIQUE REFERENCES gl_journal(id) DEFERRABLE INITIALLY DEFERRED,
 recorded_at timestamptz NOT NULL,UNIQUE(sacco_id,station_id,request_key),
 FOREIGN KEY(request_id,sacco_id,station_id) REFERENCES loan_adjustment_request(id,sacco_id,station_id),
 CHECK((decision='APPROVED')=(journal_id IS NOT NULL))
);
CREATE TRIGGER loan_adjustment_request_immutable BEFORE UPDATE OR DELETE ON loan_adjustment_request FOR EACH ROW EXECUTE FUNCTION gl_protect_evidence();
CREATE TRIGGER loan_adjustment_decision_immutable BEFORE UPDATE OR DELETE ON loan_adjustment_decision FOR EACH ROW EXECUTE FUNCTION gl_protect_evidence();

CREATE VIEW loan_adjustment_balance AS
 SELECT l.loan_application_id loan_id,coalesce(b.allowance,0)::numeric(18,2) allowance,
 coalesce(b.written_off,0)::numeric(18,2) written_off,b.last_date,
 latest.id last_adjustment,provision.allowance_account,write_off.journal_id write_off_journal
 FROM loan_ledgers l
 LEFT JOIN LATERAL(SELECT sum(q.allowance_change) allowance,
  -sum(q.principal_change) FILTER(WHERE q.kind IN ('WRITE_OFF','WRITE_BACK')) written_off,max(q.effective_date) last_date
  FROM loan_adjustment_request q JOIN loan_adjustment_decision d ON d.request_id=q.id
  JOIN gl_journal j ON j.id=d.journal_id AND j.state='POSTED' WHERE q.loan_id=l.loan_application_id) b ON true
 LEFT JOIN LATERAL(SELECT q.id FROM loan_adjustment_request q JOIN loan_adjustment_decision d ON d.request_id=q.id
  JOIN gl_journal j ON j.id=d.journal_id AND j.state='POSTED' WHERE q.loan_id=l.loan_application_id ORDER BY d.event_sequence DESC LIMIT 1) latest ON true
 LEFT JOIN LATERAL(SELECT CASE WHEN q.allowance_change>0 THEN q.credit_account_id ELSE q.debit_account_id END allowance_account
  FROM loan_adjustment_request q JOIN loan_adjustment_decision d ON d.request_id=q.id JOIN gl_journal j ON j.id=d.journal_id AND j.state='POSTED'
  WHERE q.loan_id=l.loan_application_id AND q.kind='PROVISION' ORDER BY d.event_sequence DESC LIMIT 1) provision ON true
 LEFT JOIN LATERAL(SELECT d.journal_id FROM loan_adjustment_request q JOIN loan_adjustment_decision d ON d.request_id=q.id
  JOIN gl_journal j ON j.id=d.journal_id AND j.state='POSTED' WHERE q.loan_id=l.loan_application_id AND q.kind='WRITE_OFF'
  ORDER BY d.event_sequence DESC LIMIT 1) write_off ON true;

CREATE FUNCTION loan_adjustment_validate_journal(j gl_journal) RETURNS void LANGUAGE plpgsql AS $$
DECLARE q loan_adjustment_request; d loan_adjustment_decision; l loan_ledgers; a loan_applications;
 b record; p accountant_loan_post; dr gl_account; cr gl_account; period accounting_period; original gl_journal;
BEGIN
 SELECT * INTO q FROM loan_adjustment_request WHERE id::text=j.source_reference;
 SELECT * INTO d FROM loan_adjustment_decision WHERE request_id=q.id;
 SELECT * INTO a FROM loan_applications WHERE id=q.loan_id;
 SELECT * INTO l FROM loan_ledgers WHERE loan_application_id=q.loan_id FOR UPDATE;
 SELECT * INTO b FROM loan_adjustment_balance WHERE loan_id=q.loan_id;
 SELECT * INTO p FROM accountant_loan_post WHERE loan_id=q.loan_id AND kind='DISBURSEMENT' AND state='POSTED';
 SELECT * INTO period FROM accounting_period WHERE id=j.period_id FOR SHARE;
 IF q.id IS NULL OR d.decision IS DISTINCT FROM 'APPROVED' OR d.journal_id IS DISTINCT FROM j.id OR NOT j.direct_post
 OR j.policy_id IS NOT NULL OR j.checker_id IS NOT NULL OR j.currency<>'TZS' OR j.sacco_id<>q.sacco_id OR j.station_id<>q.station_id
 OR j.maker_id<>d.actor_id OR d.actor_id=q.actor_id OR j.effective_date<>q.effective_date OR d.effective_date<>q.effective_date
 OR j.request_key<>d.request_key OR j.payload_hash<>q.payload_hash OR j.reason<>q.reason OR j.evidence_reference<>q.evidence
 OR j.recorded_at<>d.recorded_at OR j.posted_at<>d.recorded_at OR j.reverses_id IS DISTINCT FROM q.reverses_journal_id
 OR l.loan_application_id IS NULL OR p.id IS NULL OR a.sacco_id<>q.sacco_id OR a.station_id<>q.station_id
 OR l.sacco_id<>q.sacco_id OR l.station_id<>q.station_id OR l.principal<>a.amount OR a.top_up_source_loan_id IS NOT NULL
 OR period.id IS NULL OR period.sacco_id<>q.sacco_id OR period.state<>'OPEN' OR q.effective_date NOT BETWEEN period.starts_on AND period.ends_on
 OR q.effective_date>CURRENT_DATE OR q.effective_date<l.disbursement_date OR q.effective_date<l.last_payment_date OR q.effective_date<b.last_date
 OR q.outstanding_before<>l.principal-l.principal_paid OR q.repayment_sequence<>l.next_sequence
 OR q.allowance_before<>b.allowance OR q.written_off_before<>b.written_off OR q.last_adjustment IS DISTINCT FROM b.last_adjustment
 THEN RAISE EXCEPTION 'Adjustment requires current scoped source, independent decision and open period'; END IF;
 IF NOT EXISTS(SELECT 1 FROM members m WHERE m.id=q.actor_id AND m.sacco_id=q.sacco_id AND m.station_id=q.station_id)
 OR NOT EXISTS(SELECT 1 FROM members m WHERE m.id=d.actor_id AND m.sacco_id=q.sacco_id AND m.station_id=q.station_id)
 THEN RAISE EXCEPTION 'Adjustment staff scope mismatch'; END IF;
 SELECT * INTO dr FROM gl_account WHERE id=q.debit_account_id FOR SHARE;
 SELECT * INTO cr FROM gl_account WHERE id=q.credit_account_id FOR SHARE;
 IF NOT dr.active OR NOT cr.active OR dr.sacco_id<>q.sacco_id OR cr.sacco_id<>q.sacco_id
 OR (SELECT count(*) FROM gl_journal_line WHERE journal_id=j.id)<>2
 OR NOT EXISTS(SELECT 1 FROM gl_journal_line WHERE journal_id=j.id AND account_id=dr.id AND debit=q.amount AND credit=0)
 OR NOT EXISTS(SELECT 1 FROM gl_journal_line WHERE journal_id=j.id AND account_id=cr.id AND credit=q.amount AND debit=0)
 OR NOT EXISTS(SELECT 1 FROM accounting_outbox WHERE journal_id=j.id AND sacco_id=q.sacco_id AND station_id=q.station_id)
 THEN RAISE EXCEPTION 'Exact active balanced adjustment accounts and outbox required'; END IF;
 IF q.kind='PROVISION' THEN
  IF a.status NOT IN ('DISBURSED','PAR','DEFAULTED','PAID') OR b.written_off<>0 OR b.allowance+q.allowance_change<0
    OR b.allowance+q.allowance_change>q.outstanding_before
    OR NOT ((q.allowance_change>0 AND dr.type='EXPENSE' AND dr.kind='POSTING' AND dr.purpose='EXPENSE'
      AND cr.type='ASSET' AND cr.kind='POSTING' AND cr.purpose='ALLOWANCE'
      AND (b.allowance=0 OR cr.id=b.allowance_account))
     OR (q.allowance_change<0 AND cr.type='EXPENSE' AND cr.kind='POSTING' AND cr.purpose='EXPENSE'
      AND dr.type='ASSET' AND dr.kind='POSTING' AND dr.purpose='ALLOWANCE' AND dr.id=b.allowance_account))
  THEN RAISE EXCEPTION 'Invalid provision target or mapping'; END IF;
 ELSIF q.kind='WRITE_OFF' THEN
  IF a.status NOT IN ('DISBURSED','PAR','DEFAULTED') OR q.amount<>q.outstanding_before OR q.amount<>b.allowance OR b.written_off<>0
   OR dr.id<>b.allowance_account OR cr.id<>p.principal_account_id
  THEN RAISE EXCEPTION 'Full principal write-off requires its exact allowance'; END IF;
 ELSIF q.kind='WRITE_BACK' THEN
  IF a.status<>'WRITTEN_OFF' OR q.amount<>b.written_off OR q.amount<>q.outstanding_before
   OR dr.id<>p.principal_account_id OR cr.id<>b.allowance_account OR q.reverses_journal_id<>b.write_off_journal
  THEN RAISE EXCEPTION 'Write-back must restore the linked write-off'; END IF;
 ELSIF q.kind='DISBURSEMENT_REVERSAL' THEN
  IF a.status NOT IN ('DISBURSED','PAR','DEFAULTED') OR q.amount<>l.principal OR l.next_sequence<>1 OR l.principal_paid<>0 OR l.interest_paid<>0
   OR EXISTS(SELECT 1 FROM loan_repayment_transactions WHERE loan_application_id=q.loan_id)
   OR b.allowance<>0 OR b.written_off<>0 OR dr.id<>p.money_account_id OR cr.id<>p.principal_account_id
   OR q.reverses_journal_id<>p.journal_id OR p.actor_id IN (q.actor_id,d.actor_id)
  THEN RAISE EXCEPTION 'Untouched disbursement and independent correction required'; END IF;
 END IF;
 IF q.reverses_journal_id IS NOT NULL THEN
  SELECT * INTO original FROM gl_journal WHERE id=q.reverses_journal_id FOR UPDATE;
  IF original.state<>'POSTED' OR j.effective_date<original.effective_date
   OR (SELECT count(*) FROM gl_journal_line WHERE journal_id=original.id)<>2
   OR EXISTS((SELECT account_id,debit,credit FROM gl_journal_line WHERE journal_id=j.id)
     EXCEPT ALL(SELECT account_id,credit,debit FROM gl_journal_line WHERE journal_id=original.id))
  THEN RAISE EXCEPTION 'Linked adjustment must exactly reverse original journal'; END IF;
 END IF;
END $$;

-- Extend the existing validator at one explicit source boundary; keep all existing guards.
DO $$ DECLARE definition text; BEGIN
 SELECT pg_get_functiondef('gl_validate_post()'::regprocedure) INTO definition;
 IF strpos(definition,'IF NEW.state<>''POSTED'' THEN RETURN NEW; END IF;')=0 THEN RAISE EXCEPTION 'Unexpected GL validator'; END IF;
 definition:=replace(definition,'IF NEW.state<>''POSTED'' THEN RETURN NEW; END IF;',
  'IF NEW.state<>''POSTED'' THEN RETURN NEW; END IF; IF NEW.source_type=''LOAN_ADJUSTMENT'' THEN PERFORM loan_adjustment_validate_journal(NEW); RETURN NEW; END IF;');
 EXECUTE definition;
 ALTER TABLE gl_journal DROP CONSTRAINT gl_core_identity;
 ALTER TABLE gl_journal ADD CONSTRAINT gl_core_identity CHECK ((NOT direct_post AND policy_id IS NOT NULL) OR
 (direct_post AND policy_id IS NULL AND checker_id IS NULL AND source_type IN
 ('CORE_RECEIPT','CORE_PAYMENT','CORE_JOURNAL','CORE_REVERSAL','LOAN_RECORD_DISBURSEMENT','LOAN_RECORD_PAYMENT','LOAN_RECORD_REVERSAL','LOAN_ADJUSTMENT')));
 SELECT pg_get_constraintdef(oid) INTO definition FROM pg_constraint WHERE conrelid='loan_applications'::regclass AND conname='loan_applications_status_check';
 ALTER TABLE loan_applications DROP CONSTRAINT loan_applications_status_check;
 EXECUTE 'ALTER TABLE loan_applications ADD CONSTRAINT loan_applications_status_check CHECK (status IN (''WRITTEN_OFF'',''DISBURSEMENT_REVERSED'') OR ('||regexp_replace(definition,'^CHECK \((.*)\)$','\1')||'))';
 SELECT pg_get_functiondef('accountant_loan_terms_protect()'::regprocedure) INTO definition;
 definition:=replace(definition,'recorded_review_transition(OLD.status,NEW.status,NEW.policy_snapshot::jsonb)',
  '(recorded_review_transition(OLD.status,NEW.status,NEW.policy_snapshot::jsonb) OR (OLD.status IN (''DISBURSED'',''PAR'',''DEFAULTED'') AND NEW.status IN (''WRITTEN_OFF'',''DISBURSEMENT_REVERSED'')) OR (OLD.status=''WRITTEN_OFF'' AND NEW.status=''DEFAULTED''))');
 EXECUTE definition;
END $$;

CREATE FUNCTION loan_adjustment_status_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE latest text;
BEGIN
 IF NEW.status IS DISTINCT FROM OLD.status AND (NEW.status IN ('WRITTEN_OFF','DISBURSEMENT_REVERSED') OR OLD.status IN ('WRITTEN_OFF','DISBURSEMENT_REVERSED')) THEN
  SELECT q.kind INTO latest FROM loan_adjustment_request q JOIN loan_adjustment_decision d ON d.request_id=q.id
   JOIN gl_journal j ON j.id=d.journal_id AND j.state='POSTED' WHERE q.loan_id=NEW.id ORDER BY d.event_sequence DESC LIMIT 1;
  IF NOT ((OLD.status IN ('DISBURSED','PAR','DEFAULTED') AND NEW.status='WRITTEN_OFF' AND latest='WRITE_OFF')
    OR (OLD.status IN ('DISBURSED','PAR','DEFAULTED') AND NEW.status='DISBURSEMENT_REVERSED' AND latest='DISBURSEMENT_REVERSAL')
    OR (OLD.status='WRITTEN_OFF' AND NEW.status='DEFAULTED' AND latest='WRITE_BACK'))
  OR latest IS NULL THEN RAISE EXCEPTION 'Loan adjustment status requires its posted source'; END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER loan_adjustment_status BEFORE UPDATE ON loan_applications FOR EACH ROW EXECUTE FUNCTION loan_adjustment_status_guard();

CREATE FUNCTION loan_adjustment_commit_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE q loan_adjustment_request; j gl_journal; status text;
BEGIN
 IF NEW.decision='REJECTED' THEN RETURN NULL; END IF;
 SELECT * INTO q FROM loan_adjustment_request WHERE id=NEW.request_id;
 SELECT * INTO j FROM gl_journal WHERE id=NEW.journal_id;
 SELECT a.status INTO status FROM loan_applications a WHERE a.id=q.loan_id;
 IF j.id IS NULL OR j.state<>'POSTED' OR j.source_type<>'LOAN_ADJUSTMENT' OR j.source_reference<>q.id::text
 OR (q.kind='WRITE_OFF' AND status<>'WRITTEN_OFF') OR (q.kind='WRITE_BACK' AND status<>'DEFAULTED')
 OR (q.kind='DISBURSEMENT_REVERSAL' AND status<>'DISBURSEMENT_REVERSED')
 THEN RAISE EXCEPTION 'Decision, journal and loan state must commit together'; END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER loan_adjustment_commit AFTER INSERT ON loan_adjustment_decision DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION loan_adjustment_commit_guard();



CREATE FUNCTION loan_adjustment_payment_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 PERFORM 1 FROM loan_ledgers WHERE loan_application_id=NEW.loan_application_id FOR UPDATE;
 IF EXISTS(SELECT 1 FROM loan_applications WHERE id=NEW.loan_application_id AND status IN ('WRITTEN_OFF','DISBURSEMENT_REVERSED'))
 THEN RAISE EXCEPTION 'Written-off or reversed loans cannot receive ordinary repayment mutations'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER loan_adjustment_payment BEFORE INSERT ON loan_repayment_transactions FOR EACH ROW EXECUTE FUNCTION loan_adjustment_payment_guard();
