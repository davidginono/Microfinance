-- Retain source corrections and cancellation decisions; do not rewrite previously posted history.
DROP INDEX ux_business_money_reference;
CREATE UNIQUE INDEX ux_business_money_reference ON accounting_business_document(sacco_id,station_id,money_account_key,channel_reference)
 WHERE channel_reference IS NOT NULL AND state<>'REJECTED'
 AND kind IN('LOAN_DISBURSEMENT','LOAN_REPAYMENT','UNMATCHED_RECEIPT','LOAN_ADVANCE','DIRECT_EXPENSE','PAYABLE_PAYMENT','CAPITAL_RECEIPT','FUNDING_RECEIPT','RECOVERY','REFUND');
ALTER TABLE accounting_business_control_entry ADD CONSTRAINT business_control_root_scope
 FOREIGN KEY(root_document_id,sacco_id,station_id) REFERENCES accounting_business_document(id,sacco_id,station_id);
CREATE INDEX ix_business_document_maker ON accounting_business_document(maker_id);
CREATE INDEX ix_business_document_checker ON accounting_business_document(checker_id) WHERE checker_id IS NOT NULL;
CREATE INDEX ix_accounting_supplier_maker ON accounting_supplier(maker_id);
CREATE TRIGGER business_supplier_evidence_immutable BEFORE UPDATE OR DELETE ON accounting_supplier
 FOR EACH ROW EXECUTE FUNCTION protect_loan_financial_history();

CREATE FUNCTION validate_business_source_correction() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE j gl_journal; original accounting_business_document; receipt loan_repayment_transactions;
BEGIN
 IF NEW.state='REJECTED' AND NEW.journal_id IS NOT NULL THEN
   IF NOT EXISTS(SELECT 1 FROM gl_source_cancellation c WHERE c.journal_id=NEW.journal_id
       AND c.sacco_id=NEW.sacco_id AND c.station_id=NEW.station_id AND c.maker_id=NEW.maker_id
       AND c.checker_id=NEW.checker_id AND c.source_reference=NEW.id::text AND c.evidence_reference=NEW.approval_evidence)
   THEN RAISE EXCEPTION 'Rejected submitted source requires its retained independent journal cancellation'; END IF;
 END IF;
 IF NEW.state<>'POSTED' THEN RETURN NEW; END IF;
 SELECT * INTO j FROM gl_journal WHERE id=NEW.journal_id;
 IF NEW.kind IN('BUSINESS_REVERSAL','LOAN_REPAYMENT_REVERSAL') THEN
   SELECT * INTO original FROM accounting_business_document WHERE id=NEW.related_document_id;
   IF j.source_type<>'SOURCE_REVERSAL' OR j.reverses_id IS DISTINCT FROM original.journal_id
      OR original.state<>'POSTED' OR original.sacco_id<>NEW.sacco_id OR original.station_id<>NEW.station_id
      OR NEW.effective_date<original.effective_date OR NEW.amount<>original.amount
   THEN RAISE EXCEPTION 'Source correction requires the independently linked original journal'; END IF;
 END IF;
 IF NEW.kind IN('LOAN_REPAYMENT','LOAN_REPAYMENT_REVERSAL') THEN
   SELECT * INTO receipt FROM loan_repayment_transactions WHERE id=NEW.loan_transaction_id;
   IF receipt.id IS NULL OR receipt.sacco_id<>NEW.sacco_id OR receipt.station_id<>NEW.station_id
      OR receipt.loan_application_id IS DISTINCT FROM NEW.loan_id OR receipt.actor_member_id IS DISTINCT FROM NEW.checker_id
      OR receipt.request_key<>NEW.request_key OR receipt.payment_date<>NEW.effective_date
      OR receipt.amount<>NEW.amount OR receipt.principal_amount<>NEW.principal OR receipt.interest_amount<>NEW.interest
      OR (NEW.kind='LOAN_REPAYMENT' AND receipt.kind<>'PAYMENT')
      OR (NEW.kind='LOAN_REPAYMENT_REVERSAL' AND (receipt.kind<>'REVERSAL'
          OR receipt.reverses_transaction_id IS DISTINCT FROM original.loan_transaction_id))
   THEN RAISE EXCEPTION 'Loan source, original receipt, effective date and exact allocations must agree'; END IF;
 END IF;
 RETURN NEW;
END; $$;
CREATE CONSTRAINT TRIGGER business_source_correction_validation AFTER INSERT OR UPDATE ON accounting_business_document
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION validate_business_source_correction();

CREATE FUNCTION protect_business_asset_basis() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'The posted fixed-asset register cannot be deleted'; END IF;
 IF ROW(NEW.id,NEW.sacco_id,NEW.station_id,NEW.description,NEW.acquired_on,NEW.cost)
    IS DISTINCT FROM ROW(OLD.id,OLD.sacco_id,OLD.station_id,OLD.description,OLD.acquired_on,OLD.cost)
 THEN RAISE EXCEPTION 'The posted fixed-asset acquisition basis is immutable'; END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER business_asset_basis_immutable BEFORE UPDATE OR DELETE ON accounting_fixed_asset
 FOR EACH ROW EXECUTE FUNCTION protect_business_asset_basis();
CREATE FUNCTION validate_business_asset_register() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE source accounting_business_document; registered accounting_fixed_asset; depreciation numeric; disposed boolean;
BEGIN
 SELECT * INTO registered FROM accounting_fixed_asset WHERE id=NEW.id;
 SELECT * INTO source FROM accounting_business_document WHERE id=NEW.id;
 SELECT coalesce(sum(amount),0) INTO depreciation FROM accounting_business_document
   WHERE related_document_id=NEW.id AND sacco_id=registered.sacco_id AND station_id=registered.station_id AND kind='DEPRECIATION' AND state='POSTED';
 SELECT exists(SELECT 1 FROM accounting_business_document WHERE related_document_id=NEW.id
   AND sacco_id=registered.sacco_id AND station_id=registered.station_id AND kind='ASSET_DISPOSAL' AND state='POSTED') INTO disposed;
 IF source.state<>'POSTED' OR source.kind<>'ASSET_PURCHASE' OR source.sacco_id<>registered.sacco_id OR source.station_id<>registered.station_id
    OR source.amount<>registered.cost OR source.effective_date<>registered.acquired_on OR source.description<>registered.description
    OR registered.accumulated_depreciation<>depreciation OR registered.disposed<>disposed
 THEN RAISE EXCEPTION 'The fixed-asset register must agree with retained posted acquisition and adjustment sources'; END IF;
 RETURN NEW;
END; $$;
CREATE CONSTRAINT TRIGGER business_asset_register_validation AFTER INSERT OR UPDATE ON accounting_fixed_asset
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION validate_business_asset_register();
CREATE UNIQUE INDEX ux_business_asset_disposal ON accounting_business_document(related_document_id)
 WHERE kind='ASSET_DISPOSAL' AND state='POSTED';
