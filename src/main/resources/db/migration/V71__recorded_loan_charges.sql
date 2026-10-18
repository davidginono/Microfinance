-- No fee, account, permission or policy approval is seeded by this migration.
SET check_function_bodies=on;
CREATE TABLE loan_charge_policy (
 id uuid PRIMARY KEY,sacco_id varchar(255) NOT NULL REFERENCES registered_saccos(sacco_id),product_id uuid NOT NULL REFERENCES loan_product_settings(id),
 effective_from date NOT NULL,enabled boolean NOT NULL,management_timing varchar(16) NOT NULL CHECK(management_timing IN('SEPARATE','DEDUCT')),
 insurance_timing varchar(16) NOT NULL CHECK(insurance_timing IN('SEPARATE','DEDUCT')),receivable_account_id uuid,management_account_id uuid,insurance_account_id uuid,
 policy_reference varchar(500) NOT NULL,agreement_reference varchar(500) NOT NULL,insurer varchar(500) NOT NULL,insurer_verification varchar(500) NOT NULL,
 allow_custom boolean NOT NULL,custom_management_limit numeric(18,2),request_key uuid NOT NULL,payload_hash varchar(64) NOT NULL,
 actor_id uuid NOT NULL REFERENCES members(id),recorded_at timestamptz NOT NULL,
 UNIQUE(sacco_id,product_id,effective_from),UNIQUE(sacco_id,request_key),UNIQUE(id,sacco_id),
 FOREIGN KEY(receivable_account_id,sacco_id) REFERENCES gl_account(id,sacco_id),FOREIGN KEY(management_account_id,sacco_id) REFERENCES gl_account(id,sacco_id),
 FOREIGN KEY(insurance_account_id,sacco_id) REFERENCES gl_account(id,sacco_id),
 CHECK(NOT enabled OR (receivable_account_id IS NOT NULL AND length(trim(policy_reference))>0 AND length(trim(agreement_reference))>0)),
 CHECK(NOT allow_custom OR (custom_management_limit IS NOT NULL AND custom_management_limit>0)),CHECK(insurance_account_id IS NULL OR (length(trim(insurer))>0 AND length(trim(insurer_verification))>0))
);
CREATE TRIGGER loan_charge_policy_immutable BEFORE UPDATE OR DELETE ON loan_charge_policy FOR EACH ROW EXECUTE FUNCTION gl_protect_evidence();
CREATE TABLE loan_charge_contract (
 loan_id uuid PRIMARY KEY,sacco_id varchar(255) NOT NULL,station_id varchar(255) NOT NULL,policy_id uuid,
 policy_reference varchar(500) NOT NULL,agreement_reference varchar(500) NOT NULL,recorded_at timestamptz NOT NULL,
 UNIQUE(loan_id,sacco_id,station_id),FOREIGN KEY(loan_id,sacco_id,station_id) REFERENCES accountant_loan_record(loan_id,sacco_id,station_id),
 FOREIGN KEY(policy_id,sacco_id) REFERENCES loan_charge_policy(id,sacco_id)
);
CREATE TRIGGER loan_charge_contract_immutable BEFORE UPDATE OR DELETE ON loan_charge_contract FOR EACH ROW EXECUTE FUNCTION gl_protect_evidence();
CREATE TABLE loan_charge_term (
 id uuid PRIMARY KEY,loan_id uuid NOT NULL,sacco_id varchar(255) NOT NULL,station_id varchar(255) NOT NULL,
 kind varchar(16) NOT NULL CHECK(kind IN('APPLICATION','PROCESSING','INSURANCE')),basis varchar(24) NOT NULL CHECK(basis IN('FIXED','PRINCIPAL_RATE','CUSTOM_AMOUNT')),
 basis_value numeric(20,4) NOT NULL CHECK(basis_value>=0),amount numeric(18,2) NOT NULL CHECK(amount>0),timing varchar(16) NOT NULL CHECK(timing IN('SEPARATE','DEDUCT')),
 receivable_account_id uuid NOT NULL,liability_account_id uuid NOT NULL,evidence varchar(500) NOT NULL,
 receivable_account_name varchar(500) NOT NULL,liability_account_name varchar(500) NOT NULL,
 provider varchar(500) NOT NULL,verification varchar(500) NOT NULL,
 UNIQUE(loan_id,kind),UNIQUE(id,loan_id,sacco_id,station_id),
 FOREIGN KEY(loan_id,sacco_id,station_id) REFERENCES loan_charge_contract(loan_id,sacco_id,station_id),
 FOREIGN KEY(receivable_account_id,sacco_id) REFERENCES gl_account(id,sacco_id),FOREIGN KEY(liability_account_id,sacco_id) REFERENCES gl_account(id,sacco_id)
);
CREATE TRIGGER loan_charge_term_immutable BEFORE UPDATE OR DELETE ON loan_charge_term FOR EACH ROW EXECUTE FUNCTION gl_protect_evidence();
CREATE TABLE loan_charge_command (
 id uuid PRIMARY KEY,loan_id uuid NOT NULL,sacco_id varchar(255) NOT NULL,station_id varchar(255) NOT NULL,request_key uuid NOT NULL,payload_hash varchar(64) NOT NULL,
 effective_date date NOT NULL,kind varchar(16) NOT NULL CHECK(kind IN('ASSESS','ASSESS_UNDO')),journal_id uuid NOT NULL UNIQUE REFERENCES gl_journal(id) DEFERRABLE INITIALLY DEFERRED,
 reverses_id uuid UNIQUE REFERENCES loan_charge_command(id),actor_id uuid NOT NULL REFERENCES members(id),evidence varchar(500) NOT NULL CHECK(length(trim(evidence))>0),reason varchar(500) NOT NULL,
 recorded_at timestamptz NOT NULL,UNIQUE(sacco_id,station_id,request_key),FOREIGN KEY(loan_id,sacco_id,station_id) REFERENCES loan_charge_contract(loan_id,sacco_id,station_id),
 CHECK((kind='ASSESS' AND reverses_id IS NULL) OR (kind='ASSESS_UNDO' AND reverses_id IS NOT NULL AND length(trim(reason))>0))
);
CREATE TRIGGER loan_charge_command_immutable BEFORE UPDATE OR DELETE ON loan_charge_command FOR EACH ROW EXECUTE FUNCTION gl_protect_evidence();
CREATE TABLE loan_charge_event (
 id uuid PRIMARY KEY,loan_id uuid NOT NULL,term_id uuid NOT NULL,sacco_id varchar(255) NOT NULL,station_id varchar(255) NOT NULL,sequence bigint NOT NULL CHECK(sequence>0),
 kind varchar(16) NOT NULL CHECK(kind IN('ASSESS','ASSESS_UNDO','COLLECT','COLLECT_UNDO','DEDUCT','DEDUCT_UNDO','SETTLE','SETTLE_UNDO')),
 amount numeric(18,2) NOT NULL CHECK(amount>0),effective_date date NOT NULL,journal_id uuid NOT NULL REFERENCES gl_journal(id),posting_id uuid REFERENCES accountant_loan_post(id),
 command_id uuid REFERENCES loan_charge_command(id),reverses_id uuid UNIQUE REFERENCES loan_charge_event(id),actor_id uuid NOT NULL REFERENCES members(id),
 evidence varchar(500) NOT NULL,reason varchar(500) NOT NULL,recorded_at timestamptz NOT NULL,UNIQUE(loan_id,sequence),
 FOREIGN KEY(term_id,loan_id,sacco_id,station_id) REFERENCES loan_charge_term(id,loan_id,sacco_id,station_id),
 CHECK((kind IN('ASSESS','ASSESS_UNDO') AND command_id IS NOT NULL AND posting_id IS NULL) OR (kind NOT IN('ASSESS','ASSESS_UNDO') AND command_id IS NULL AND posting_id IS NOT NULL)),
 CHECK((kind LIKE '%_UNDO' AND reverses_id IS NOT NULL AND length(trim(reason))>0) OR (kind NOT LIKE '%_UNDO' AND reverses_id IS NULL))
);
CREATE INDEX ix_loan_charge_event_term ON loan_charge_event(term_id,sequence);
CREATE INDEX ix_loan_charge_event_journal ON loan_charge_event(journal_id);
CREATE INDEX ix_loan_charge_event_posting ON loan_charge_event(posting_id);
CREATE TRIGGER loan_charge_event_immutable BEFORE UPDATE OR DELETE ON loan_charge_event FOR EACH ROW EXECUTE FUNCTION gl_protect_evidence();
CREATE VIEW loan_charge_balance AS
 SELECT c.loan_id,c.sacco_id,c.station_id,coalesce(max(e.sequence),0) sequence,max(e.effective_date) last_date,
 coalesce(sum(CASE WHEN e.kind='ASSESS' THEN e.amount WHEN e.kind='ASSESS_UNDO' THEN -e.amount ELSE 0 END),0) assessed,
 coalesce(sum(CASE WHEN e.kind IN('COLLECT','DEDUCT','SETTLE') THEN e.amount WHEN e.kind IN('COLLECT_UNDO','DEDUCT_UNDO','SETTLE_UNDO') THEN -e.amount ELSE 0 END),0) collected,
 coalesce(sum(CASE WHEN e.kind IN('ASSESS','COLLECT_UNDO','DEDUCT_UNDO','SETTLE_UNDO') THEN e.amount ELSE -e.amount END),0) outstanding
 FROM loan_charge_contract c LEFT JOIN loan_charge_event e ON e.loan_id=c.loan_id GROUP BY c.loan_id,c.sacco_id,c.station_id;
-- Hashes bound index width for long multilingual references; uniqueness is conservative on collisions.
CREATE UNIQUE INDEX ux_insurance_premium_evidence ON loan_charge_term(sacco_id,md5(lower(btrim(provider))),md5(lower(btrim(evidence)))) WHERE kind='INSURANCE';
ALTER TABLE accountant_loan_post ADD COLUMN charge_amount numeric(18,2) NOT NULL DEFAULT 0 CHECK(charge_amount>=0);
ALTER TABLE accountant_loan_post DROP CONSTRAINT accountant_loan_post_check1;
ALTER TABLE accountant_loan_post ADD CONSTRAINT accountant_loan_post_component_total CHECK(state='POSTING' OR
 (principal_amount>=0 AND interest_amount>=0 AND principal_balance>=0 AND amount=principal_amount+interest_amount+charge_amount AND
 ((kind='DISBURSEMENT' AND transaction_id IS NULL AND interest_amount=0 AND charge_amount=0) OR
 (kind<>'DISBURSEMENT' AND ((principal_amount+interest_amount>0 AND transaction_id IS NOT NULL) OR (principal_amount+interest_amount=0 AND transaction_id IS NULL AND charge_amount>0))))));
DO $$ DECLARE definition text; BEGIN
 SELECT pg_get_functiondef('accountant_loan_post_protect()'::regprocedure) INTO definition;
 definition:=replace(definition,'''principal_balance'']','''principal_balance'',''charge_amount'']'); EXECUTE definition;
END $$;
ALTER TABLE accountant_loan_post ADD COLUMN event_sequence bigint GENERATED ALWAYS AS IDENTITY;
CREATE INDEX ix_recorded_loan_cash_sequence ON accountant_loan_post(loan_id,event_sequence);
ALTER TABLE accountant_loan_topup ADD COLUMN settled_charges numeric(18,2) NOT NULL DEFAULT 0 CHECK(settled_charges>=0);
ALTER TABLE accountant_loan_topup ADD COLUMN deducted_charges numeric(18,2) NOT NULL DEFAULT 0 CHECK(deducted_charges>=0);

CREATE FUNCTION loan_charge_terms_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE a loan_applications; p loan_charge_policy;
BEGIN
 IF TG_TABLE_NAME='loan_applications' THEN
  IF EXISTS(SELECT 1 FROM loan_charge_contract WHERE loan_id=OLD.id) AND
   (NEW.financial_snapshot->'recordedCharges' IS DISTINCT FROM OLD.financial_snapshot->'recordedCharges' OR NEW.financial_snapshot->'recordedChargeTotal' IS DISTINCT FROM OLD.financial_snapshot->'recordedChargeTotal' OR NEW.financial_snapshot->'recordedChargeDeductions' IS DISTINCT FROM OLD.financial_snapshot->'recordedChargeDeductions' OR NEW.financial_snapshot->'recordedNetCashEstimate' IS DISTINCT FROM OLD.financial_snapshot->'recordedNetCashEstimate' OR NEW.financial_snapshot->'recordedTotalPayable' IS DISTINCT FROM OLD.financial_snapshot->'recordedTotalPayable')
  THEN RAISE EXCEPTION 'Frozen loan charge terms cannot change'; END IF; RETURN NEW;
 END IF;
 IF TG_TABLE_NAME='loan_charge_policy' THEN
  IF NOT EXISTS(SELECT 1 FROM loan_product_settings WHERE id=NEW.product_id AND sacco_id=NEW.sacco_id)
  THEN RAISE EXCEPTION 'Charge policy product scope required'; END IF; RETURN NEW;
 END IF;
 SELECT * INTO a FROM loan_applications WHERE id=NEW.loan_id;
 IF TG_TABLE_NAME='loan_charge_contract' THEN
  IF a.financial_snapshot->'recordedCharges' IS NULL
   OR NEW.policy_id::text IS DISTINCT FROM a.financial_snapshot->'recordedCharges'->>'policyId'
   OR NEW.policy_reference IS DISTINCT FROM a.financial_snapshot->'recordedCharges'->>'policyReference'
   OR NEW.agreement_reference IS DISTINCT FROM a.financial_snapshot->'recordedCharges'->>'agreementReference'
  THEN RAISE EXCEPTION 'Server-frozen charge snapshot and policy evidence required'; END IF; RETURN NEW;
 END IF;
 SELECT p1.* INTO p FROM loan_charge_policy p1 JOIN loan_charge_contract c ON c.policy_id=p1.id WHERE c.loan_id=NEW.loan_id;
 IF p.id IS NULL OR NOT p.enabled OR p.sacco_id<>NEW.sacco_id OR a.loan_product_setting_id<>p.product_id
 OR NOT EXISTS(SELECT 1 FROM jsonb_array_elements(a.financial_snapshot->'recordedCharges'->'terms') t
   WHERE t->>'id'=NEW.id::text AND t->>'kind'=NEW.kind AND (t->>'amount')::numeric=NEW.amount AND t->>'timing'=NEW.timing AND t->>'basis'=NEW.basis AND coalesce(t->>'evidence','')=NEW.evidence AND coalesce(t->>'provider','')=NEW.provider AND coalesce(t->>'verification','')=NEW.verification
   AND (t->>'basisValue')::numeric=NEW.basis_value AND t->>'receivableAccountId'=NEW.receivable_account_id::text AND t->>'liabilityAccountId'=NEW.liability_account_id::text)
 OR (a.top_up_source_loan_id IS NOT NULL AND NEW.kind IN('APPLICATION','PROCESSING'))
 OR NOT EXISTS(SELECT 1 FROM gl_account WHERE id=NEW.receivable_account_id AND sacco_id=NEW.sacco_id AND active AND kind='CONTROL' AND type='ASSET' AND purpose='FEE_RECEIVABLE')
 OR NOT EXISTS(SELECT 1 FROM gl_account WHERE id=NEW.liability_account_id AND sacco_id=NEW.sacco_id AND active AND kind='CONTROL' AND type='LIABILITY'
   AND ((NEW.kind='INSURANCE' AND purpose='PAYABLE' AND id=p.insurance_account_id AND length(trim(NEW.evidence))>0 AND length(trim(p.insurer_verification))>0)
    OR (NEW.kind IN('APPLICATION','PROCESSING') AND purpose='CLEARING' AND id=p.management_account_id)))
 OR (NEW.receivable_account_id<>p.receivable_account_id)
 OR (NEW.timing<>CASE WHEN NEW.kind='INSURANCE' THEN p.insurance_timing ELSE p.management_timing END)
 OR (NEW.kind='INSURANCE' AND (NEW.provider<>p.insurer OR NEW.verification<>p.insurer_verification OR NEW.amount IS DISTINCT FROM (a.form_data->>'insurancePremium')::numeric))
 OR (NEW.basis='CUSTOM_AMOUNT' AND (NOT p.allow_custom OR length(trim(NEW.evidence))=0 OR
   (SELECT sum((term->>'amount')::numeric) FROM jsonb_array_elements(a.financial_snapshot->'recordedCharges'->'terms') term WHERE term->>'kind' IN('APPLICATION','PROCESSING'))>p.custom_management_limit))
 THEN RAISE EXCEPTION 'Scoped frozen supported charge policy, accounts and evidence required'; END IF; RETURN NEW;
END $$;
CREATE TRIGGER loan_charge_policy_scope BEFORE INSERT ON loan_charge_policy FOR EACH ROW EXECUTE FUNCTION loan_charge_terms_guard();
CREATE TRIGGER loan_charge_contract_scope BEFORE INSERT ON loan_charge_contract FOR EACH ROW EXECUTE FUNCTION loan_charge_terms_guard();
CREATE TRIGGER loan_charge_terms_scope BEFORE INSERT ON loan_charge_term FOR EACH ROW EXECUTE FUNCTION loan_charge_terms_guard();
CREATE TRIGGER loan_charge_contract_protect BEFORE UPDATE ON loan_applications FOR EACH ROW EXECUTE FUNCTION loan_charge_terms_guard();

CREATE FUNCTION loan_charge_event_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE t loan_charge_term; original loan_charge_event; balance numeric; assessed numeric; last_date date; seq bigint;
BEGIN
 PERFORM 1 FROM accountant_loan_record WHERE loan_id=NEW.loan_id AND sacco_id=NEW.sacco_id AND station_id=NEW.station_id FOR UPDATE;
 SELECT * INTO t FROM loan_charge_term WHERE id=NEW.term_id;
 SELECT max(effective_date),coalesce(max(sequence),0) INTO last_date,seq FROM loan_charge_event WHERE loan_id=NEW.loan_id;
 SELECT coalesce(sum(CASE WHEN kind IN('ASSESS','COLLECT_UNDO','DEDUCT_UNDO','SETTLE_UNDO') THEN amount ELSE -amount END),0),
 coalesce(sum(CASE WHEN kind='ASSESS' THEN amount WHEN kind='ASSESS_UNDO' THEN -amount ELSE 0 END),0) INTO balance,assessed FROM loan_charge_event WHERE term_id=t.id;
 IF NEW.sequence<>seq+1 OR (last_date IS NOT NULL AND NEW.effective_date<last_date) OR NEW.effective_date>CURRENT_DATE
 OR NOT EXISTS(SELECT 1 FROM members WHERE id=NEW.actor_id AND sacco_id=NEW.sacco_id AND station_id=NEW.station_id AND status='ACTIVE')
 THEN RAISE EXCEPTION 'Ordered scoped dated charge event required'; END IF;
 IF NEW.kind='ASSESS' AND (assessed<>0 OR NEW.amount<>t.amount) THEN RAISE EXCEPTION 'Charge may only be assessed once'; END IF;
 IF NEW.kind IN('COLLECT','DEDUCT','SETTLE','ASSESS_UNDO') AND NEW.amount>balance THEN RAISE EXCEPTION 'Charge overcollection or dependent correction'; END IF;
 IF NEW.kind='DEDUCT' AND t.timing<>'DEDUCT' THEN RAISE EXCEPTION 'Only agreed deductions may reduce cash'; END IF;
 IF NEW.reverses_id IS NOT NULL THEN
  SELECT * INTO original FROM loan_charge_event WHERE id=NEW.reverses_id;
  IF original.id IS NULL OR original.kind LIKE '%_UNDO' OR NEW.kind<>original.kind||'_UNDO' OR original.actor_id=NEW.actor_id
   OR ROW(original.loan_id,original.term_id,original.amount) IS DISTINCT FROM ROW(NEW.loan_id,NEW.term_id,NEW.amount)
   OR NEW.effective_date<original.effective_date THEN RAISE EXCEPTION 'Exact independent linked charge correction required'; END IF;
 END IF; RETURN NEW;
END $$;
CREATE TRIGGER loan_charge_event_scope BEFORE INSERT ON loan_charge_event FOR EACH ROW EXECUTE FUNCTION loan_charge_event_guard();

CREATE FUNCTION loan_charge_validate(j gl_journal) RETURNS void LANGUAGE plpgsql AS $$
DECLARE c loan_charge_command; p accountant_loan_post; original accountant_loan_post; t loan_repayment_transactions; l loan_ledgers; a loan_applications;
 period accounting_period; n integer; difference numeric; fees numeric; expected integer; original_command loan_charge_command;
BEGIN
 SELECT * INTO period FROM accounting_period WHERE id=j.period_id FOR SHARE;
 IF NOT j.direct_post OR j.policy_id IS NOT NULL OR j.checker_id IS NOT NULL OR j.currency<>'TZS' OR period.id IS NULL OR period.sacco_id<>j.sacco_id OR period.state<>'OPEN'
 OR j.effective_date NOT BETWEEN period.starts_on AND period.ends_on OR j.effective_date>CURRENT_DATE
 OR NOT EXISTS(SELECT 1 FROM accounting_outbox WHERE journal_id=j.id AND sacco_id=j.sacco_id AND station_id=j.station_id)
 OR EXISTS(SELECT 1 FROM gl_journal_line z JOIN gl_account g ON g.id=z.account_id WHERE z.journal_id=j.id AND (NOT g.active OR g.sacco_id<>j.sacco_id OR g.kind='HEADING'))
 THEN RAISE EXCEPTION 'Scoped open charge posting period, accounts and outbox required'; END IF;
 SELECT count(*),sum(debit-credit) INTO n,difference FROM gl_journal_line WHERE journal_id=j.id;
 IF n NOT BETWEEN 2 AND 10 OR difference<>0 THEN RAISE EXCEPTION 'Balanced exact charge journal required'; END IF;
 IF j.source_type IN('LOAN_CHARGE_ASSESS','LOAN_CHARGE_UNDO') THEN
  SELECT * INTO c FROM loan_charge_command WHERE journal_id=j.id;
  IF c.id IS NULL OR c.id::text<>j.source_reference OR c.sacco_id<>j.sacco_id OR c.station_id<>j.station_id OR c.actor_id<>j.maker_id OR c.effective_date<>j.effective_date
   OR j.source_type<>(CASE WHEN c.kind='ASSESS' THEN 'LOAN_CHARGE_ASSESS' ELSE 'LOAN_CHARGE_UNDO' END)
   OR c.request_key<>j.request_key OR c.payload_hash<>j.payload_hash OR c.recorded_at<>j.posted_at
   OR n<>2*(SELECT count(*) FROM loan_charge_event WHERE command_id=c.id)
   OR n<>2*(SELECT count(*) FROM loan_charge_term WHERE loan_id=c.loan_id)
   OR EXISTS(SELECT 1 FROM loan_charge_event e WHERE e.command_id=c.id AND (e.loan_id<>c.loan_id OR e.kind<>c.kind OR e.journal_id<>j.id OR e.actor_id<>j.maker_id OR e.effective_date<>j.effective_date))
   OR NOT EXISTS(SELECT 1 FROM loan_applications WHERE id=c.loan_id AND status IN('READY_FOR_DISBURSEMENT','DISBURSED','DISBURSEMENT_REVERSED'))
   OR EXISTS((SELECT account_id,debit,credit FROM gl_journal_line WHERE journal_id=j.id) EXCEPT ALL
     (SELECT t1.receivable_account_id,CASE WHEN e.kind='ASSESS' THEN e.amount ELSE 0 END,CASE WHEN e.kind='ASSESS' THEN 0 ELSE e.amount END FROM loan_charge_event e JOIN loan_charge_term t1 ON t1.id=e.term_id WHERE command_id=c.id
      UNION ALL SELECT t1.liability_account_id,CASE WHEN e.kind='ASSESS' THEN 0 ELSE e.amount END,CASE WHEN e.kind='ASSESS' THEN e.amount ELSE 0 END FROM loan_charge_event e JOIN loan_charge_term t1 ON t1.id=e.term_id WHERE command_id=c.id))
  THEN RAISE EXCEPTION 'Charge assessment and frozen terms must match the journal'; END IF;
  IF c.kind='ASSESS_UNDO' THEN
   SELECT * INTO original_command FROM loan_charge_command WHERE id=c.reverses_id;
   IF original_command.actor_id=c.actor_id OR original_command.journal_id IS DISTINCT FROM j.reverses_id
   OR EXISTS(SELECT 1 FROM loan_charge_event e WHERE command_id=original_command.id AND NOT EXISTS(SELECT 1 FROM loan_charge_event r WHERE r.command_id=c.id AND r.reverses_id=e.id))
   THEN RAISE EXCEPTION 'Complete independent assessment correction required'; END IF;
  ELSIF j.reverses_id IS NOT NULL THEN RAISE EXCEPTION 'Unexpected charge reversal'; END IF;
  RETURN;
 END IF;
 SELECT * INTO p FROM accountant_loan_post WHERE journal_id=j.id;SELECT * INTO a FROM loan_applications WHERE id=p.loan_id;SELECT * INTO l FROM loan_ledgers WHERE loan_application_id=p.loan_id;
 IF p.id IS NULL OR p.state<>'POSTED' OR p.sacco_id<>j.sacco_id OR p.station_id<>j.station_id OR p.id::text<>j.source_reference OR p.actor_id<>j.maker_id
 OR p.request_key<>j.request_key OR p.payload_hash<>j.payload_hash OR p.effective_date<>j.effective_date OR p.recorded_at<>j.posted_at
 OR l.loan_application_id IS NULL OR l.sacco_id<>p.sacco_id OR l.station_id<>p.station_id OR a.amount<>l.principal
 OR NOT EXISTS(SELECT 1 FROM gl_account WHERE id=p.money_account_id AND sacco_id=p.sacco_id AND type='ASSET' AND kind='POSTING' AND purpose IN('CASH','BANK','MOBILE_MONEY'))
 OR NOT EXISTS(SELECT 1 FROM gl_account WHERE id=p.principal_account_id AND sacco_id=p.sacco_id AND type='ASSET' AND kind='CONTROL' AND purpose='LOAN_PRINCIPAL')
 THEN RAISE EXCEPTION 'Charge funding/receipt must own its loan and GL source'; END IF;
 SELECT coalesce(sum(amount),0) INTO fees FROM loan_charge_event WHERE journal_id=j.id AND kind IN('COLLECT','COLLECT_UNDO','DEDUCT');
 IF p.kind<>'DISBURSEMENT' AND p.charge_amount<>fees THEN RAISE EXCEPTION 'Frozen receipt charge component must equal its charge allocations'; END IF;
 IF EXISTS(SELECT 1 FROM loan_charge_event WHERE journal_id=j.id AND (actor_id<>j.maker_id OR effective_date<>j.effective_date)) THEN RAISE EXCEPTION 'Charge allocations must retain posting identity and date'; END IF;
 IF fees<=0 THEN RAISE EXCEPTION 'Charge posting requires charge allocations'; END IF;
 IF j.source_type='LOAN_CHARGE_FUNDING' THEN
  IF p.kind<>'DISBURSEMENT' OR p.transaction_id IS NOT NULL OR p.amount<>a.amount OR p.principal_amount<>a.amount OR p.interest_amount<>0 OR a.status<>'DISBURSED' OR a.disbursement_date<>p.effective_date
   OR EXISTS(SELECT 1 FROM loan_charge_event WHERE journal_id=j.id AND (kind<>'DEDUCT' OR loan_id<>p.loan_id OR posting_id<>p.id))
  OR fees<>(SELECT coalesce(sum(amount),0) FROM loan_charge_term WHERE loan_id=p.loan_id AND timing='DEDUCT')
  THEN RAISE EXCEPTION 'Charge deduction must match frozen disbursement'; END IF;
 ELSE
  IF p.kind NOT IN('PAYMENT','REVERSAL') OR p.amount<>p.principal_amount+p.interest_amount+fees
   OR EXISTS(SELECT 1 FROM loan_charge_event WHERE journal_id=j.id AND (kind<>CASE WHEN p.kind='PAYMENT' THEN 'COLLECT' ELSE 'COLLECT_UNDO' END OR loan_id<>p.loan_id OR posting_id<>p.id))
   OR (p.interest_amount>0 AND NOT EXISTS(SELECT 1 FROM gl_account WHERE id=p.interest_account_id AND kind='POSTING' AND purpose='CLEARING' AND sacco_id=p.sacco_id))
  THEN RAISE EXCEPTION 'Interest, charges and principal must equal actual receipt cash'; END IF;
  IF p.principal_amount+p.interest_amount=0 THEN
   IF p.transaction_id IS NOT NULL THEN RAISE EXCEPTION 'Charge-only cash cannot fabricate a principal repayment'; END IF;
  ELSE
   SELECT * INTO t FROM loan_repayment_transactions WHERE id=p.transaction_id;
   IF t.id IS NULL OR t.loan_application_id<>p.loan_id OR t.sacco_id<>p.sacco_id OR t.station_id<>p.station_id OR t.actor_member_id<>p.actor_id OR t.request_key<>p.request_key
    OR t.payment_date<>p.effective_date OR t.amount<>p.principal_amount+p.interest_amount OR t.principal_amount<>p.principal_amount OR t.interest_amount<>p.interest_amount OR t.kind<>p.kind
   THEN RAISE EXCEPTION 'Charge receipt debt allocation must match the existing repayment ledger'; END IF;
  END IF;
  IF p.kind='PAYMENT' AND EXISTS(SELECT 1 FROM loan_ledger_installments WHERE loan_application_id=p.loan_id AND due_date<=p.effective_date AND interest_paid<interest)
  THEN RAISE EXCEPTION 'Due interest must be discharged before collecting charges'; END IF;
 END IF;
 expected:=1+(CASE WHEN p.principal_amount>0 THEN 1 ELSE 0 END)+(CASE WHEN p.interest_amount>0 THEN 1 ELSE 0 END)+(SELECT count(*) FROM loan_charge_event WHERE journal_id=j.id);
 IF n<>expected OR EXISTS((SELECT account_id,debit,credit FROM gl_journal_line WHERE journal_id=j.id) EXCEPT ALL
  (SELECT p.money_account_id,CASE WHEN p.kind='PAYMENT' THEN p.amount ELSE 0 END,CASE WHEN p.kind='PAYMENT' THEN 0 WHEN p.kind='DISBURSEMENT' THEN p.amount-fees ELSE p.amount END
   UNION ALL SELECT p.principal_account_id,CASE WHEN p.kind='PAYMENT' THEN 0 ELSE p.principal_amount END,CASE WHEN p.kind='PAYMENT' THEN p.principal_amount ELSE 0 END WHERE p.principal_amount>0
   UNION ALL SELECT p.interest_account_id,CASE WHEN p.kind='PAYMENT' THEN 0 ELSE p.interest_amount END,CASE WHEN p.kind='PAYMENT' THEN p.interest_amount ELSE 0 END WHERE p.interest_amount>0
   UNION ALL SELECT tr.receivable_account_id,CASE WHEN p.kind='REVERSAL' THEN e.amount ELSE 0 END,CASE WHEN p.kind='REVERSAL' THEN 0 ELSE e.amount END FROM loan_charge_event e JOIN loan_charge_term tr ON tr.id=e.term_id WHERE e.journal_id=j.id))
 THEN RAISE EXCEPTION 'Exact principal, charge-control and actual-money lines required'; END IF;
 IF p.kind='REVERSAL' THEN
  SELECT * INTO original FROM accountant_loan_post WHERE id=p.reverses_id;
  IF original.kind<>'PAYMENT' OR original.actor_id=p.actor_id OR original.journal_id IS DISTINCT FROM j.reverses_id OR p.effective_date<original.effective_date OR p.amount<>original.amount
   OR n<>(SELECT count(*) FROM gl_journal_line WHERE journal_id=original.journal_id)
   OR EXISTS(SELECT 1 FROM loan_charge_event e WHERE journal_id=original.journal_id AND NOT EXISTS(SELECT 1 FROM loan_charge_event r WHERE r.journal_id=j.id AND r.reverses_id=e.id))
   OR EXISTS((SELECT account_id,debit,credit FROM gl_journal_line WHERE journal_id=j.id) EXCEPT ALL(SELECT account_id,credit,debit FROM gl_journal_line WHERE journal_id=original.journal_id))
  THEN RAISE EXCEPTION 'Exact independent linked charge receipt reversal required'; END IF;
 ELSIF j.reverses_id IS NOT NULL THEN RAISE EXCEPTION 'Unexpected funding/receipt reversal'; END IF;
 IF l.principal_paid<>(SELECT coalesce(sum(CASE WHEN kind IN('PAYMENT','SETTLEMENT') THEN principal_amount ELSE -principal_amount END),0) FROM loan_repayment_transactions WHERE loan_application_id=p.loan_id)
 OR l.interest_paid<>(SELECT coalesce(sum(CASE WHEN kind IN('PAYMENT','SETTLEMENT') THEN interest_amount ELSE -interest_amount END),0) FROM loan_repayment_transactions WHERE loan_application_id=p.loan_id)
 THEN RAISE EXCEPTION 'Charge receipt must reconcile the existing debt ledger'; END IF;
END $$;

CREATE FUNCTION loan_charge_commit() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE j gl_journal; contract loan_charge_contract; BEGIN
 IF TG_TABLE_NAME='loan_charge_contract' THEN
  SELECT * INTO contract FROM loan_charge_contract WHERE loan_id=NEW.loan_id;
  IF (SELECT count(*) FROM loan_charge_term WHERE loan_id=NEW.loan_id)<>(SELECT jsonb_array_length(financial_snapshot->'recordedCharges'->'terms') FROM loan_applications WHERE id=NEW.loan_id)
  THEN RAISE EXCEPTION 'Complete frozen charge terms required'; END IF; RETURN NULL;
 END IF;
 SELECT * INTO j FROM gl_journal WHERE id=NEW.journal_id;
 IF j.state IS DISTINCT FROM 'POSTED' THEN RAISE EXCEPTION 'Charge events must commit with their owning posted journal'; END IF;
 IF j.source_type IN('LOAN_CHARGE_ASSESS','LOAN_CHARGE_UNDO','LOAN_CHARGE_FUNDING','LOAN_CHARGE_RECEIPT','LOAN_CHARGE_REVERSAL') THEN PERFORM loan_charge_validate(j);
 ELSIF j.source_type IN('LOAN_TOPUP','LOAN_TOPUP_UNDO') THEN PERFORM loan_topup_validate(j);
 ELSIF j.source_type='LOAN_ADJUSTMENT' THEN PERFORM loan_adjustment_validate_journal(j);
 ELSE RAISE EXCEPTION 'Charge event requires a specialized loan posting'; END IF;
 RETURN NULL;
END $$;

-- Retain the existing independent adjustment workflow and every scope/period/sequence check.
-- A charge-bearing funding correction swaps the original complete funding lines.
DO $$ DECLARE definition text; old_lines text; new_lines text; BEGIN
 SELECT pg_get_functiondef('loan_adjustment_validate_journal(gl_journal)'::regprocedure) INTO definition; definition:=replace(definition,chr(13),'');
 old_lines:=$old$OR (SELECT count(*) FROM gl_journal_line WHERE journal_id=j.id)<>2
 OR NOT EXISTS(SELECT 1 FROM gl_journal_line WHERE journal_id=j.id AND account_id=dr.id AND debit=q.amount AND credit=0)
 OR NOT EXISTS(SELECT 1 FROM gl_journal_line WHERE journal_id=j.id AND account_id=cr.id AND credit=q.amount AND debit=0)$old$;
 new_lines:=$new$OR ((q.kind<>'DISBURSEMENT_REVERSAL' OR NOT EXISTS(SELECT 1 FROM loan_charge_term WHERE loan_id=q.loan_id)) AND (
 (SELECT count(*) FROM gl_journal_line WHERE journal_id=j.id)<>2
 OR NOT EXISTS(SELECT 1 FROM gl_journal_line WHERE journal_id=j.id AND account_id=dr.id AND debit=q.amount AND credit=0)
 OR NOT EXISTS(SELECT 1 FROM gl_journal_line WHERE journal_id=j.id AND account_id=cr.id AND credit=q.amount AND debit=0)))$new$;
 old_lines:=replace(old_lines,chr(13),''); new_lines:=replace(new_lines,chr(13),'');
 IF strpos(definition,old_lines)=0 THEN RAISE EXCEPTION 'Expected adjustment validation boundary missing'; END IF;
 definition:=replace(definition,old_lines,new_lines);
 definition:=replace(definition,'OR (SELECT count(*) FROM gl_journal_line WHERE journal_id=original.id)<>2','OR (SELECT count(*) FROM gl_journal_line WHERE journal_id=original.id)<>(SELECT count(*) FROM gl_journal_line WHERE journal_id=j.id)');
 definition:=replace(definition,'OR q.reverses_journal_id<>p.journal_id OR p.actor_id=d.actor_id','OR q.reverses_journal_id<>p.journal_id OR p.actor_id=d.actor_id OR EXISTS(SELECT 1 FROM loan_charge_balance WHERE loan_id=q.loan_id AND (assessed<>0 OR outstanding<>0))'); EXECUTE definition;
END $$;

-- Assessment history is visible as charge documents, never as cash received.
DO $$ DECLARE columns text; BEGIN
 SELECT string_agg(format('movement.%I',attname),',' ORDER BY attnum) INTO columns FROM pg_attribute WHERE attrelid='accountant_loan_movement'::regclass AND attnum>0 AND NOT attisdropped;
 ALTER VIEW accountant_loan_movement RENAME TO accountant_loan_movement_v70;
 EXECUTE 'CREATE VIEW accountant_loan_movement AS SELECT p.* FROM accountant_loan_movement_v70 p UNION ALL SELECT '||columns||$view$
 FROM loan_charge_command c JOIN loan_applications l ON l.id=c.loan_id
 CROSS JOIN LATERAL jsonb_populate_record(NULL::accountant_loan_post,jsonb_build_object(
 'id',c.id,'loan_id',c.loan_id,'sacco_id',c.sacco_id,'station_id',c.station_id,'kind',CASE WHEN c.kind='ASSESS' THEN 'CHARGE_ASSESS' ELSE 'CHARGE_UNDO' END,'state','POSTED',
 'request_key',c.request_key,'payload_hash',c.payload_hash,'effective_date',c.effective_date,'reference','CHG-'||c.id::text,
 'amount',(SELECT sum(amount) FROM loan_charge_event WHERE command_id=c.id),'principal_amount',0,'interest_amount',0,'principal_balance',NULL,
 'money_account_id',NULL,'principal_account_id',NULL,'interest_account_id',NULL,'money_account_name','','principal_account_name','','interest_account_name','',
 'evidence',c.evidence,'notes',c.reason,'actor_id',c.actor_id,'recorded_at',c.recorded_at,'journal_id',c.journal_id,'transaction_id',NULL,'reverses_id',c.reverses_id)) movement$view$;
END $$;
CREATE CONSTRAINT TRIGGER loan_charge_contract_complete AFTER INSERT ON loan_charge_contract DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION loan_charge_commit();
CREATE CONSTRAINT TRIGGER loan_charge_event_commit AFTER INSERT ON loan_charge_event DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION loan_charge_commit();
CREATE CONSTRAINT TRIGGER loan_charge_command_commit AFTER INSERT ON loan_charge_command DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION loan_charge_commit();

DO $$ DECLARE definition text; BEGIN
 SELECT pg_get_functiondef('gl_validate_post()'::regprocedure) INTO definition; definition:=replace(definition,chr(13),'');
 definition:=replace(definition,'IF NEW.state<>''POSTED'' THEN RETURN NEW; END IF;',
  'IF NEW.state<>''POSTED'' THEN RETURN NEW; END IF; IF NEW.source_type IN(''LOAN_CHARGE_ASSESS'',''LOAN_CHARGE_UNDO'',''LOAN_CHARGE_FUNDING'',''LOAN_CHARGE_RECEIPT'',''LOAN_CHARGE_REVERSAL'') THEN PERFORM loan_charge_validate(NEW); RETURN NEW; END IF;'); EXECUTE definition;
 SELECT pg_get_functiondef('accountant_loan_commit()'::regprocedure) INTO definition; definition:=replace(definition,chr(13),'');
 definition:=replace(definition,'SELECT * INTO a FROM loan_applications',
  'IF j.source_type IN(''LOAN_CHARGE_FUNDING'',''LOAN_CHARGE_RECEIPT'',''LOAN_CHARGE_REVERSAL'') THEN PERFORM loan_charge_validate(j); RETURN NULL; END IF; SELECT * INTO a FROM loan_applications'); EXECUTE definition;
 SELECT pg_get_functiondef('core_post_commit_guard()'::regprocedure) INTO definition; definition:=replace(definition,chr(13),'');
 definition:=replace(definition,'IF NOT EXISTS(SELECT 1 FROM gl_journal j JOIN accounting_voucher',
  'IF EXISTS(SELECT 1 FROM gl_journal j WHERE j.id=journal AND j.state=''POSTED'' AND j.direct_post AND j.source_type IN(''LOAN_CHARGE_ASSESS'',''LOAN_CHARGE_UNDO'',''LOAN_CHARGE_FUNDING'',''LOAN_CHARGE_RECEIPT'',''LOAN_CHARGE_REVERSAL'')) THEN PERFORM loan_charge_validate((SELECT j1 FROM gl_journal j1 WHERE j1.id=journal)); RETURN NULL; END IF; IF NOT EXISTS(SELECT 1 FROM gl_journal j JOIN accounting_voucher'); EXECUTE definition;
 SELECT pg_get_functiondef('loan_topup_validate(gl_journal)'::regprocedure) INTO definition; definition:=replace(definition,chr(13),'');
 definition:=replace(definition,'e.principal+e.interest+e.cash','e.principal+e.interest+e.cash+e.settled_charges+e.deducted_charges');
 definition:=replace(definition,'cnt NOT BETWEEN 3 AND 4','cnt NOT BETWEEN 3 AND 10');
 definition:=replace(definition,'cnt<>(3+CASE WHEN e.interest>0 THEN 1 ELSE 0 END)','cnt<>(3+CASE WHEN e.interest>0 THEN 1 ELSE 0 END+(SELECT count(*) FROM loan_charge_event WHERE journal_id=j.id))');
 definition:=replace(definition,'UNION ALL SELECT p.money_account_id,0,e.cash','UNION ALL SELECT tr.receivable_account_id,0,ce.amount FROM loan_charge_event ce JOIN loan_charge_term tr ON tr.id=ce.term_id WHERE ce.journal_id=j.id AND ce.kind IN(''SETTLE'',''DEDUCT'') UNION ALL SELECT p.money_account_id,0,e.cash');
 definition:=replace(definition,'ROW(e.loan_id,e.source_id,e.posting_id,e.principal,e.interest,e.excluded_interest,e.cash)','ROW(e.loan_id,e.source_id,e.posting_id,e.principal,e.interest,e.excluded_interest,e.cash,e.settled_charges,e.deducted_charges)');
 definition:=replace(definition,'ROW(original.loan_id,original.source_id,original.posting_id,original.principal,original.interest,original.excluded_interest,original.cash)','ROW(original.loan_id,original.source_id,original.posting_id,original.principal,original.interest,original.excluded_interest,original.cash,original.settled_charges,original.deducted_charges)');
 definition:=replace(definition,'SELECT count(*),sum(debit-credit) INTO cnt,total FROM gl_journal_line WHERE journal_id=j.id;',
  'IF EXISTS(SELECT 1 FROM loan_charge_event ce WHERE ce.journal_id=j.id AND (ce.actor_id<>e.actor_id OR ce.effective_date<>e.effective_date OR ce.posting_id<>e.posting_id OR NOT ((ce.loan_id=e.source_id AND ce.kind=CASE WHEN e.reverses_id IS NULL THEN ''SETTLE'' ELSE ''SETTLE_UNDO'' END) OR (ce.loan_id=e.loan_id AND ce.kind=CASE WHEN e.reverses_id IS NULL THEN ''DEDUCT'' ELSE ''DEDUCT_UNDO'' END)))) OR (e.reverses_id IS NULL AND EXISTS(SELECT 1 FROM loan_charge_balance WHERE loan_id=e.source_id AND outstanding<>0)) THEN RAISE EXCEPTION ''Exact top-up charge ownership and complete source settlement required''; END IF; IF e.settled_charges<>(SELECT coalesce(sum(amount),0) FROM loan_charge_event WHERE journal_id=j.id AND loan_id=e.source_id AND kind=CASE WHEN e.reverses_id IS NULL THEN ''SETTLE'' ELSE ''SETTLE_UNDO'' END) OR e.deducted_charges<>(SELECT coalesce(sum(amount),0) FROM loan_charge_event WHERE journal_id=j.id AND loan_id=e.loan_id AND kind=CASE WHEN e.reverses_id IS NULL THEN ''DEDUCT'' ELSE ''DEDUCT_UNDO'' END) THEN RAISE EXCEPTION ''Top-up charge allocations must match source settlement and new deductions''; END IF; SELECT count(*),sum(debit-credit) INTO cnt,total FROM gl_journal_line WHERE journal_id=j.id;'); EXECUTE definition;
 ALTER TABLE gl_journal DROP CONSTRAINT gl_core_identity;
 ALTER TABLE gl_journal ADD CONSTRAINT gl_core_identity CHECK((NOT direct_post AND policy_id IS NOT NULL) OR (direct_post AND policy_id IS NULL AND checker_id IS NULL AND source_type IN
  ('CORE_RECEIPT','CORE_PAYMENT','CORE_JOURNAL','CORE_REVERSAL','LOAN_RECORD_DISBURSEMENT','LOAN_RECORD_PAYMENT','LOAN_RECORD_REVERSAL','LOAN_ADJUSTMENT','LOAN_TOPUP','LOAN_TOPUP_UNDO','LOAN_CHARGE_ASSESS','LOAN_CHARGE_UNDO','LOAN_CHARGE_FUNDING','LOAN_CHARGE_RECEIPT','LOAN_CHARGE_REVERSAL')));
END $$;

CREATE FUNCTION loan_charge_payment_guard() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF NEW.kind='PAYMENT' AND NEW.principal_amount>0 AND EXISTS(SELECT 1 FROM loan_charge_balance WHERE loan_id=NEW.loan_application_id AND outstanding>0)
 THEN RAISE EXCEPTION 'Due charges must be collected before principal'; END IF; RETURN NEW;
END $$;
CREATE TRIGGER loan_charge_payment_priority BEFORE INSERT ON loan_repayment_transactions FOR EACH ROW EXECUTE FUNCTION loan_charge_payment_guard();
-- Identity column is intentionally absent from the existing movement views' historical row type.

-- Loan lifecycle projections cannot conceal agreed fees or omit the funding deductions.
CREATE FUNCTION loan_charge_lifecycle_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE agreed numeric; withheld numeric; journal uuid; assessed numeric; outstanding numeric;
BEGIN
 IF NOT EXISTS(SELECT 1 FROM accountant_loan_record WHERE loan_id=NEW.id) THEN RETURN NULL; END IF;
 SELECT coalesce(sum(amount),0),coalesce(sum(CASE WHEN timing='DEDUCT' THEN amount ELSE 0 END),0) INTO agreed,withheld FROM loan_charge_term WHERE loan_id=NEW.id;
 SELECT coalesce(b.assessed,0),coalesce(b.outstanding,0) INTO assessed,outstanding FROM loan_charge_balance b WHERE loan_id=NEW.id;
 IF NEW.status='DISBURSED' AND OLD.status IS DISTINCT FROM NEW.status AND (OLD.status IS DISTINCT FROM 'PAID') THEN
  IF NEW.financial_snapshot->'recordedCharges' IS NOT NULL AND NOT EXISTS(SELECT 1 FROM loan_charge_contract WHERE loan_id=NEW.id) THEN RAISE EXCEPTION 'Recorded charge contract required at funding'; END IF;
  SELECT p.journal_id INTO journal FROM accountant_loan_post p WHERE p.loan_id=NEW.id AND p.kind='DISBURSEMENT' AND p.state='POSTED';
  IF agreed>0 AND (assessed IS DISTINCT FROM agreed OR withheld IS DISTINCT FROM (SELECT coalesce(sum(amount),0) FROM loan_charge_event WHERE loan_id=NEW.id AND kind='DEDUCT' AND journal_id=journal))
  THEN RAISE EXCEPTION 'Funding must assess all agreed charges and apply exactly the frozen deductions'; END IF;
 END IF;
 IF NEW.status='PAID' AND coalesce(outstanding,0)<>0 THEN RAISE EXCEPTION 'Outstanding charges prevent paid status'; END IF;
 IF NEW.status='DISBURSEMENT_REVERSED' AND (coalesce(assessed,0)<>0 OR coalesce(outstanding,0)<>0) THEN RAISE EXCEPTION 'Funding correction must also reverse the linked charges'; END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER loan_charge_lifecycle AFTER UPDATE ON loan_applications DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION loan_charge_lifecycle_guard();
