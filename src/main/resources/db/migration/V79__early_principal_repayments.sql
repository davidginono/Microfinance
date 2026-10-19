-- Excluded future interest is separate from cash collected and immutable contractual interest.
ALTER TABLE loan_ledger_installments ADD COLUMN interest_excluded numeric(18,2) NOT NULL DEFAULT 0;
ALTER TABLE loan_ledger_installments ADD CONSTRAINT installment_interest_exclusion
 CHECK (interest_excluded>=0 AND interest_paid+interest_excluded<=interest);
ALTER TABLE loan_repayment_transactions ADD COLUMN future_interest_excluded numeric(18,2) NOT NULL DEFAULT 0
 CHECK (future_interest_excluded>=0);
ALTER TABLE loan_repayment_allocations ADD COLUMN interest_excluded numeric(18,2) NOT NULL DEFAULT 0
 CHECK (interest_excluded>=0);
ALTER TABLE loan_repayment_allocations DROP CONSTRAINT loan_repayment_allocations_check;
ALTER TABLE loan_repayment_allocations ADD CONSTRAINT repayment_allocation_nonempty
 CHECK (principal_amount+interest_amount+interest_excluded>0);
CREATE INDEX ix_repayment_interest_exclusions ON loan_repayment_allocations(installment_id,transaction_id) WHERE interest_excluded>0;

CREATE FUNCTION validate_interest_exclusion_transaction() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE original loan_repayment_transactions; effective date;
BEGIN
 IF NEW.future_interest_excluded<>(SELECT coalesce(sum(a.interest_excluded),0) FROM loan_repayment_allocations a WHERE a.transaction_id=NEW.id)
 THEN RAISE EXCEPTION 'Excluded interest must match retained allocations'; END IF;
 IF NEW.future_interest_excluded=0 THEN RETURN NULL; END IF;
 IF NEW.kind NOT IN ('PAYMENT','REVERSAL') THEN RAISE EXCEPTION 'Excluded interest requires a payment or its reversal'; END IF;
 effective:=NEW.payment_date;
 IF NEW.kind='REVERSAL' THEN
   SELECT * INTO STRICT original FROM loan_repayment_transactions WHERE id=NEW.reverses_transaction_id;
   IF original.loan_application_id<>NEW.loan_application_id OR original.future_interest_excluded<>NEW.future_interest_excluded
   THEN RAISE EXCEPTION 'Excluded interest reversal must match the original payment'; END IF;
   effective:=original.payment_date;
 ELSE
   IF NOT EXISTS(SELECT 1 FROM loan_ledgers l WHERE l.loan_application_id=NEW.loan_application_id AND l.principal_paid=l.principal)
     OR EXISTS(SELECT 1 FROM loan_ledger_installments i WHERE i.loan_application_id=NEW.loan_application_id AND i.due_date<=effective AND i.interest_paid<>i.interest)
   THEN RAISE EXCEPTION 'Future interest can only be excluded on full repayment'; END IF;
 END IF;
 IF EXISTS(SELECT 1 FROM loan_repayment_allocations a JOIN loan_ledger_installments i ON i.id=a.installment_id
   WHERE a.transaction_id=NEW.id AND a.interest_excluded>0 AND (i.loan_application_id<>NEW.loan_application_id OR i.due_date<=effective))
 THEN RAISE EXCEPTION 'Only future interest on the repaid loan may be excluded'; END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER repayment_interest_exclusion AFTER INSERT ON loan_repayment_transactions
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION validate_interest_exclusion_transaction();

CREATE FUNCTION validate_interest_exclusion_projection() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE installment uuid; actual numeric; retained numeric;
BEGIN
 IF TG_TABLE_NAME='loan_ledger_installments' THEN installment:=NEW.id; ELSE installment:=NEW.installment_id; END IF;
 SELECT interest_excluded INTO actual FROM loan_ledger_installments WHERE id=installment;
 SELECT coalesce(sum(CASE WHEN t.kind IN ('PAYMENT','SETTLEMENT') THEN a.interest_excluded ELSE -a.interest_excluded END),0)
 INTO retained FROM loan_repayment_allocations a JOIN loan_repayment_transactions t ON t.id=a.transaction_id WHERE a.installment_id=installment AND a.interest_excluded>0;
 IF actual<>retained THEN RAISE EXCEPTION 'Excluded interest counter must match retained allocations'; END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER installment_exclusion_projection AFTER UPDATE ON loan_ledger_installments
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW WHEN(NEW.interest_excluded IS DISTINCT FROM OLD.interest_excluded)
 EXECUTE FUNCTION validate_interest_exclusion_projection();
CREATE CONSTRAINT TRIGGER allocation_exclusion_projection AFTER INSERT ON loan_repayment_allocations
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW WHEN(NEW.interest_excluded>0)
 EXECUTE FUNCTION validate_interest_exclusion_projection();
