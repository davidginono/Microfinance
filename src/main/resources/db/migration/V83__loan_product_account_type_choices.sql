-- Product mappings choose the institution's accounts, rather than imposing account classifications.
-- Preserve existing contracts, frozen loan bindings, exact source ownership and linked corrections.
CREATE OR REPLACE FUNCTION validate_product_account_binding() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM loan_applications l JOIN loan_product_settings p ON p.id=l.loan_product_setting_id
   JOIN gl_account a ON a.id=NEW.principal_account_id AND a.sacco_id=NEW.sacco_id
   JOIN gl_account i ON i.id=NEW.interest_account_id AND i.sacco_id=NEW.sacco_id
   WHERE l.id=NEW.loan_id AND l.sacco_id=NEW.sacco_id AND l.station_id=NEW.station_id AND p.sacco_id=NEW.sacco_id AND p.id=NEW.product_id
    AND p.principal_gl_account_id=NEW.principal_account_id AND p.interest_gl_account_id=NEW.interest_account_id
    AND a.active AND a.kind IN ('POSTING','CONTROL') AND i.active AND i.kind IN ('POSTING','CONTROL'))
 THEN RAISE EXCEPTION 'Loan accounts must match its scoped product and active posting accounts'; END IF;
 IF NEW.source='EXISTING_LOAN' AND NOT EXISTS(SELECT 1 FROM accountant_loan_post WHERE loan_id=NEW.loan_id AND kind='DISBURSEMENT' AND state='POSTED' AND principal_account_id=NEW.principal_account_id)
 THEN RAISE EXCEPTION 'Existing loan principal must retain its original disbursement account'; END IF;
 RETURN NEW;
END $$;

-- Change only the principal/interest classification predicates inside the established validators.
-- Fail if their expected definitions differ, keeping all other financial checks intact.
DO $$ DECLARE definition text; updated text; BEGIN
 SELECT pg_get_functiondef('gl_validate_post()'::regprocedure) INTO definition;
 updated:=replace(definition,'a.kind=''CONTROL'' AND a.type=''ASSET'' AND a.purpose=''LOAN_PRINCIPAL''',
   'a.kind IN (''POSTING'',''CONTROL'')');
 IF updated=definition THEN RAISE EXCEPTION 'Expected loan principal classification guard missing'; END IF;
 definition:=updated;
 updated:=replace(definition,'i.kind=''POSTING'' AND (i.purpose=''CLEARING'' OR (i.type=''INCOME'' AND i.normal_balance=''CREDIT'' AND i.purpose=''INCOME''))',
   'i.kind IN (''POSTING'',''CONTROL'')');
 IF updated=definition THEN RAISE EXCEPTION 'Expected loan interest classification guard missing'; END IF; EXECUTE updated;

 SELECT pg_get_functiondef('loan_charge_validate(gl_journal)'::regprocedure) INTO definition;
 updated:=replace(definition,'type=''ASSET'' AND kind=''CONTROL'' AND purpose=''LOAN_PRINCIPAL''',
   'kind IN (''POSTING'',''CONTROL'')');
 IF updated=definition THEN RAISE EXCEPTION 'Expected charge principal classification guard missing'; END IF;
 definition:=updated;
 updated:=replace(definition,'kind=''POSTING'' AND (purpose=''CLEARING'' OR (type=''INCOME'' AND normal_balance=''CREDIT'' AND purpose=''INCOME'')) AND sacco_id=p.sacco_id',
   'kind IN (''POSTING'',''CONTROL'') AND sacco_id=p.sacco_id');
 IF updated=definition THEN RAISE EXCEPTION 'Expected charge interest classification guard missing'; END IF; EXECUTE updated;

 SELECT pg_get_functiondef('loan_topup_validate(gl_journal)'::regprocedure) INTO definition;
 updated:=replace(definition,'kind=''CONTROL'' AND type=''ASSET'' AND purpose=''LOAN_PRINCIPAL''',
   'kind IN (''POSTING'',''CONTROL'')');
 IF updated=definition THEN RAISE EXCEPTION 'Expected top-up principal classification guard missing'; END IF;
 definition:=updated;
 updated:=replace(definition,'a.kind=''POSTING'' AND (a.purpose=''CLEARING'' OR (a.type=''INCOME'' AND a.normal_balance=''CREDIT'' AND a.purpose=''INCOME''))',
   'a.kind IN (''POSTING'',''CONTROL'') AND a.id=(SELECT interest_account_id FROM loan_product_account_binding WHERE loan_id=e.source_id AND sacco_id=e.sacco_id AND station_id=e.station_id)');
 IF updated=definition THEN RAISE EXCEPTION 'Expected top-up interest classification guard missing'; END IF; EXECUTE updated;
END $$;
