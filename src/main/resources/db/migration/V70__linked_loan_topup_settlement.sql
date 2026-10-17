-- Internal refinancing has its own immutable source. It never creates a cash receipt.
SET check_function_bodies=on;
CREATE TABLE accountant_loan_topup (
 id uuid PRIMARY KEY,loan_id uuid NOT NULL,source_id uuid NOT NULL CHECK(source_id<>loan_id),posting_id uuid NOT NULL REFERENCES accountant_loan_post(id),
 sacco_id varchar(255) NOT NULL,station_id varchar(255) NOT NULL,request_key uuid NOT NULL,payload_hash varchar(64) NOT NULL,
 effective_date date NOT NULL,principal numeric(18,2) NOT NULL CHECK(principal>0),interest numeric(18,2) NOT NULL CHECK(interest>=0),
 excluded_interest numeric(18,2) NOT NULL CHECK(excluded_interest>=0),cash numeric(18,2) NOT NULL CHECK(cash>0),source_sequence bigint NOT NULL,
 transaction_id uuid NOT NULL UNIQUE REFERENCES loan_repayment_transactions(id),journal_id uuid NOT NULL UNIQUE REFERENCES gl_journal(id),
 reverses_id uuid UNIQUE REFERENCES accountant_loan_topup(id),actor_id uuid NOT NULL REFERENCES members(id),
 evidence varchar(500) NOT NULL CHECK(length(trim(evidence))>0),reason varchar(500) NOT NULL,recorded_at timestamptz NOT NULL,
 UNIQUE(sacco_id,station_id,request_key),FOREIGN KEY(loan_id,sacco_id,station_id) REFERENCES accountant_loan_record(loan_id,sacco_id,station_id),
 FOREIGN KEY(source_id,sacco_id,station_id) REFERENCES accountant_loan_record(loan_id,sacco_id,station_id)
);
CREATE UNIQUE INDEX ux_topup_one_opening ON accountant_loan_topup(loan_id) WHERE reverses_id IS NULL;
CREATE INDEX ix_topup_source ON accountant_loan_topup(source_id,recorded_at,id);
CREATE TRIGGER accountant_topup_immutable BEFORE UPDATE OR DELETE ON accountant_loan_topup FOR EACH ROW EXECUTE FUNCTION gl_protect_evidence();

-- Do not alter existing financial migrations or overload PAYMENT with non-cash movements.
DO $$ DECLARE c record; BEGIN
 FOR c IN SELECT conname,pg_get_constraintdef(oid) definition FROM pg_constraint WHERE conrelid='loan_repayment_transactions'::regclass AND contype='c' LOOP
  IF c.definition LIKE '%kind%' OR c.definition LIKE '%channel%IN%' OR c.definition LIKE '%channel%ANY%' THEN
   EXECUTE format('ALTER TABLE loan_repayment_transactions DROP CONSTRAINT %I',c.conname);
  END IF;
 END LOOP;
END $$;
ALTER TABLE loan_repayment_transactions ADD CONSTRAINT loan_movement_kind CHECK(kind IN ('PAYMENT','REVERSAL','SETTLEMENT','SETTLEMENT_UNDO'));
ALTER TABLE loan_repayment_transactions ADD CONSTRAINT loan_movement_channel CHECK(channel IN ('CASH','BANK','MOBILE_MONEY','INTERNAL'));
ALTER TABLE loan_repayment_transactions ADD CONSTRAINT loan_movement_identity CHECK
 ((kind IN ('PAYMENT','REVERSAL') AND channel<>'INTERNAL') OR (kind IN ('SETTLEMENT','SETTLEMENT_UNDO') AND channel='INTERNAL'));
ALTER TABLE loan_repayment_transactions ADD CONSTRAINT loan_movement_reversal CHECK
 ((kind IN ('PAYMENT','SETTLEMENT') AND reverses_transaction_id IS NULL) OR
  (kind IN ('REVERSAL','SETTLEMENT_UNDO') AND reverses_transaction_id IS NOT NULL AND length(trim(reason))>0));
CREATE UNIQUE INDEX ux_topup_transaction_reference ON loan_repayment_transactions(sacco_id,station_id,channel_reference) WHERE kind='SETTLEMENT';

CREATE FUNCTION loan_topup_validate(j gl_journal) RETURNS void LANGUAGE plpgsql AS $$
DECLARE e accountant_loan_topup; original accountant_loan_topup; t loan_repayment_transactions; p accountant_loan_post;
 n loan_applications; o loan_applications; b loan_ledgers; period accounting_period; cnt integer; total numeric; original_journal gl_journal;
BEGIN
 SELECT * INTO e FROM accountant_loan_topup WHERE journal_id=j.id;
 SELECT * INTO p FROM accountant_loan_post WHERE id=e.posting_id;
 SELECT * INTO n FROM loan_applications WHERE id=e.loan_id;
 SELECT * INTO o FROM loan_applications WHERE id=e.source_id;
 SELECT * INTO b FROM loan_ledgers WHERE loan_application_id=e.source_id FOR UPDATE;
 SELECT * INTO t FROM loan_repayment_transactions WHERE id=e.transaction_id;
 SELECT * INTO period FROM accounting_period WHERE id=j.period_id FOR SHARE;
 IF e.id IS NULL OR NOT j.direct_post OR j.policy_id IS NOT NULL OR j.checker_id IS NOT NULL OR j.currency<>'TZS'
 OR period.id IS NULL OR period.state<>'OPEN' OR period.sacco_id<>e.sacco_id OR e.effective_date NOT BETWEEN period.starts_on AND period.ends_on OR e.effective_date>CURRENT_DATE
 OR j.sacco_id<>e.sacco_id OR j.station_id<>e.station_id OR j.source_reference<>e.id::text OR j.request_key<>e.request_key OR j.payload_hash<>e.payload_hash
 OR j.maker_id<>e.actor_id OR j.effective_date<>e.effective_date OR j.posted_at<>e.recorded_at
 OR p.kind<>'DISBURSEMENT' OR p.state<>'POSTED' OR p.loan_id<>e.loan_id OR p.sacco_id<>e.sacco_id OR p.station_id<>e.station_id
 OR n.sacco_id<>e.sacco_id OR n.station_id<>e.station_id OR o.sacco_id<>e.sacco_id OR o.station_id<>e.station_id OR n.applicant_member_id<>o.applicant_member_id
 OR n.top_up_source_loan_id<>o.id OR n.amount<>e.principal+e.interest+e.cash
 OR t.loan_application_id<>e.source_id OR t.sacco_id<>e.sacco_id OR t.station_id<>e.station_id OR t.channel<>'INTERNAL'
 OR t.channel_reference<>e.id::text OR t.request_key<>e.request_key OR t.payment_date<>e.effective_date OR t.actor_member_id<>e.actor_id
 OR t.principal_amount<>e.principal OR t.interest_amount<>e.interest OR t.sequence<>e.source_sequence
 OR NOT EXISTS(SELECT 1 FROM members WHERE id=e.actor_id AND sacco_id=e.sacco_id AND station_id=e.station_id AND status='ACTIVE')
 OR NOT EXISTS(SELECT 1 FROM accounting_outbox WHERE journal_id=j.id AND sacco_id=e.sacco_id AND station_id=e.station_id)
 THEN RAISE EXCEPTION 'Top-up source, scope, journal and loan movements must agree'; END IF;
 SELECT count(*),sum(debit-credit) INTO cnt,total FROM gl_journal_line WHERE journal_id=j.id;
 IF total<>0 OR cnt NOT BETWEEN 3 AND 4 OR EXISTS(SELECT 1 FROM gl_journal_line z JOIN gl_account a ON a.id=z.account_id WHERE z.journal_id=j.id AND (a.sacco_id<>e.sacco_id OR NOT a.active))
 THEN RAISE EXCEPTION 'Exact balanced top-up journal required'; END IF;
 IF e.reverses_id IS NULL THEN
  IF j.source_type<>'LOAN_TOPUP' OR j.reverses_id IS NOT NULL OR p.journal_id<>j.id OR p.amount<>n.amount
   OR n.status<>'DISBURSED' OR o.status<>'PAID' OR b.principal_paid<>b.principal OR t.kind<>'SETTLEMENT' OR t.loan_status_before<>'DISBURSED'
   OR n.disbursement_date<>e.effective_date OR p.effective_date<>e.effective_date OR b.next_sequence<>t.sequence+1
   OR EXISTS(SELECT 1 FROM loan_ledger_installments WHERE loan_application_id=o.id AND (principal_paid<>principal OR (due_date<=e.effective_date AND interest_paid<>interest)))
   OR e.excluded_interest<>(SELECT coalesce(sum(interest-interest_paid),0) FROM loan_ledger_installments WHERE loan_application_id=o.id AND due_date>e.effective_date)
   OR EXISTS(SELECT 1 FROM loan_adjustment_balance WHERE loan_id=o.id AND (allowance<>0 OR written_off<>0 OR last_date>e.effective_date))
  THEN RAISE EXCEPTION 'Top-up must settle a verified active source and open the replacement'; END IF;
  IF NOT EXISTS(SELECT 1 FROM gl_account WHERE id=p.money_account_id AND kind='POSTING' AND type='ASSET' AND purpose IN ('CASH','BANK','MOBILE_MONEY'))
   OR NOT EXISTS(SELECT 1 FROM gl_account WHERE id=p.principal_account_id AND kind='CONTROL' AND type='ASSET' AND purpose='LOAN_PRINCIPAL')
   OR cnt<>(3+CASE WHEN e.interest>0 THEN 1 ELSE 0 END)
   OR EXISTS((SELECT account_id,debit,credit FROM gl_journal_line WHERE journal_id=j.id) EXCEPT ALL
    (SELECT p.principal_account_id,n.amount,0 UNION ALL SELECT sp.principal_account_id,0,e.principal FROM accountant_loan_post sp WHERE sp.loan_id=o.id AND sp.kind='DISBURSEMENT'
     UNION ALL SELECT p.money_account_id,0,e.cash UNION ALL
     SELECT z.account_id,0,e.interest FROM gl_journal_line z JOIN gl_account a ON a.id=z.account_id WHERE z.journal_id=j.id AND z.credit=e.interest AND e.interest>0 AND a.kind='POSTING' AND a.purpose='CLEARING'))
  THEN RAISE EXCEPTION 'Top-up journal must distinguish principal transfer, due interest and net cash'; END IF;
 ELSE
  SELECT * INTO original FROM accountant_loan_topup WHERE id=e.reverses_id;
  SELECT * INTO original_journal FROM gl_journal WHERE id=original.journal_id FOR UPDATE;
  IF j.source_type<>'LOAN_TOPUP_UNDO' OR j.reverses_id<>original.journal_id OR original_journal.state<>'POSTED'
   OR original.actor_id=e.actor_id OR e.effective_date<original.effective_date OR length(trim(e.reason))=0
   OR ROW(e.loan_id,e.source_id,e.posting_id,e.principal,e.interest,e.excluded_interest,e.cash) IS DISTINCT FROM
      ROW(original.loan_id,original.source_id,original.posting_id,original.principal,original.interest,original.excluded_interest,original.cash)
   OR t.kind<>'SETTLEMENT_UNDO' OR t.reverses_transaction_id<>original.transaction_id
   OR n.status<>'DISBURSEMENT_REVERSED' OR o.status<>'DISBURSED'
   OR EXISTS(SELECT 1 FROM loan_ledgers WHERE loan_application_id=n.id AND (next_sequence<>1 OR principal_paid<>0 OR interest_paid<>0))
   OR EXISTS(SELECT 1 FROM loan_adjustment_request WHERE loan_id IN(n.id,o.id))
   OR cnt<>(SELECT count(*) FROM gl_journal_line WHERE journal_id=original.journal_id)
   OR EXISTS((SELECT account_id,debit,credit FROM gl_journal_line WHERE journal_id=j.id) EXCEPT ALL (SELECT account_id,credit,debit FROM gl_journal_line WHERE journal_id=original.journal_id))
  THEN RAISE EXCEPTION 'Independent top-up reversal requires untouched replacement and exact linked entries'; END IF;
 END IF;
 IF (SELECT coalesce(sum(principal_amount),0) FROM loan_repayment_allocations WHERE transaction_id=t.id)<>e.principal
 OR (SELECT coalesce(sum(interest_amount),0) FROM loan_repayment_allocations WHERE transaction_id=t.id)<>e.interest
 OR EXISTS(SELECT 1 FROM loan_repayment_allocations a JOIN loan_ledger_installments i ON i.id=a.installment_id WHERE a.transaction_id=t.id AND (i.loan_application_id<>o.id OR (i.due_date>CASE WHEN e.reverses_id IS NULL THEN e.effective_date ELSE original.effective_date END AND a.interest_amount<>0)))
 OR b.principal_paid<>(SELECT coalesce(sum(CASE WHEN kind IN('PAYMENT','SETTLEMENT') THEN principal_amount ELSE -principal_amount END),0) FROM loan_repayment_transactions WHERE loan_application_id=o.id)
 OR b.interest_paid<>(SELECT coalesce(sum(CASE WHEN kind IN('PAYMENT','SETTLEMENT') THEN interest_amount ELSE -interest_amount END),0) FROM loan_repayment_transactions WHERE loan_application_id=o.id)
 OR EXISTS(SELECT 1 FROM loan_ledger_installments i WHERE i.loan_application_id=o.id AND
  (i.principal_paid<>(SELECT coalesce(sum(CASE WHEN mt.kind IN('PAYMENT','SETTLEMENT') THEN ma.principal_amount ELSE -ma.principal_amount END),0) FROM loan_repayment_allocations ma JOIN loan_repayment_transactions mt ON mt.id=ma.transaction_id WHERE ma.installment_id=i.id)
   OR i.interest_paid<>(SELECT coalesce(sum(CASE WHEN mt.kind IN('PAYMENT','SETTLEMENT') THEN ma.interest_amount ELSE -ma.interest_amount END),0) FROM loan_repayment_allocations ma JOIN loan_repayment_transactions mt ON mt.id=ma.transaction_id WHERE ma.installment_id=i.id)))
 THEN RAISE EXCEPTION 'Top-up allocations and ledger balances must reconcile'; END IF;
END $$;

CREATE FUNCTION loan_topup_commit() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE j gl_journal; BEGIN
 SELECT * INTO j FROM gl_journal WHERE id=NEW.journal_id;
 IF j.state IS DISTINCT FROM 'POSTED' THEN RAISE EXCEPTION 'Top-up must commit as a posted transaction'; END IF;
 PERFORM loan_topup_validate(j); RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER loan_topup_committed AFTER INSERT ON accountant_loan_topup DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION loan_topup_commit();

CREATE FUNCTION loan_topup_transaction_commit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF NEW.kind IN('SETTLEMENT','SETTLEMENT_UNDO') AND NOT EXISTS(SELECT 1 FROM accountant_loan_topup WHERE transaction_id=NEW.id)
 THEN RAISE EXCEPTION 'Internal settlement requires its owning top-up'; END IF; RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER loan_topup_transaction AFTER INSERT ON loan_repayment_transactions DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION loan_topup_transaction_commit();

CREATE FUNCTION loan_topup_dependency_guard() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF EXISTS(SELECT 1 FROM loan_repayment_transactions t WHERE t.loan_application_id=NEW.loan_application_id AND t.kind='SETTLEMENT'
  AND NOT EXISTS(SELECT 1 FROM loan_repayment_transactions r WHERE r.reverses_transaction_id=t.id)) AND NEW.kind<>'SETTLEMENT_UNDO'
 THEN RAISE EXCEPTION 'Settled loan requires linked top-up correction'; END IF;
 IF NEW.payment_date<(SELECT max(payment_date) FROM loan_repayment_transactions WHERE loan_application_id=NEW.loan_application_id AND kind IN('SETTLEMENT','SETTLEMENT_UNDO'))
 THEN RAISE EXCEPTION 'Movement cannot precede internal settlement history'; END IF; RETURN NEW;
END $$;
CREATE TRIGGER loan_topup_dependency BEFORE INSERT ON loan_repayment_transactions FOR EACH ROW EXECUTE FUNCTION loan_topup_dependency_guard();

DO $$ DECLARE definition text; BEGIN
 SELECT pg_get_functiondef('gl_validate_post()'::regprocedure) INTO definition;
 definition:=replace(definition,'IF NEW.state<>''POSTED'' THEN RETURN NEW; END IF;',
  'IF NEW.state<>''POSTED'' THEN RETURN NEW; END IF; IF NEW.source_type IN(''LOAN_TOPUP'',''LOAN_TOPUP_UNDO'') THEN PERFORM loan_topup_validate(NEW); RETURN NEW; END IF;');
 EXECUTE definition;
 ALTER TABLE gl_journal DROP CONSTRAINT gl_core_identity;
 ALTER TABLE gl_journal ADD CONSTRAINT gl_core_identity CHECK ((NOT direct_post AND policy_id IS NOT NULL) OR
 (direct_post AND policy_id IS NULL AND checker_id IS NULL AND source_type IN
 ('CORE_RECEIPT','CORE_PAYMENT','CORE_JOURNAL','CORE_REVERSAL','LOAN_RECORD_DISBURSEMENT','LOAN_RECORD_PAYMENT','LOAN_RECORD_REVERSAL','LOAN_ADJUSTMENT','LOAN_TOPUP','LOAN_TOPUP_UNDO')));
 SELECT pg_get_functiondef('accountant_loan_commit()'::regprocedure) INTO definition;
 definition:=replace(definition,'CASE WHEN kind=''PAYMENT'' THEN','CASE WHEN kind IN(''PAYMENT'',''SETTLEMENT'') THEN');EXECUTE definition;
 SELECT pg_get_functiondef('loan_adjustment_status_guard()'::regprocedure) INTO definition;
 definition:=replace(definition,'IF NEW.status IS DISTINCT',
  'IF NEW.status=''DISBURSEMENT_REVERSED'' AND EXISTS(SELECT 1 FROM accountant_loan_topup WHERE loan_id=NEW.id AND reverses_id IS NOT NULL) THEN RETURN NEW; END IF; IF NEW.status IS DISTINCT');EXECUTE definition;
END $$;

CREATE FUNCTION loan_topup_contract_guard() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF OLD.top_up_source_loan_id IS DISTINCT FROM NEW.top_up_source_loan_id AND EXISTS(SELECT 1 FROM accountant_loan_record WHERE loan_id=OLD.id)
 THEN RAISE EXCEPTION 'Recorded top-up source is immutable'; END IF; RETURN NEW;
END $$;
CREATE TRIGGER loan_topup_contract BEFORE UPDATE ON loan_applications FOR EACH ROW EXECUTE FUNCTION loan_topup_contract_guard();

CREATE FUNCTION loan_topup_application_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE source loan_applications;
BEGIN
 IF NEW.top_up_source_loan_id IS NULL OR NOT EXISTS(SELECT 1 FROM accountant_loan_record WHERE loan_id=NEW.top_up_source_loan_id) THEN RETURN NEW; END IF;
 PERFORM 1 FROM accountant_loan_record WHERE loan_id=NEW.top_up_source_loan_id FOR UPDATE;
 SELECT * INTO source FROM loan_applications WHERE id=NEW.top_up_source_loan_id;
 IF source.sacco_id IS DISTINCT FROM NEW.sacco_id OR source.station_id IS DISTINCT FROM NEW.station_id OR source.applicant_member_id IS DISTINCT FROM NEW.applicant_member_id
 OR source.status<>'DISBURSED' OR EXISTS(SELECT 1 FROM loan_applications WHERE top_up_source_loan_id=source.id AND status NOT IN
  ('MANAGER_REJECTED','LOAN_OFFICER_REJECTED','CHAIRPERSON_REJECTED','BOARD_REJECTED','CREDIT_COMMITTEE_REJECTED','ACCOUNTANT_REJECTED','REJECTED','DISBURSEMENT_REVERSED','PAID'))
 THEN RAISE EXCEPTION 'A scoped active source with no pending top-up is required'; END IF; RETURN NEW;
END $$;
CREATE TRIGGER loan_topup_application BEFORE INSERT ON loan_applications FOR EACH ROW EXECUTE FUNCTION loan_topup_application_guard();

CREATE FUNCTION recorded_topup_funding_guard() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF NEW.status='DISBURSED' AND OLD.status IS DISTINCT FROM NEW.status AND NEW.top_up_source_loan_id IS NOT NULL
  AND EXISTS(SELECT 1 FROM accountant_loan_record WHERE loan_id=NEW.id)
  AND NOT EXISTS(SELECT 1 FROM accountant_loan_topup e JOIN gl_journal j ON j.id=e.journal_id AND j.state='POSTED'
   WHERE e.loan_id=NEW.id AND e.source_id=NEW.top_up_source_loan_id AND e.reverses_id IS NULL)
 THEN RAISE EXCEPTION 'Recorded top-up funding requires its linked settlement'; END IF; RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER recorded_topup_funded AFTER UPDATE ON loan_applications DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION recorded_topup_funding_guard();

-- Loan/client history includes internal movements, while receipt registers remain cash-only.
CREATE VIEW accountant_loan_movement AS
 SELECT p.* FROM accountant_loan_post p
 UNION ALL
 SELECT movement.* FROM accountant_loan_topup e JOIN accountant_loan_post p ON p.id=e.posting_id
 JOIN accountant_loan_post source ON source.loan_id=e.source_id AND source.kind='DISBURSEMENT'
 JOIN loan_repayment_transactions t ON t.id=e.transaction_id
 LEFT JOIN accountant_loan_topup original ON original.id=e.reverses_id
 CROSS JOIN LATERAL jsonb_populate_record(NULL::accountant_loan_post,jsonb_build_object(
  'id',e.transaction_id,'loan_id',e.source_id,'sacco_id',e.sacco_id,'station_id',e.station_id,'kind',t.kind,'state','POSTED',
  'request_key',e.request_key,'payload_hash',e.payload_hash,'effective_date',e.effective_date,'reference',t.receipt_reference,
  'amount',e.principal+e.interest,'principal_amount',e.principal,'interest_amount',e.interest,
  'principal_balance',CASE WHEN e.reverses_id IS NULL THEN 0 ELSE e.principal END,
  'money_account_id',p.money_account_id,'principal_account_id',source.principal_account_id,'interest_account_id',NULL,
  'money_account_name','','principal_account_name',source.principal_account_name,'interest_account_name','',
  'evidence',e.evidence,'notes',e.reason,'actor_id',e.actor_id,'recorded_at',e.recorded_at,'journal_id',e.journal_id,
  'transaction_id',e.transaction_id,'reverses_id',original.transaction_id)) movement;

CREATE OR REPLACE FUNCTION core_post_commit_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE journal uuid;
BEGIN
 IF TG_TABLE_NAME='gl_journal' THEN journal:=NEW.id; ELSE journal:=NEW.journal_id; END IF;
 IF EXISTS(SELECT 1 FROM gl_journal j JOIN accountant_loan_topup e ON e.journal_id=j.id
  WHERE j.id=journal AND j.direct_post AND j.state='POSTED' AND j.source_type IN('LOAN_TOPUP','LOAN_TOPUP_UNDO')) THEN RETURN NULL; END IF;
 IF EXISTS(SELECT 1 FROM gl_journal j JOIN accountant_loan_post x ON x.journal_id=j.id WHERE j.id=journal AND j.direct_post AND j.state='POSTED' AND x.state='POSTED' AND j.source_type LIKE 'LOAN_RECORD_%') THEN RETURN NULL; END IF;
 IF EXISTS(SELECT 1 FROM gl_journal j JOIN loan_adjustment_decision d ON d.journal_id=j.id JOIN loan_adjustment_request q ON q.id=d.request_id
  WHERE j.id=journal AND j.direct_post AND j.state='POSTED' AND j.source_type='LOAN_ADJUSTMENT' AND j.source_reference=q.id::text
  AND d.decision='APPROVED' AND j.sacco_id=q.sacco_id AND j.station_id=q.station_id) THEN RETURN NULL; END IF;
 IF NOT EXISTS(SELECT 1 FROM gl_journal j JOIN accounting_voucher v ON v.journal_id=j.id WHERE j.id=journal AND j.direct_post AND j.state='POSTED')
 THEN RAISE EXCEPTION 'Core vouchers must commit as posted documents'; END IF; RETURN NULL;
END $$;
