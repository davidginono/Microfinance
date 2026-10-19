-- Product configuration is prospective. Historical journals and existing loans are not backfilled.
ALTER TABLE loan_product_settings ADD COLUMN principal_gl_account_id uuid;
ALTER TABLE loan_product_settings ADD COLUMN interest_gl_account_id uuid;
ALTER TABLE loan_product_settings ADD CONSTRAINT product_account_pair CHECK ((principal_gl_account_id IS NULL)=(interest_gl_account_id IS NULL));
ALTER TABLE loan_product_settings ADD FOREIGN KEY(principal_gl_account_id,sacco_id) REFERENCES gl_account(id,sacco_id);
ALTER TABLE loan_product_settings ADD FOREIGN KEY(interest_gl_account_id,sacco_id) REFERENCES gl_account(id,sacco_id);

CREATE TABLE loan_product_account_binding (
 loan_id uuid PRIMARY KEY, sacco_id varchar(255) NOT NULL, station_id varchar(255) NOT NULL,
 product_id uuid NOT NULL REFERENCES loan_product_settings(id), principal_account_id uuid NOT NULL, interest_account_id uuid NOT NULL,
 source varchar(24) NOT NULL CHECK(source IN('DISBURSEMENT','EXISTING_LOAN')),
 linked_by uuid NOT NULL REFERENCES members(id), linked_at timestamptz NOT NULL,
 FOREIGN KEY(loan_id,sacco_id,station_id) REFERENCES accountant_loan_record(loan_id,sacco_id,station_id),
 FOREIGN KEY(principal_account_id,sacco_id) REFERENCES gl_account(id,sacco_id),
 FOREIGN KEY(interest_account_id,sacco_id) REFERENCES gl_account(id,sacco_id)
);
CREATE TRIGGER loan_product_accounts_immutable BEFORE UPDATE OR DELETE ON loan_product_account_binding FOR EACH ROW EXECUTE FUNCTION gl_protect_evidence();

CREATE FUNCTION validate_product_account_binding() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM loan_applications l JOIN loan_product_settings p ON p.id=l.loan_product_setting_id
   JOIN gl_account a ON a.id=NEW.principal_account_id AND a.sacco_id=NEW.sacco_id
   JOIN gl_account i ON i.id=NEW.interest_account_id AND i.sacco_id=NEW.sacco_id
   WHERE l.id=NEW.loan_id AND l.sacco_id=NEW.sacco_id AND l.station_id=NEW.station_id AND p.sacco_id=NEW.sacco_id AND p.id=NEW.product_id
    AND p.principal_gl_account_id=NEW.principal_account_id AND p.interest_gl_account_id=NEW.interest_account_id
    AND a.active AND a.kind='CONTROL' AND a.type='ASSET' AND a.normal_balance='DEBIT' AND a.purpose='LOAN_PRINCIPAL'
    AND i.active AND i.kind='POSTING' AND ((i.type='INCOME' AND i.normal_balance='CREDIT' AND i.purpose='INCOME') OR i.purpose='CLEARING'))
 THEN RAISE EXCEPTION 'Loan accounts must match its scoped product and valid account classifications'; END IF;
 IF NEW.source='EXISTING_LOAN' AND NOT EXISTS(SELECT 1 FROM accountant_loan_post WHERE loan_id=NEW.loan_id AND kind='DISBURSEMENT' AND state='POSTED' AND principal_account_id=NEW.principal_account_id)
 THEN RAISE EXCEPTION 'Existing loan principal must retain its original disbursement account'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER product_account_binding_guard BEFORE INSERT ON loan_product_account_binding FOR EACH ROW EXECUTE FUNCTION validate_product_account_binding();

CREATE FUNCTION product_account_binding_commit() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM accountant_loan_post WHERE loan_id=NEW.loan_id AND sacco_id=NEW.sacco_id AND station_id=NEW.station_id AND kind='DISBURSEMENT' AND state='POSTED' AND principal_account_id=NEW.principal_account_id)
 THEN RAISE EXCEPTION 'Frozen principal account must match the committed disbursement'; END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER product_account_binding_source AFTER INSERT ON loan_product_account_binding DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION product_account_binding_commit();

-- New allocation policies need no shared interest account. Retain all old policy evidence.
ALTER TABLE repayment_accounting_policy ALTER COLUMN interest_account_id DROP NOT NULL;

-- Extend the established classification guards to product interest-income accounts.
-- Fail migration if an expected guard changes, rather than silently failing to extend it.
DO $$ DECLARE definition text; updated text; BEGIN
 SELECT pg_get_functiondef('gl_validate_post()'::regprocedure) INTO definition;
 updated:=replace(definition,'i.kind=''POSTING'' AND i.purpose=''CLEARING''',
   'i.kind=''POSTING'' AND (i.purpose=''CLEARING'' OR (i.type=''INCOME'' AND i.normal_balance=''CREDIT'' AND i.purpose=''INCOME''))');
 IF updated=definition THEN RAISE EXCEPTION 'Expected loan interest classification guard missing'; END IF; EXECUTE updated;
 SELECT pg_get_functiondef('loan_charge_validate(gl_journal)'::regprocedure) INTO definition;
 updated:=replace(definition,'kind=''POSTING'' AND purpose=''CLEARING'' AND sacco_id=p.sacco_id',
   'kind=''POSTING'' AND (purpose=''CLEARING'' OR (type=''INCOME'' AND normal_balance=''CREDIT'' AND purpose=''INCOME'')) AND sacco_id=p.sacco_id');
 IF updated=definition THEN RAISE EXCEPTION 'Expected charge receipt interest classification guard missing'; END IF; EXECUTE updated;
 SELECT pg_get_functiondef('loan_topup_validate(gl_journal)'::regprocedure) INTO definition;
 updated:=replace(definition,'a.kind=''POSTING'' AND a.purpose=''CLEARING''',
   'a.kind=''POSTING'' AND (a.purpose=''CLEARING'' OR (a.type=''INCOME'' AND a.normal_balance=''CREDIT'' AND a.purpose=''INCOME''))');
 IF updated=definition THEN RAISE EXCEPTION 'Expected top-up interest classification guard missing'; END IF; EXECUTE updated;
END $$;

-- New sources cannot override a frozen mapping, including direct calls that bypass the UI.
CREATE FUNCTION enforce_loan_product_posting_accounts() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE b loan_product_account_binding; BEGIN
 IF NEW.kind IN('REVERSAL','FUNDING_UNDO') THEN RETURN NEW; END IF;
 SELECT * INTO b FROM loan_product_account_binding WHERE loan_id=NEW.loan_id;
 IF b.loan_id IS NULL THEN RAISE EXCEPTION 'Link loan product accounts before posting'; END IF;
 IF NEW.principal_account_id<>b.principal_account_id OR (NEW.kind='PAYMENT' AND NEW.interest_account_id IS DISTINCT FROM b.interest_account_id)
 THEN RAISE EXCEPTION 'Loan posting must retain its frozen product accounts'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER loan_product_posting_accounts BEFORE INSERT ON accountant_loan_post FOR EACH ROW EXECUTE FUNCTION enforce_loan_product_posting_accounts();
