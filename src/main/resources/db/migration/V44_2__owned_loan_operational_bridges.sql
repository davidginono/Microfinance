-- New owned loan sources link their existing operational voucher to the same GL journal.
-- Previously posted source history is retained and is not silently backfilled.
CREATE FUNCTION validate_business_operational_bridge() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE j gl_journal; d accounting_business_document; actual_count bigint;
BEGIN
 SELECT * INTO j FROM gl_journal WHERE id=NEW.journal_id;
 IF j.id IS NULL OR j.sacco_id<>NEW.sacco_id OR j.station_id<>NEW.station_id
    OR NOT EXISTS(SELECT 1 FROM loan_journal_entries e JOIN loan_ledgers l ON l.loan_application_id=e.loan_application_id
         WHERE e.voucher_id=NEW.voucher_id AND l.sacco_id=NEW.sacco_id AND l.station_id=NEW.station_id)
    OR EXISTS(SELECT 1 FROM loan_journal_entries e JOIN loan_ledgers l ON l.loan_application_id=e.loan_application_id
         WHERE e.voucher_id=NEW.voucher_id AND (l.sacco_id<>NEW.sacco_id OR l.station_id<>NEW.station_id OR e.effective_date<>j.effective_date))
 THEN RAISE EXCEPTION 'Operational bridge requires the exact scoped dated voucher'; END IF;
 SELECT * INTO d FROM accounting_business_document WHERE journal_id=NEW.journal_id;
 IF d.id IS NOT NULL THEN
   IF d.kind NOT IN('LOAN_DISBURSEMENT','LOAN_REPAYMENT','LOAN_REPAYMENT_REVERSAL')
      OR d.state NOT IN('POSTING','POSTED') OR d.loan_id IS NULL OR j.state<>'POSTED'
      OR EXISTS(SELECT 1 FROM loan_journal_entries e WHERE e.voucher_id=NEW.voucher_id
         AND (e.loan_application_id<>d.loan_id OR (d.kind='LOAN_DISBURSEMENT' AND e.transaction_id IS NOT NULL)
         OR (d.kind<>'LOAN_DISBURSEMENT' AND e.transaction_id IS DISTINCT FROM NEW.voucher_id)))
   THEN RAISE EXCEPTION 'Owned bridge must retain its actual loan origin or receipt'; END IF;
 ELSE
   IF j.source_type<>'OPERATIONAL_BRIDGE' OR j.source_reference<>NEW.voucher_id::text
      OR EXISTS(SELECT 1 FROM accounting_business_document s JOIN loan_journal_entries e ON e.loan_application_id=s.loan_id
        WHERE e.voucher_id=NEW.voucher_id AND s.state IN('POSTING','POSTED') AND
          ((s.kind='LOAN_DISBURSEMENT' AND e.transaction_id IS NULL) OR s.loan_transaction_id=e.transaction_id))
   THEN RAISE EXCEPTION 'An owned loan voucher cannot create a second GL posting'; END IF;
 END IF;
 SELECT count(*) INTO actual_count FROM loan_journal_entries WHERE voucher_id=NEW.voucher_id;
 IF actual_count<2 OR actual_count>100 THEN RAISE EXCEPTION 'Operational voucher exceeds the verified source boundary'; END IF;
 RETURN NEW;
END; $$;
CREATE CONSTRAINT TRIGGER business_operational_bridge_source AFTER INSERT ON gl_operational_bridge
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION validate_business_operational_bridge();

CREATE FUNCTION require_business_operational_bridge() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE bridge gl_operational_bridge; mapped gl_account; principal_balance numeric; operational_principal numeric; operational_cash numeric; operational_interest numeric;
BEGIN
 IF NEW.state<>'POSTED' OR NEW.kind NOT IN('LOAN_DISBURSEMENT','LOAN_REPAYMENT','LOAN_REPAYMENT_REVERSAL') THEN RETURN NEW; END IF;
 SELECT * INTO bridge FROM gl_operational_bridge WHERE journal_id=NEW.journal_id;
 IF bridge.voucher_id IS NULL OR bridge.sacco_id<>NEW.sacco_id OR bridge.station_id<>NEW.station_id
    OR (NEW.kind<>'LOAN_DISBURSEMENT' AND bridge.voucher_id IS DISTINCT FROM NEW.loan_transaction_id)
    OR NOT EXISTS(SELECT 1 FROM loan_journal_entries e WHERE e.voucher_id=bridge.voucher_id AND e.loan_application_id=NEW.loan_id)
    OR EXISTS(SELECT 1 FROM loan_journal_entries e WHERE e.voucher_id=bridge.voucher_id
         AND (e.loan_application_id<>NEW.loan_id OR e.effective_date<>NEW.effective_date
           OR (NEW.kind='LOAN_DISBURSEMENT' AND e.transaction_id IS NOT NULL)))
 THEN RAISE EXCEPTION 'Posted owned loan source requires its exact immutable voucher link'; END IF;
 IF NEW.fees<>0 OR NEW.principal+NEW.interest<>NEW.amount OR (NEW.kind='LOAN_DISBURSEMENT' AND (NEW.principal<>NEW.amount OR NEW.interest<>0))
 THEN RAISE EXCEPTION 'Owned loan source must retain exact supported principal and interest components'; END IF;
 SELECT coalesce(sum(CASE WHEN account_code='LOAN_PRINCIPAL' THEN debit-credit ELSE 0 END),0),
        coalesce(sum(CASE WHEN account_code IN('DISBURSEMENT_CLEARING','CASH_CLEARING') THEN debit-credit ELSE 0 END),0),
        coalesce(sum(CASE WHEN account_code='INTEREST_COLLECTIONS_CLEARING' THEN debit-credit ELSE 0 END),0)
 INTO operational_principal,operational_cash,operational_interest FROM loan_journal_entries WHERE voucher_id=bridge.voucher_id;
 IF operational_principal<>(CASE WHEN NEW.kind='LOAN_REPAYMENT' THEN -NEW.principal ELSE NEW.principal END)
    OR operational_cash<>(CASE WHEN NEW.kind='LOAN_REPAYMENT' THEN NEW.amount ELSE -NEW.amount END)
    OR operational_interest<>(CASE WHEN NEW.kind='LOAN_REPAYMENT' THEN -NEW.interest ELSE NEW.interest END)
    OR EXISTS(SELECT 1 FROM loan_journal_entries WHERE voucher_id=bridge.voucher_id AND
      (account_code NOT IN('LOAN_PRINCIPAL','DISBURSEMENT_CLEARING','CASH_CLEARING','INTEREST_COLLECTIONS_CLEARING')
       OR (NEW.kind='LOAN_DISBURSEMENT' AND account_code IN('CASH_CLEARING','INTEREST_COLLECTIONS_CLEARING'))
       OR (NEW.kind<>'LOAN_DISBURSEMENT' AND account_code='DISBURSEMENT_CLEARING')))
 THEN RAISE EXCEPTION 'Owned loan source must agree with actual operational voucher components'; END IF;
 SELECT a.* INTO mapped FROM gl_journal j JOIN accounting_policies p ON p.id=j.policy_id
   JOIN gl_account a ON a.id::text=p.account_mappings_json::jsonb->>'LOAN_PRINCIPAL' AND a.sacco_id=j.sacco_id WHERE j.id=NEW.journal_id;
 SELECT coalesce(sum(debit-credit),0) INTO principal_balance FROM gl_journal_line WHERE journal_id=NEW.journal_id AND account_id=mapped.id;
 IF mapped.id IS NULL OR mapped.kind<>'CONTROL' OR mapped.purpose<>'LOAN_PRINCIPAL'
    OR principal_balance<>(CASE WHEN NEW.kind='LOAN_REPAYMENT' THEN -NEW.principal ELSE NEW.principal END)
 THEN RAISE EXCEPTION 'Owned loan source principal must agree with its mapped GL control'; END IF;
 RETURN NEW;
END; $$;
CREATE CONSTRAINT TRIGGER business_source_operational_agreement AFTER INSERT OR UPDATE ON accounting_business_document
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION require_business_operational_bridge();

-- A second journal cannot evade the one-voucher bridge by relying on an existing different link.
CREATE FUNCTION require_posted_operational_bridge_owner() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.state='POSTED' AND NEW.source_type='OPERATIONAL_BRIDGE' AND NOT EXISTS(
    SELECT 1 FROM gl_operational_bridge b WHERE b.journal_id=NEW.id AND b.sacco_id=NEW.sacco_id
      AND b.station_id=NEW.station_id AND b.voucher_id::text=NEW.source_reference)
 THEN RAISE EXCEPTION 'Posted operational journal requires its own unique immutable voucher bridge'; END IF;
 RETURN NEW;
END; $$;
CREATE CONSTRAINT TRIGGER operational_bridge_post_owner AFTER INSERT OR UPDATE ON gl_journal
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION require_posted_operational_bridge_owner();
