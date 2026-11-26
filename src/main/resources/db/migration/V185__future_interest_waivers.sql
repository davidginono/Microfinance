ALTER TABLE loan_interest_waiver ADD COLUMN future_amount numeric(18,2) NOT NULL DEFAULT 0
 CHECK(future_amount BETWEEN 0 AND amount);
ALTER TABLE loan_interest_waiver_part ADD COLUMN future_amount numeric(18,2) NOT NULL DEFAULT 0
 CHECK(future_amount BETWEEN 0 AND amount);
CREATE INDEX ix_interest_waiver_part_installment ON loan_interest_waiver_part(installment_id,waiver_id);

-- An instalment's cancelled future interest reduces accrual over the part of its
-- period that remained at the time of the waiver. A reversal restores that same
-- reduction starting on the reversal's effective date.
CREATE FUNCTION loan_interest_net_of_waivers(installment uuid,as_of date,before_sequence bigint)
RETURNS numeric LANGUAGE sql STABLE AS $$
 SELECT greatest(0,least(i.interest-i.interest_excluded-waivers.total,
                         loan_interest_earned_gross(i.id,as_of)-waivers.accrued))
 FROM loan_ledger_installments i
 JOIN loan_ledgers l ON l.loan_application_id=i.loan_application_id
 LEFT JOIN loan_prepayment_terms p ON p.loan_id=i.loan_application_id
 LEFT JOIN loan_ledger_installments prior ON prior.loan_application_id=i.loan_application_id
  AND prior.installment_number=i.installment_number-1
 CROSS JOIN LATERAL (
  SELECT coalesce(sum(s.sign*a.amount),0) total,
   coalesce(sum(s.sign*(a.amount-a.future_amount+a.future_amount*
    CASE WHEN p.loan_id IS NULL THEN CASE WHEN as_of>=i.due_date THEN 1 ELSE 0 END
         ELSE loan_period_fraction(greatest(coalesce(original.effective_date,w.effective_date),
                     coalesce(prior.due_date,l.disbursement_date)),i.due_date,as_of,p.day_basis) END)),0) accrued
  FROM loan_interest_waiver_part a JOIN loan_interest_waiver w ON w.id=a.waiver_id
  LEFT JOIN loan_interest_waiver original ON original.id=w.reverses_id
  CROSS JOIN LATERAL (SELECT CASE WHEN w.kind='WAIVER' THEN 1 ELSE -1 END AS sign) s
  WHERE a.installment_id=i.id AND w.effective_date<=as_of AND (before_sequence IS NULL OR w.sequence<before_sequence)
 ) waivers
 WHERE i.id=installment
$$;

CREATE OR REPLACE FUNCTION loan_interest_earned_exact(installment uuid,as_of date)
RETURNS numeric LANGUAGE sql STABLE AS $$
 SELECT loan_interest_net_of_waivers(installment,as_of,NULL)
$$;

CREATE OR REPLACE FUNCTION validate_interest_waiver() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE w loan_interest_waiver; original loan_interest_waiver; installment uuid; retained numeric; actual numeric;
BEGIN
 IF TG_TABLE_NAME='loan_ledger_installments' THEN installment:=NEW.id;
 ELSE
  IF TG_TABLE_NAME='loan_interest_waiver' THEN w:=NEW;
  ELSE SELECT * INTO STRICT w FROM loan_interest_waiver WHERE id=NEW.waiver_id; installment:=NEW.installment_id; END IF;
  IF NOT EXISTS(SELECT 1 FROM loan_ledgers l WHERE l.loan_application_id=w.loan_id AND l.sacco_id=w.sacco_id AND l.station_id=w.station_id AND l.disbursement_date<=w.effective_date AND l.next_sequence>w.sequence)
    OR w.effective_date>CURRENT_DATE
    OR NOT EXISTS(SELECT 1 FROM accounting_period p WHERE p.sacco_id=w.sacco_id AND p.state='OPEN' AND w.effective_date BETWEEN p.starts_on AND p.ends_on)
    OR NOT EXISTS(SELECT 1 FROM members m WHERE m.id=w.actor_id AND m.sacco_id=w.sacco_id AND m.station_id=w.station_id AND m.status='ACTIVE')
    OR EXISTS(SELECT 1 FROM loan_repayment_transactions t WHERE t.loan_application_id=w.loan_id AND t.sequence=w.sequence)
  THEN RAISE EXCEPTION 'Waiver scope must match'; END IF;
  IF (SELECT ROW(coalesce(sum(a.amount),0),coalesce(sum(a.future_amount),0))
      FROM loan_interest_waiver_part a WHERE a.waiver_id=w.id)
      IS DISTINCT FROM ROW(w.amount,w.future_amount)
  THEN RAISE EXCEPTION 'Waiver totals must match allocations'; END IF;
  IF TG_TABLE_NAME='loan_interest_waiver_part' AND EXISTS(
    SELECT 1 FROM loan_ledger_installments i WHERE i.id=installment
    AND (i.loan_application_id<>w.loan_id OR (w.kind='WAIVER' AND
      NEW.future_amount<>greatest(0,NEW.amount-greatest(0,
        round(loan_interest_net_of_waivers(i.id,w.effective_date,w.sequence),2)-i.interest_paid))))
  )
  THEN RAISE EXCEPTION 'Waiver allocation must match the loan and earned interest at its effective date'; END IF;
  IF TG_TABLE_NAME='loan_interest_waiver' AND w.kind='REVERSAL' THEN
   SELECT * INTO STRICT original FROM loan_interest_waiver WHERE id=w.reverses_id;
   IF original.kind<>'WAIVER' OR original.loan_id<>w.loan_id OR original.amount<>w.amount OR original.future_amount<>w.future_amount
    OR original.actor_id=w.actor_id OR w.effective_date<original.effective_date
    OR EXISTS((SELECT installment_id,amount,future_amount FROM loan_interest_waiver_part WHERE waiver_id=original.id EXCEPT SELECT installment_id,amount,future_amount FROM loan_interest_waiver_part WHERE waiver_id=w.id)
       UNION ALL (SELECT installment_id,amount,future_amount FROM loan_interest_waiver_part WHERE waiver_id=w.id EXCEPT SELECT installment_id,amount,future_amount FROM loan_interest_waiver_part WHERE waiver_id=original.id))
   THEN RAISE EXCEPTION 'Waiver reversal must independently match the original'; END IF;
  END IF;
 END IF;
 IF installment IS NOT NULL THEN
  SELECT interest_waived INTO actual FROM loan_ledger_installments WHERE id=installment;
  SELECT coalesce(sum(CASE WHEN ev.kind='WAIVER' THEN a.amount ELSE -a.amount END),0) INTO retained FROM loan_interest_waiver_part a JOIN loan_interest_waiver ev ON ev.id=a.waiver_id WHERE a.installment_id=installment;
  IF actual<>retained THEN RAISE EXCEPTION 'Waived interest counter must match immutable waiver history'; END IF;
 END IF;
 RETURN NULL;
END $$;
