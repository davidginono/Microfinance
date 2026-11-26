CREATE TABLE loan_monthly_interest_terms (
    loan_id uuid PRIMARY KEY REFERENCES loan_ledgers(loan_application_id),
    effective_date date NOT NULL,
    first_installment_number integer NOT NULL CHECK (first_installment_number BETWEEN 1 AND 600),
    payoff_interest varchar(32) NOT NULL CHECK (payoff_interest IN ('EXCLUDE_OPEN_PERIOD','ELAPSED_DAYS')),
    actor_id uuid REFERENCES members(id),
    request_key uuid,
    agreement_reference varchar(160),
    approval_reference varchar(160),
    recorded_at timestamptz NOT NULL,
    CHECK ((actor_id IS NULL AND request_key IS NULL AND agreement_reference IS NULL AND approval_reference IS NULL)
        OR (actor_id IS NOT NULL AND request_key IS NOT NULL AND length(btrim(agreement_reference)) > 0
            AND length(btrim(approval_reference)) > 0 AND agreement_reference IS NOT NULL AND approval_reference IS NOT NULL))
);

CREATE TRIGGER monthly_interest_terms_immutable BEFORE UPDATE OR DELETE ON loan_monthly_interest_terms
 FOR EACH ROW EXECUTE FUNCTION protect_loan_financial_history();

CREATE FUNCTION validate_monthly_interest_terms() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NOT EXISTS (
    SELECT 1 FROM loan_ledgers l
    JOIN loan_applications loan ON loan.id=l.loan_application_id
    JOIN loan_ledger_installments inst ON inst.loan_application_id=l.loan_application_id
      AND inst.installment_number=NEW.first_installment_number
    LEFT JOIN loan_ledger_installments previous ON previous.loan_application_id=inst.loan_application_id
      AND previous.installment_number=inst.installment_number-1
    WHERE l.loan_application_id=NEW.loan_id AND loan.repayment_frequency='MONTHLY'
      AND NEW.effective_date=coalesce(previous.due_date+1,l.disbursement_date)
      AND (NEW.actor_id IS NOT NULL OR NEW.first_installment_number=1)
      AND (NEW.actor_id IS NULL OR EXISTS (
          SELECT 1 FROM members m WHERE m.id=NEW.actor_id AND m.sacco_id=l.sacco_id AND m.station_id=l.station_id
          AND m.is_member IS FALSE AND m.status='ACTIVE'))
 ) THEN RAISE EXCEPTION 'Monthly interest terms must match the agreed loan and period'; END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER monthly_interest_terms_scope AFTER INSERT ON loan_monthly_interest_terms
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION validate_monthly_interest_terms();

ALTER FUNCTION loan_interest_net_of_waivers(uuid,date,bigint) RENAME TO loan_interest_daily_net_of_waivers;

CREATE FUNCTION loan_interest_net_of_waivers(installment uuid,as_of date,before_sequence bigint)
RETURNS numeric LANGUAGE plpgsql STABLE AS $$
DECLARE row_interest numeric; opens_on date; rebates numeric; waivers numeric;
BEGIN
 SELECT i.interest,coalesce(previous.due_date+1,l.disbursement_date) INTO row_interest,opens_on
 FROM loan_ledger_installments i JOIN loan_ledgers l ON l.loan_application_id=i.loan_application_id
 JOIN loan_monthly_interest_terms term ON term.loan_id=i.loan_application_id
  AND term.first_installment_number<=i.installment_number AND term.effective_date<=as_of
 LEFT JOIN loan_ledger_installments previous ON previous.loan_application_id=i.loan_application_id
  AND previous.installment_number=i.installment_number-1
 WHERE i.id=installment;
 IF NOT FOUND THEN RETURN loan_interest_daily_net_of_waivers(installment,as_of,before_sequence); END IF;
 IF as_of<opens_on THEN RETURN 0; END IF;

 SELECT coalesce(sum(CASE WHEN t.kind IN ('PAYMENT','SETTLEMENT') THEN a.interest_excluded ELSE -a.interest_excluded END),0)
 INTO rebates FROM loan_repayment_allocations a JOIN loan_repayment_transactions t ON t.id=a.transaction_id
 WHERE a.installment_id=installment AND a.interest_excluded>0 AND t.payment_date<=as_of
   AND (before_sequence IS NULL OR t.sequence<before_sequence);
 SELECT coalesce(sum(CASE WHEN w.kind='WAIVER' THEN a.amount ELSE -a.amount END),0)
 INTO waivers FROM loan_interest_waiver_part a JOIN loan_interest_waiver w ON w.id=a.waiver_id
 WHERE a.installment_id=installment AND w.effective_date<=as_of
   AND (before_sequence IS NULL OR w.sequence<before_sequence);
 RETURN greatest(0,row_interest-rebates-waivers);
END $$;

CREATE OR REPLACE FUNCTION loan_interest_earned_exact(installment uuid,as_of date)
RETURNS numeric LANGUAGE sql STABLE AS $$
 SELECT loan_interest_net_of_waivers(installment,as_of,NULL)
$$;

CREATE OR REPLACE FUNCTION loan_interest_due(installment uuid,as_of date)
RETURNS numeric LANGUAGE sql STABLE AS $$
 SELECT greatest(0,loan_interest_earned(i.id,as_of)-CASE WHEN term.loan_id IS NULL THEN i.interest_paid
     ELSE coalesce((SELECT sum(CASE WHEN t.kind IN ('PAYMENT','SETTLEMENT') THEN a.interest_amount ELSE -a.interest_amount END)
          FROM loan_repayment_allocations a JOIN loan_repayment_transactions t ON t.id=a.transaction_id
          WHERE a.installment_id=i.id AND t.payment_date<=as_of),0) END)
 FROM loan_ledger_installments i
 LEFT JOIN loan_monthly_interest_terms term ON term.loan_id=i.loan_application_id
 WHERE i.id=installment
$$;
