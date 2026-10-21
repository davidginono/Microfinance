-- Ordinary vouchers must not bypass loan-owned posting and repayment allocation.
-- Linked reversals retain their original evidence and specialized sources keep their guards.
CREATE FUNCTION protect_generated_loan_accounts() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.state='POSTED' AND NEW.source_type IN ('CORE_RECEIPT','CORE_PAYMENT','CORE_JOURNAL','MANUAL')
  AND EXISTS(SELECT 1 FROM gl_journal_line l JOIN loan_product_account_provision g ON g.sacco_id=NEW.sacco_id
   AND (l.account_id=g.principal_account_id OR l.account_id=g.interest_account_id) WHERE l.journal_id=NEW.id)
 THEN RAISE EXCEPTION 'Generated loan product accounts require loan-owned posting'; END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER generated_loan_account_posting_guard AFTER INSERT OR UPDATE ON gl_journal
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION protect_generated_loan_accounts();
