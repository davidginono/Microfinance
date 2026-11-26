ALTER TABLE loan_product_settings ADD COLUMN interest_accrual_mode varchar(16)
 CHECK (interest_accrual_mode IN ('DAILY','PERIODIC'));

CREATE TABLE loan_contract_interest_accrual (
 loan_id uuid PRIMARY KEY REFERENCES loan_ledgers(loan_application_id),
 mode varchar(16) NOT NULL CHECK (mode IN ('DAILY','PERIODIC')),
 tenure_unit varchar(8) NOT NULL CHECK (tenure_unit IN ('DAYS','MONTHS','YEARS'))
);

CREATE TRIGGER loan_contract_accrual_immutable BEFORE UPDATE OR DELETE ON loan_contract_interest_accrual
 FOR EACH ROW EXECUTE FUNCTION protect_loan_financial_history();

CREATE FUNCTION validate_loan_contract_accrual() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NOT EXISTS (
   SELECT 1 FROM loan_applications a
   WHERE a.id=NEW.loan_id AND a.applicant_member_id IS NOT NULL
     AND a.financial_snapshot->'prepaymentTerms'->>'accrualMode'=NEW.mode
     AND a.financial_snapshot->>'tenureUnit'=NEW.tenure_unit
 ) THEN RAISE EXCEPTION 'Interest accrual must match the retained individual contract'; END IF;
 RETURN NULL;
END $$;

CREATE CONSTRAINT TRIGGER loan_contract_accrual_scope AFTER INSERT ON loan_contract_interest_accrual
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION validate_loan_contract_accrual();

-- A bucket includes its ending anniversary. The next bucket starts the following day.
CREATE FUNCTION loan_accrual_bucket_open(disbursement date, collection_open date, unit text)
RETURNS date LANGUAGE plpgsql IMMUTABLE STRICT AS $$
DECLARE multiple integer; boundary date; bucket interval;
BEGIN
 IF unit='YEARS' THEN
   multiple:=extract(year from collection_open)-extract(year from disbursement);
   bucket:=interval '1 year';
 ELSE
   multiple:=(extract(year from collection_open)-extract(year from disbursement))*12
     +extract(month from collection_open)-extract(month from disbursement);
   bucket:=interval '1 month';
 END IF;
 IF multiple<=0 THEN RETURN disbursement; END IF;
 boundary:=(disbursement+multiple*bucket)::date;
 IF collection_open<=boundary THEN
   multiple:=multiple-1;
   boundary:=(disbursement+multiple*bucket)::date;
 END IF;
 RETURN CASE WHEN multiple=0 THEN disbursement ELSE boundary+1 END;
END $$;

-- In day-based periodic accrual the disbursement day is the first slice. Later
-- instalments begin on the day after the prior due date, so days never overlap.
CREATE FUNCTION loan_contract_interest_fraction(mode text, unit text, frequency text,
   disbursement date, prior_due date, due date, as_of date, collectible boolean)
RETURNS numeric LANGUAGE sql IMMUTABLE AS $$
 SELECT CASE
   WHEN as_of<disbursement THEN 0
   WHEN as_of>=due THEN 1
   WHEN mode='DAILY' THEN loan_period_fraction(coalesce(prior_due,disbursement),due,as_of,'ACTUAL_PERIOD')
   WHEN unit='DAYS' THEN
     CASE WHEN prior_due IS NULL THEN loan_period_fraction(disbursement,due,as_of+1,'ACTUAL_PERIOD')
          ELSE loan_period_fraction(prior_due,due,as_of,'ACTUAL_PERIOD') END
   WHEN as_of>=CASE WHEN collectible OR (unit='MONTHS' AND frequency='MONTHLY')
      THEN coalesce(prior_due+1,disbursement)
      ELSE loan_accrual_bucket_open(disbursement,coalesce(prior_due+1,disbursement),unit)
   END THEN 1
   ELSE 0
 END
$$;

CREATE FUNCTION loan_remaining_interest_fraction(current_fraction numeric, original_fraction numeric)
RETURNS numeric LANGUAGE sql IMMUTABLE STRICT AS $$
 SELECT CASE WHEN original_fraction>=1 THEN 1
   ELSE greatest(0,(current_fraction-original_fraction)/(1-original_fraction)) END
$$;

CREATE FUNCTION loan_configured_interest(installment uuid, as_of date, before_sequence bigint, collectible boolean)
RETURNS numeric LANGUAGE sql STABLE AS $$
 SELECT greatest(0,least(i.interest-adjustment.total,i.interest*current_value.fraction-adjustment.earned))
 FROM loan_ledger_installments i
 JOIN loan_contract_interest_accrual term ON term.loan_id=i.loan_application_id
 JOIN loan_ledgers l ON l.loan_application_id=i.loan_application_id
 JOIN loan_applications loan ON loan.id=i.loan_application_id
 LEFT JOIN loan_ledger_installments prior ON prior.loan_application_id=i.loan_application_id
      AND prior.installment_number=i.installment_number-1
 CROSS JOIN LATERAL (SELECT loan_contract_interest_fraction(term.mode,term.tenure_unit,loan.repayment_frequency,
    l.disbursement_date,prior.due_date,i.due_date,as_of,collectible) AS fraction) current_value
 CROSS JOIN LATERAL (
   SELECT coalesce(sum(e.amount),0) AS total,coalesce(sum(
     CASE WHEN current_value.fraction=0 THEN 0
     ELSE e.accrued_amount + (e.amount-e.accrued_amount)*loan_remaining_interest_fraction(current_value.fraction,
         loan_contract_interest_fraction(term.mode,term.tenure_unit,loan.repayment_frequency,l.disbursement_date,
           prior.due_date,i.due_date,e.original_date,collectible)) END),0) AS earned
   FROM (
     SELECT CASE WHEN t.kind IN ('PAYMENT','SETTLEMENT','PAYOFF') THEN a.interest_excluded ELSE -a.interest_excluded END amount,
         0::numeric accrued_amount,coalesce(original.payment_date,t.payment_date) original_date
     FROM loan_repayment_allocations a JOIN loan_repayment_transactions t ON t.id=a.transaction_id
     LEFT JOIN loan_repayment_transactions original ON original.id=t.reverses_transaction_id
     WHERE a.installment_id=i.id AND a.interest_excluded>0 AND t.payment_date<=as_of
       AND (before_sequence IS NULL OR t.sequence<before_sequence)
     UNION ALL
     SELECT s.sign*a.amount,s.sign*(a.amount-a.future_amount),coalesce(original.effective_date,w.effective_date)
     FROM loan_interest_waiver_part a JOIN loan_interest_waiver w ON w.id=a.waiver_id
     LEFT JOIN loan_interest_waiver original ON original.id=w.reverses_id
     CROSS JOIN LATERAL (SELECT CASE WHEN w.kind='WAIVER' THEN 1 ELSE -1 END AS sign) s
     WHERE a.installment_id=i.id AND w.effective_date<=as_of AND (before_sequence IS NULL OR w.sequence<before_sequence)
   ) e
 ) adjustment
 WHERE i.id=installment
$$;

ALTER FUNCTION loan_interest_net_of_waivers(uuid,date,bigint) RENAME TO loan_legacy_interest_net_of_waivers;

CREATE FUNCTION loan_interest_net_of_waivers(installment uuid,as_of date,before_sequence bigint)
RETURNS numeric LANGUAGE sql STABLE AS $$
 SELECT coalesce(loan_configured_interest(installment,as_of,before_sequence,false),
                 loan_legacy_interest_net_of_waivers(installment,as_of,before_sequence))
$$;

CREATE OR REPLACE FUNCTION loan_interest_earned_exact(installment uuid,as_of date)
RETURNS numeric LANGUAGE sql STABLE AS $$
 SELECT loan_interest_net_of_waivers(installment,as_of,NULL)
$$;

CREATE FUNCTION loan_interest_collectible_exact(installment uuid,as_of date)
RETURNS numeric LANGUAGE sql STABLE AS $$
 SELECT coalesce(loan_configured_interest(installment,as_of,NULL,true),loan_interest_earned_exact(installment,as_of))
$$;

CREATE FUNCTION loan_interest_unpaid(installment uuid,as_of date,collectible boolean)
RETURNS numeric LANGUAGE sql STABLE AS $$
 SELECT greatest(0, round(CASE WHEN collectible THEN loan_interest_collectible_exact(i.id,as_of)
                              ELSE loan_interest_earned_exact(i.id,as_of) END,2)
   - CASE WHEN EXISTS (SELECT 1 FROM loan_contract_interest_accrual term WHERE term.loan_id=i.loan_application_id)
        OR EXISTS (SELECT 1 FROM loan_monthly_interest_terms term WHERE term.loan_id=i.loan_application_id)
     THEN coalesce((SELECT sum((CASE WHEN t.kind IN ('PAYMENT','SETTLEMENT','PAYOFF') THEN 1 ELSE -1 END)
                   *(a.interest_amount-a.interest_returned))
          FROM loan_repayment_allocations a JOIN loan_repayment_transactions t ON t.id=a.transaction_id
          WHERE a.installment_id=i.id AND t.payment_date<=as_of),0)
     ELSE i.interest_paid END)
 FROM loan_ledger_installments i WHERE i.id=installment
$$;

CREATE OR REPLACE FUNCTION loan_interest_due(installment uuid,as_of date)
RETURNS numeric LANGUAGE sql STABLE AS $$
 SELECT loan_interest_unpaid(installment,as_of,true)
$$;

DO $$ DECLARE definition text; BEGIN
 SELECT pg_get_functiondef('loan_topup_validate(gl_journal)'::regprocedure) INTO definition;
 IF strpos(definition,'loan_interest_earned(id,e.effective_date)')=0 THEN
   RAISE EXCEPTION 'Top-up interest validation boundary missing';
 END IF;
 EXECUTE replace(definition,'loan_interest_earned(id,e.effective_date)','round(loan_interest_collectible_exact(id,e.effective_date),2)');
END $$;
