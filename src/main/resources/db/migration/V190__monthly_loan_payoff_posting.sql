ALTER TABLE loan_monthly_interest_terms DROP CONSTRAINT loan_monthly_interest_terms_pkey,
 ADD PRIMARY KEY (loan_id,first_installment_number), ADD UNIQUE (loan_id,effective_date), ADD UNIQUE (loan_id,request_key);

ALTER TABLE loan_repayment_transactions ADD COLUMN interest_returned numeric(18,2) NOT NULL DEFAULT 0 CHECK (interest_returned>=0);
ALTER TABLE loan_repayment_allocations ADD COLUMN interest_returned numeric(18,2) NOT NULL DEFAULT 0 CHECK (interest_returned>=0);
ALTER TABLE loan_repayment_transactions DROP CONSTRAINT loan_movement_kind, DROP CONSTRAINT loan_movement_identity, DROP CONSTRAINT loan_movement_reversal;
ALTER TABLE loan_repayment_transactions ADD CONSTRAINT loan_movement_kind CHECK(kind IN ('PAYMENT','REVERSAL','SETTLEMENT','SETTLEMENT_UNDO','PAYOFF','PAYOFF_UNDO')),
 ADD CONSTRAINT loan_movement_identity CHECK ((kind IN ('PAYMENT','REVERSAL') AND channel<>'INTERNAL') OR
   (kind IN ('SETTLEMENT','SETTLEMENT_UNDO','PAYOFF','PAYOFF_UNDO') AND channel='INTERNAL')),
 ADD CONSTRAINT loan_movement_reversal CHECK ((kind IN ('PAYMENT','SETTLEMENT','PAYOFF') AND reverses_transaction_id IS NULL) OR
   (kind IN ('REVERSAL','SETTLEMENT_UNDO','PAYOFF_UNDO') AND reverses_transaction_id IS NOT NULL AND length(trim(reason))>0)),
 ADD CONSTRAINT loan_interest_return_source CHECK (interest_returned=0 OR kind IN ('PAYOFF','PAYOFF_UNDO'));
DO $$ DECLARE name text; BEGIN
 SELECT conname INTO STRICT name FROM pg_constraint WHERE conrelid='loan_repayment_transactions'::regclass
   AND contype='c' AND pg_get_constraintdef(oid) IN ('CHECK ((amount > (0)::numeric))','CHECK ((amount > 0))');
 EXECUTE format('ALTER TABLE loan_repayment_transactions DROP CONSTRAINT %I',name);
END $$;
ALTER TABLE loan_repayment_transactions ADD CONSTRAINT loan_movement_amount CHECK (amount>0 OR (amount=0 AND kind IN ('PAYOFF','PAYOFF_UNDO')));
ALTER TABLE loan_repayment_allocations DROP CONSTRAINT repayment_allocation_nonempty;
ALTER TABLE loan_repayment_allocations ADD CONSTRAINT repayment_allocation_nonempty CHECK(principal_amount+interest_amount+interest_excluded+interest_returned>0);

CREATE TABLE loan_payoff_post (
 id uuid PRIMARY KEY, loan_id uuid NOT NULL, sacco_id varchar(255) NOT NULL, station_id varchar(255) NOT NULL,
 transaction_id uuid NOT NULL UNIQUE REFERENCES loan_repayment_transactions(id),
 journal_id uuid UNIQUE, effective_date date NOT NULL, request_key uuid NOT NULL, payload_hash varchar(64) NOT NULL,
 actor_id uuid NOT NULL REFERENCES members(id), recorded_at timestamptz NOT NULL, reverses_id uuid UNIQUE REFERENCES loan_payoff_post(id),
 principal_account_id uuid NOT NULL, interest_account_id uuid NOT NULL, money_account_id uuid, payable_account_id uuid,
 cash numeric(18,2) NOT NULL CHECK(cash>=0), charges numeric(18,2) NOT NULL CHECK(charges>=0),
 refund numeric(18,2) NOT NULL CHECK(refund>=0), CHECK(cash=0 OR refund=0),
 CHECK((cash=0)=(money_account_id IS NULL)), CHECK((refund=0)=(payable_account_id IS NULL)),
 FOREIGN KEY(loan_id,sacco_id,station_id) REFERENCES accountant_loan_record(loan_id,sacco_id,station_id),
 FOREIGN KEY(journal_id,sacco_id,station_id) REFERENCES gl_journal(id,sacco_id,station_id),
 FOREIGN KEY(principal_account_id,sacco_id) REFERENCES gl_account(id,sacco_id),
 FOREIGN KEY(interest_account_id,sacco_id) REFERENCES gl_account(id,sacco_id),
 FOREIGN KEY(money_account_id,sacco_id) REFERENCES gl_account(id,sacco_id),
 FOREIGN KEY(payable_account_id,sacco_id) REFERENCES gl_account(id,sacco_id),
 UNIQUE(sacco_id,station_id,request_key)
);
CREATE INDEX loan_payoff_history ON loan_payoff_post(loan_id,effective_date,recorded_at);
CREATE TRIGGER loan_payoff_post_immutable BEFORE UPDATE OR DELETE ON loan_payoff_post FOR EACH ROW EXECUTE FUNCTION gl_protect_evidence();

CREATE TABLE loan_payoff_refund (
 payoff_id uuid NOT NULL REFERENCES loan_payoff_post(id), voucher_id uuid PRIMARY KEY REFERENCES accounting_voucher(id)
);
CREATE INDEX loan_payoff_refund_lookup ON loan_payoff_refund(payoff_id);
CREATE TRIGGER loan_payoff_refund_immutable BEFORE UPDATE OR DELETE ON loan_payoff_refund FOR EACH ROW EXECUTE FUNCTION gl_protect_evidence();

-- Existing charge collections continue to own their original sources. A full payoff owns its charge allocations by journal.
DO $$ DECLARE name text; BEGIN
 SELECT conname INTO STRICT name FROM pg_constraint WHERE conrelid='loan_charge_event'::regclass
   AND contype='c' AND pg_get_constraintdef(oid) LIKE '%command_id IS NOT NULL%posting_id IS NULL%';
 EXECUTE format('ALTER TABLE loan_charge_event DROP CONSTRAINT %I',name);
END $$;
ALTER TABLE loan_charge_event ADD CONSTRAINT loan_charge_event_source_check CHECK (
 (kind IN ('ASSESS','ASSESS_UNDO') AND command_id IS NOT NULL AND posting_id IS NULL) OR
 (kind NOT IN ('ASSESS','ASSESS_UNDO') AND command_id IS NULL AND (posting_id IS NOT NULL OR kind IN ('COLLECT','COLLECT_UNDO'))));

CREATE FUNCTION payoff_movement_committed() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.kind IN ('PAYOFF','PAYOFF_UNDO') AND NOT EXISTS(SELECT 1 FROM loan_payoff_post WHERE transaction_id=NEW.id)
    OR NEW.kind NOT IN ('PAYOFF','PAYOFF_UNDO') AND EXISTS(SELECT 1 FROM loan_repayment_allocations WHERE transaction_id=NEW.id AND interest_returned<>0)
 THEN RAISE EXCEPTION 'Payoff credits require their owning settlement'; END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER payoff_movement_source AFTER INSERT ON loan_repayment_transactions
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION payoff_movement_committed();

CREATE FUNCTION validate_payoff_post(p loan_payoff_post) RETURNS void LANGUAGE plpgsql AS $$
DECLARE t loan_repayment_transactions; j gl_journal; original loan_payoff_post; undo boolean;
BEGIN
 SELECT * INTO STRICT t FROM loan_repayment_transactions WHERE id=p.transaction_id;
 undo:=p.reverses_id IS NOT NULL;
 IF t.kind<>(CASE WHEN undo THEN 'PAYOFF_UNDO' ELSE 'PAYOFF' END) OR t.loan_application_id<>p.loan_id
   OR t.sacco_id<>p.sacco_id OR t.station_id<>p.station_id OR t.request_key<>p.request_key OR t.payment_date<>p.effective_date
   OR t.actor_member_id<>p.actor_id OR t.channel_reference<>p.id::text OR p.effective_date>CURRENT_DATE
   OR NOT EXISTS(SELECT 1 FROM loan_monthly_interest_terms WHERE loan_id=p.loan_id AND effective_date<=p.effective_date)
   OR NOT EXISTS(SELECT 1 FROM members m WHERE m.id=p.actor_id AND m.is_member IS FALSE AND m.status='ACTIVE' AND m.sacco_id=p.sacco_id AND m.station_id=p.station_id)
   OR NOT EXISTS(SELECT 1 FROM loan_product_account_binding b WHERE b.loan_id=p.loan_id AND b.principal_account_id=p.principal_account_id AND b.interest_account_id=p.interest_account_id)
   OR t.amount+p.charges+p.refund<>p.cash+t.interest_returned
   OR (SELECT ROW(coalesce(sum(a.principal_amount),0),coalesce(sum(a.interest_amount),0),coalesce(sum(a.interest_returned),0),coalesce(sum(a.interest_excluded),0))
       FROM loan_repayment_allocations a WHERE a.transaction_id=t.id) IS DISTINCT FROM ROW(t.principal_amount,t.interest_amount,t.interest_returned,t.future_interest_excluded)
   OR EXISTS(SELECT 1 FROM loan_repayment_allocations a JOIN loan_ledger_installments i ON i.id=a.installment_id WHERE a.transaction_id=t.id AND i.loan_application_id<>p.loan_id)
 THEN RAISE EXCEPTION 'Payoff amounts and scoped loan movements must match'; END IF;
 IF NOT EXISTS(SELECT 1 FROM loan_ledgers l WHERE l.loan_application_id=p.loan_id
      AND l.next_sequence=t.sequence+1 AND l.last_payment_date=p.effective_date
      AND l.principal_paid=(SELECT sum(i.principal_paid) FROM loan_ledger_installments i WHERE i.loan_application_id=p.loan_id)
      AND l.interest_paid=(SELECT sum(i.interest_paid) FROM loan_ledger_installments i WHERE i.loan_application_id=p.loan_id))
 THEN RAISE EXCEPTION 'Payoff must advance and reconcile the loan ledger'; END IF;
 IF p.money_account_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM gl_account WHERE id=p.money_account_id AND active AND type='ASSET' AND kind='POSTING' AND purpose IN ('CASH','BANK','MOBILE_MONEY'))
   OR p.payable_account_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM gl_account WHERE id=p.payable_account_id AND active AND type='LIABILITY' AND kind='POSTING' AND purpose='PAYABLE')
 THEN RAISE EXCEPTION 'Active receiving and refund payable accounts required'; END IF;
 IF undo THEN
   SELECT * INTO STRICT original FROM loan_payoff_post WHERE id=p.reverses_id;
   IF original.reverses_id IS NOT NULL OR original.loan_id<>p.loan_id OR original.actor_id=p.actor_id OR original.effective_date>p.effective_date
      OR t.reverses_transaction_id<>original.transaction_id OR ROW(p.cash,p.charges,p.refund,p.money_account_id,p.payable_account_id)
           IS DISTINCT FROM ROW(original.cash,original.charges,original.refund,original.money_account_id,original.payable_account_id)
      OR EXISTS((SELECT installment_id,principal_amount,interest_amount,interest_returned,interest_excluded FROM loan_repayment_allocations WHERE transaction_id=t.id)
           EXCEPT ALL (SELECT installment_id,principal_amount,interest_amount,interest_returned,interest_excluded FROM loan_repayment_allocations WHERE transaction_id=original.transaction_id))
      OR (SELECT count(*) FROM loan_repayment_allocations WHERE transaction_id=t.id)<>(SELECT count(*) FROM loan_repayment_allocations WHERE transaction_id=original.transaction_id)
      OR EXISTS(SELECT 1 FROM loan_payoff_refund r WHERE r.payoff_id=original.id AND NOT EXISTS(
         SELECT 1 FROM accounting_voucher v WHERE v.reverses_id=r.voucher_id AND v.effective_date<=p.effective_date))
   THEN RAISE EXCEPTION 'Complete independent payoff correction required'; END IF;
 ELSIF EXISTS(SELECT 1 FROM loan_applications WHERE id=p.loan_id AND status<>'PAID') OR EXISTS(
   SELECT 1 FROM loan_ledger_installments WHERE loan_application_id=p.loan_id AND (principal_paid<>principal OR interest<>interest_paid+interest_excluded+interest_waived))
 THEN RAISE EXCEPTION 'Payoff must close all instalments'; END IF;

 IF t.amount+p.charges+t.interest_returned=0 THEN
   IF p.journal_id IS NOT NULL THEN RAISE EXCEPTION 'Zero-value closure cannot create a cash or GL entry'; END IF;
   IF NOT EXISTS(SELECT 1 FROM accounting_period WHERE sacco_id=p.sacco_id AND state='OPEN' AND p.effective_date BETWEEN starts_on AND ends_on)
   THEN RAISE EXCEPTION 'Open payoff period required'; END IF;
   RETURN;
 END IF;
 SELECT * INTO STRICT j FROM gl_journal WHERE id=p.journal_id;
 IF j.state<>'POSTED' OR NOT j.direct_post OR j.source_type<>(CASE WHEN undo THEN 'MONTHLY_PAYOFF_UNDO' ELSE 'MONTHLY_PAYOFF' END)
   OR j.source_reference<>p.id::text OR j.effective_date<>p.effective_date OR j.request_key<>p.request_key OR j.payload_hash<>p.payload_hash
   OR j.maker_id<>p.actor_id OR j.posted_at<>p.recorded_at OR j.reverses_id IS DISTINCT FROM original.journal_id
   OR NOT EXISTS(SELECT 1 FROM accounting_period WHERE id=j.period_id AND sacco_id=p.sacco_id AND state='OPEN' AND p.effective_date BETWEEN starts_on AND ends_on)
   OR NOT EXISTS(SELECT 1 FROM accounting_outbox WHERE journal_id=j.id AND sacco_id=p.sacco_id AND station_id=p.station_id)
   OR EXISTS(SELECT 1 FROM gl_journal_line line JOIN gl_account account ON account.id=line.account_id WHERE line.journal_id=j.id AND NOT account.active)
   OR p.charges<>(SELECT coalesce(sum(amount),0) FROM loan_charge_event WHERE journal_id=j.id)
   OR EXISTS(SELECT 1 FROM loan_charge_event e WHERE e.journal_id=j.id AND (e.loan_id<>p.loan_id OR e.actor_id<>p.actor_id OR e.effective_date<>p.effective_date OR e.posting_id IS NOT NULL
        OR e.kind<>(CASE WHEN undo THEN 'COLLECT_UNDO' ELSE 'COLLECT' END)))
 THEN RAISE EXCEPTION 'Payoff journal, period, and charge evidence must match'; END IF;
 IF EXISTS (
   WITH expected AS (
     SELECT p.money_account_id account_id,p.cash debit,0::numeric credit WHERE p.cash>0
     UNION ALL SELECT p.interest_account_id,t.interest_returned,0 WHERE t.interest_returned>0
     UNION ALL SELECT p.principal_account_id,0,t.principal_amount WHERE t.principal_amount>0
     UNION ALL SELECT p.interest_account_id,0,t.interest_amount WHERE t.interest_amount>0
     UNION ALL SELECT p.payable_account_id,0,p.refund WHERE p.refund>0
     UNION ALL SELECT term.receivable_account_id,0,e.amount FROM loan_charge_event e JOIN loan_charge_term term ON term.id=e.term_id WHERE e.journal_id=j.id
   ), directed AS (SELECT account_id,CASE WHEN undo THEN credit ELSE debit END debit,CASE WHEN undo THEN debit ELSE credit END credit FROM expected),
   actual AS (SELECT account_id,debit,credit FROM loan_base_journal_line WHERE journal_id=j.id)
   (SELECT * FROM directed EXCEPT ALL SELECT * FROM actual) UNION ALL (SELECT * FROM actual EXCEPT ALL SELECT * FROM directed)
 ) THEN RAISE EXCEPTION 'Exact balanced payoff journal required'; END IF;
END $$;

CREATE FUNCTION payoff_post_committed() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN PERFORM validate_payoff_post(NEW); RETURN NULL; END $$;
CREATE CONSTRAINT TRIGGER payoff_post_commit AFTER INSERT ON loan_payoff_post
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION payoff_post_committed();

CREATE FUNCTION validate_payoff_optional_charge() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.posting_id IS NULL AND NEW.kind IN ('COLLECT','COLLECT_UNDO') AND NOT EXISTS(SELECT 1 FROM loan_payoff_post WHERE journal_id=NEW.journal_id AND loan_id=NEW.loan_id)
 THEN RAISE EXCEPTION 'Payoff charge requires an owning settlement'; END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER payoff_optional_charge AFTER INSERT ON loan_charge_event
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION validate_payoff_optional_charge();

CREATE FUNCTION validate_payoff_journal(j gl_journal) RETURNS void LANGUAGE plpgsql AS $$
DECLARE p loan_payoff_post;
BEGIN
 SELECT * INTO STRICT p FROM loan_payoff_post WHERE journal_id=j.id;
 PERFORM validate_payoff_post(p);
END $$;

DO $$ DECLARE definition text; identity text; source_function regprocedure; BEGIN
 SELECT pg_get_constraintdef(oid) INTO STRICT identity FROM pg_constraint WHERE conrelid='gl_journal'::regclass AND conname='gl_core_identity';
 ALTER TABLE gl_journal DROP CONSTRAINT gl_core_identity;
 EXECUTE 'ALTER TABLE gl_journal ADD CONSTRAINT gl_core_identity CHECK (('||substring(identity from 8 for length(identity)-8)||
    ') OR (direct_post AND policy_id IS NULL AND checker_id IS NULL AND source_type IN (''MONTHLY_PAYOFF'',''MONTHLY_PAYOFF_UNDO'')))';
 SELECT pg_get_functiondef('gl_validate_post()'::regprocedure) INTO definition;
 IF strpos(definition,'IF NEW.state<>''POSTED'' THEN RETURN NEW; END IF;')=0 THEN RAISE EXCEPTION 'Missing GL validation entry'; END IF;
 EXECUTE replace(definition,'IF NEW.state<>''POSTED'' THEN RETURN NEW; END IF;',
   'IF NEW.state<>''POSTED'' THEN RETURN NEW; END IF; IF NEW.source_type IN (''MONTHLY_PAYOFF'',''MONTHLY_PAYOFF_UNDO'') THEN PERFORM validate_payoff_journal(NEW); RETURN NEW; END IF;');
 SELECT pg_get_functiondef('core_post_commit_guard()'::regprocedure) INTO definition;
 IF strpos(definition,'IF NOT EXISTS(SELECT 1 FROM gl_journal j JOIN accounting_voucher')=0 THEN RAISE EXCEPTION 'Missing core posting guard'; END IF;
 EXECUTE replace(definition,'IF NOT EXISTS(SELECT 1 FROM gl_journal j JOIN accounting_voucher',
   'IF EXISTS(SELECT 1 FROM gl_journal j WHERE j.id=journal AND j.state=''POSTED'' AND j.source_type IN (''MONTHLY_PAYOFF'',''MONTHLY_PAYOFF_UNDO'')) THEN PERFORM validate_payoff_journal((SELECT j FROM gl_journal j WHERE j.id=journal)); RETURN NULL; END IF; IF NOT EXISTS(SELECT 1 FROM gl_journal j JOIN accounting_voucher');
 SELECT pg_get_functiondef('accounting_system_voucher_header()'::regprocedure) INTO definition;
 IF strpos(definition,'WHEN j.source_type=''LOAN_ADJUSTMENT'' THEN')=0 THEN RAISE EXCEPTION 'Missing system voucher classification'; END IF;
 EXECUTE replace(definition,'WHEN j.source_type=''LOAN_ADJUSTMENT'' THEN',
   'WHEN j.source_type IN (''MONTHLY_PAYOFF'',''MONTHLY_PAYOFF_UNDO'') THEN
      SELECT x.money_account_id INTO NEW.money_account_id FROM loan_payoff_post x WHERE x.journal_id=j.id;
      NEW.type:=CASE WHEN NEW.money_account_id IS NULL THEN ''JOURNAL'' ELSE ''RECEIPT'' END;
    WHEN j.source_type=''LOAN_ADJUSTMENT'' THEN');
 SELECT pg_get_functiondef('validate_interest_exclusion_transaction()'::regprocedure) INTO definition;
 IF strpos(definition,'IF NEW.future_interest_excluded')=0 THEN RAISE EXCEPTION 'Missing allocation validation'; END IF;
 EXECUTE replace(definition,'IF NEW.future_interest_excluded',
   'IF NEW.kind IN (''PAYOFF'',''PAYOFF_UNDO'') THEN RETURN NULL; END IF; IF NEW.future_interest_excluded');
 SELECT pg_get_functiondef('validate_interest_exclusion_projection()'::regprocedure) INTO definition;
 EXECUTE replace(definition,'''PAYMENT'',''SETTLEMENT''','''PAYMENT'',''SETTLEMENT'',''PAYOFF''');
 SELECT pg_get_functiondef('loan_interest_net_of_waivers(uuid,date,bigint)'::regprocedure) INTO definition;
 definition:=replace(definition,'''PAYMENT'',''SETTLEMENT''','''PAYMENT'',''SETTLEMENT'',''PAYOFF''');
 definition:=regexp_replace(definition,$pattern$JOIN loan_monthly_interest_terms term ON term.loan_id=i.loan_application_id\s+AND term.first_installment_number<=i.installment_number AND term.effective_date<=as_of\s+LEFT JOIN$pattern$,'LEFT JOIN');
 EXECUTE replace(definition,'WHERE i.id=installment;',
   'WHERE i.id=installment AND EXISTS (SELECT 1 FROM loan_monthly_interest_terms term WHERE term.loan_id=i.loan_application_id
      AND term.first_installment_number<=i.installment_number AND term.effective_date<=as_of);');
 SELECT pg_get_functiondef('loan_interest_due(uuid,date)'::regprocedure) INTO definition;
 definition:=replace(replace(replace(definition,'''PAYMENT'',''SETTLEMENT''','''PAYMENT'',''SETTLEMENT'',''PAYOFF'''),
    'THEN a.interest_amount ELSE','THEN a.interest_amount-a.interest_returned ELSE'), 'ELSE -a.interest_amount END','ELSE -a.interest_amount+a.interest_returned END');
 definition:=replace(definition,'term.loan_id IS NULL','NOT EXISTS (SELECT 1 FROM loan_monthly_interest_terms term WHERE term.loan_id=i.loan_application_id)');
 EXECUTE replace(definition,'LEFT JOIN loan_monthly_interest_terms term ON term.loan_id=i.loan_application_id','');
 SELECT pg_get_functiondef('loan_charge_commit()'::regprocedure) INTO definition;
 IF strpos(definition,'ELSE RAISE EXCEPTION ''Charge event requires a specialized loan posting''')=0 THEN RAISE EXCEPTION 'Missing charge posting guard'; END IF;
 EXECUTE replace(definition,'ELSE RAISE EXCEPTION ''Charge event requires a specialized loan posting''',
   'ELSIF j.source_type IN (''MONTHLY_PAYOFF'',''MONTHLY_PAYOFF_UNDO'') THEN PERFORM validate_payoff_journal(j); ELSE RAISE EXCEPTION ''Charge event requires a specialized loan posting''');
 SELECT pg_get_functiondef('loan_fee_recovery_guard()'::regprocedure) INTO definition;
 IF strpos(definition,'IF j.source_type NOT IN (''LOAN_RECORD_PAYMENT'',''LOAN_CHARGE_RECEIPT'')')=0 THEN RAISE EXCEPTION 'Missing fee recovery guard'; END IF;
 definition:=regexp_replace(definition,$pattern$IF j.source_type NOT IN \('LOAN_RECORD_PAYMENT','LOAN_CHARGE_RECEIPT'\).*?>p.principal_amount$pattern$,$patch$
 IF (CASE WHEN j.source_type='MONTHLY_PAYOFF' THEN NOT EXISTS (
    SELECT 1 FROM loan_payoff_post pf JOIN loan_repayment_transactions tx ON tx.id=pf.transaction_id
    WHERE pf.journal_id=j.id AND pf.loan_id=t.loan_id
      AND tx.principal_amount >= (SELECT sum(e.amount) FROM loan_fee_recovery_event e WHERE e.journal_id=j.id)
 ) ELSE j.source_type NOT IN ('LOAN_RECORD_PAYMENT','LOAN_CHARGE_RECEIPT') OR p.kind IS DISTINCT FROM 'PAYMENT'
     OR p.loan_id<>t.loan_id OR (SELECT sum(e.amount) FROM loan_fee_recovery_event e WHERE e.journal_id=j.id)>p.principal_amount END)$patch$,'s');
 EXECUTE replace(replace(definition,'''LOAN_RECORD_REVERSAL'',''LOAN_CHARGE_REVERSAL''','''LOAN_RECORD_REVERSAL'',''LOAN_CHARGE_REVERSAL'',''MONTHLY_PAYOFF_UNDO'''),
   'OR p.kind IS DISTINCT FROM ''REVERSAL'' OR p.loan_id<>t.loan_id))',
   'OR (CASE WHEN j.source_type=''MONTHLY_PAYOFF_UNDO'' THEN NOT EXISTS(SELECT 1 FROM loan_payoff_post pf WHERE pf.journal_id=j.id AND pf.loan_id=t.loan_id)
         ELSE p.kind IS DISTINCT FROM ''REVERSAL'' OR p.loan_id<>t.loan_id END)))');
 FOREACH source_function IN ARRAY ARRAY['accountant_loan_commit()'::regprocedure,'loan_topup_validate(gl_journal)'::regprocedure,
      'loan_charge_validate(gl_journal)'::regprocedure] LOOP
   definition:=pg_get_functiondef(source_function);
   definition:=replace(replace(definition,'CASE WHEN kind=''PAYMENT'' THEN','CASE WHEN kind IN(''PAYMENT'',''PAYOFF'') THEN'),
       'kind IN(''PAYMENT'',''SETTLEMENT'') THEN','kind IN(''PAYMENT'',''SETTLEMENT'',''PAYOFF'') THEN');
   definition:=replace(replace(definition,'THEN interest_amount ELSE -interest_amount END',
       'THEN interest_amount-interest_returned ELSE -interest_amount+interest_returned END'),
       'THEN ma.interest_amount ELSE -ma.interest_amount END',
       'THEN ma.interest_amount-ma.interest_returned ELSE -ma.interest_amount+ma.interest_returned END');
   EXECUTE definition;
 END LOOP;
END $$;

CREATE CONSTRAINT TRIGGER monthly_payoff_voucher_on_post AFTER INSERT OR UPDATE OF state ON gl_journal
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW
 WHEN(NEW.state='POSTED' AND NEW.source_type IN ('MONTHLY_PAYOFF','MONTHLY_PAYOFF_UNDO'))
 EXECUTE FUNCTION accounting_issue_system_voucher();

CREATE FUNCTION validate_payoff_refund() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE p loan_payoff_post; v accounting_voucher;
BEGIN
 SELECT * INTO STRICT p FROM loan_payoff_post WHERE id=NEW.payoff_id FOR UPDATE;
 SELECT * INTO STRICT v FROM accounting_voucher WHERE id=NEW.voucher_id;
 IF p.reverses_id IS NOT NULL OR p.refund<=0 OR v.type<>'PAYMENT' OR v.reverses_id IS NOT NULL OR v.total<>p.refund
   OR v.sacco_id<>p.sacco_id OR v.station_id<>p.station_id OR v.effective_date<p.effective_date
   OR NOT EXISTS(SELECT 1 FROM loan_applications a JOIN loan_borrower_directory b ON b.party_id=a.borrower_party_id AND b.sacco_id=p.sacco_id
      WHERE a.id=p.loan_id AND b.full_name=v.party)
   OR EXISTS(SELECT 1 FROM loan_payoff_post correction WHERE correction.reverses_id=p.id)
   OR EXISTS(SELECT 1 FROM loan_payoff_refund r WHERE r.payoff_id=p.id AND r.voucher_id<>NEW.voucher_id
       AND NOT EXISTS(SELECT 1 FROM accounting_voucher undo WHERE undo.reverses_id=r.voucher_id))
   OR (SELECT coalesce(sum(debit),0) FROM gl_journal_line WHERE journal_id=v.journal_id AND account_id=p.payable_account_id)<>p.refund
   OR NOT EXISTS(SELECT 1 FROM gl_journal j WHERE j.id=v.journal_id AND j.state='POSTED')
 THEN RAISE EXCEPTION 'Refund voucher must settle the exact scoped payoff payable'; END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER payoff_refund_commit AFTER INSERT ON loan_payoff_refund
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION validate_payoff_refund();

DO $$ DECLARE columns text; BEGIN
 SELECT string_agg(format('movement.%I',attname),',' ORDER BY attnum) INTO columns
 FROM pg_attribute WHERE attrelid='accountant_loan_movement'::regclass AND attnum>0 AND NOT attisdropped;
 ALTER VIEW accountant_loan_movement RENAME TO accountant_loan_movement_before_payoff;
 EXECUTE 'CREATE VIEW accountant_loan_movement AS SELECT p.* FROM accountant_loan_movement_before_payoff p UNION ALL SELECT '||columns||$view$
 FROM loan_payoff_post p JOIN loan_repayment_transactions tx ON tx.id=p.transaction_id
 LEFT JOIN gl_journal j ON j.id=p.journal_id
 LEFT JOIN gl_account cash ON cash.id=p.money_account_id
 JOIN gl_account principal ON principal.id=p.principal_account_id
 JOIN gl_account interest ON interest.id=p.interest_account_id
 CROSS JOIN LATERAL jsonb_populate_record(NULL::accountant_loan_post,jsonb_build_object(
 'id',p.id,'loan_id',p.loan_id,'sacco_id',p.sacco_id,'station_id',p.station_id,'kind',tx.kind,'state','POSTED',
 'request_key',p.request_key,'payload_hash',p.payload_hash,'effective_date',p.effective_date,'reference',tx.receipt_reference,
 'amount',p.cash,'principal_amount',tx.principal_amount,'interest_amount',tx.interest_amount-tx.interest_returned,
 'principal_balance',CASE WHEN p.reverses_id IS NULL THEN 0 ELSE tx.principal_amount END,
 'money_account_id',p.money_account_id,'principal_account_id',p.principal_account_id,'interest_account_id',p.interest_account_id,
 'money_account_name',coalesce(cash.name,''),'principal_account_name',principal.name,'interest_account_name',interest.name,
 'evidence',coalesce(j.evidence_reference,''),'notes',coalesce(j.reason,''),'actor_id',p.actor_id,'recorded_at',p.recorded_at,
 'journal_id',p.journal_id,'transaction_id',p.transaction_id,'reverses_id',p.reverses_id)) movement$view$;
END $$;
