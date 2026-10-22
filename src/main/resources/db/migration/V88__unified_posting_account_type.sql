-- Posting is the only new transaction-account type. Preserve immutable legacy account
-- kinds, submitted versions and journal references; financial ownership follows purpose.
CREATE INDEX loan_charge_policy_receivable_account_scope ON loan_charge_policy(sacco_id,receivable_account_id);
CREATE INDEX loan_charge_policy_management_account_scope ON loan_charge_policy(sacco_id,management_account_id);
CREATE INDEX loan_charge_policy_insurance_account_scope ON loan_charge_policy(sacco_id,insurance_account_id);
CREATE INDEX loan_charge_term_receivable_account_scope ON loan_charge_term(sacco_id,receivable_account_id);
CREATE INDEX loan_charge_term_liability_account_scope ON loan_charge_term(sacco_id,liability_account_id);

CREATE FUNCTION gl_account_requires_loan_source(account uuid) RETURNS boolean LANGUAGE sql STABLE AS $$
 SELECT coalesce((SELECT a.kind='CONTROL'
  OR a.purpose IN ('LOAN_PRINCIPAL','INTEREST_RECEIVABLE','FEE_RECEIVABLE','ALLOWANCE')
  OR EXISTS(SELECT 1 FROM loan_product_account_provision p WHERE p.sacco_id=a.sacco_id
    AND (p.principal_account_id=a.id OR p.interest_account_id=a.id))
  OR EXISTS(SELECT 1 FROM loan_charge_policy p WHERE p.sacco_id=a.sacco_id
    AND (p.receivable_account_id=a.id OR p.management_account_id=a.id OR p.insurance_account_id=a.id))
  OR EXISTS(SELECT 1 FROM loan_charge_term t WHERE t.sacco_id=a.sacco_id
    AND (t.receivable_account_id=a.id OR t.liability_account_id=a.id))
 FROM gl_account a WHERE a.id=account),false)
$$;

-- Retain every other source, scope, amount, period and reversal guard verbatim.
DO $$ DECLARE definition text; updated text; BEGIN
 SELECT pg_get_functiondef('gl_protect_lines()'::regprocedure) INTO definition;
 updated:=replace(definition,'a.kind=''CONTROL''','gl_account_requires_loan_source(a.id)');
 IF updated=definition THEN RAISE EXCEPTION 'Expected manual journal account guard missing'; END IF;
 EXECUTE updated;

 SELECT pg_get_functiondef('gl_transaction_template_line_guard()'::regprocedure) INTO definition;
 updated:=replace(definition,'a.kind=''CONTROL''','gl_account_requires_loan_source(a.id)');
 IF updated=definition THEN RAISE EXCEPTION 'Expected manual template account guard missing'; END IF;
 EXECUTE updated;

 SELECT pg_get_functiondef('loan_charge_terms_guard()'::regprocedure) INTO definition;
 updated:=replace(definition,'kind=''CONTROL''','kind IN (''POSTING'',''CONTROL'')');
 IF updated=definition THEN RAISE EXCEPTION 'Expected loan charge account guard missing'; END IF;
 EXECUTE updated;

 SELECT pg_get_functiondef('loan_adjustment_validate_journal(gl_journal)'::regprocedure) INTO definition;
 updated:=replace(definition,'kind=''CONTROL''','kind IN (''POSTING'',''CONTROL'')');
 IF updated=definition THEN RAISE EXCEPTION 'Expected allowance account guard missing'; END IF;
 EXECUTE updated;
END $$;

-- The existing deferred guard also protects new posting accounts selected by charge
-- policies and loan purposes. Linked corrections keep their original source validators.
CREATE OR REPLACE FUNCTION protect_generated_loan_accounts() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.state='POSTED' AND NEW.source_type IN ('CORE_RECEIPT','CORE_PAYMENT','CORE_JOURNAL','MANUAL')
  AND EXISTS(SELECT 1 FROM gl_journal_line l WHERE l.journal_id=NEW.id AND gl_account_requires_loan_source(l.account_id))
 THEN RAISE EXCEPTION 'Loan workflow accounts require loan-owned posting'; END IF;
 RETURN NULL;
END $$;
