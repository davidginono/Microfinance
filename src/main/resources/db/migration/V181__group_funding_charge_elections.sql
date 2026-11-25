CREATE OR REPLACE FUNCTION staff_group_funding_commit() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NOT EXISTS (
  SELECT 1
  FROM staff_group_loan g
  JOIN accountant_loan_post p ON p.loan_id=g.loan_id
  JOIN gl_journal j ON j.id=p.journal_id
  JOIN loan_ledgers b ON b.loan_application_id=g.loan_id
  JOIN loan_applications l ON l.id=g.loan_id
  WHERE g.loan_id=NEW.loan_id AND p.id=NEW.posting_id AND j.id=NEW.journal_id AND p.actor_id=NEW.actor_id
    AND p.kind='DISBURSEMENT' AND p.state='POSTED' AND j.state='POSTED'
    AND p.amount=l.amount AND b.principal=l.amount AND b.applicant_member_id IS NULL
    AND p.sacco_id=g.sacco_id AND p.station_id=g.station_id
    AND NOT EXISTS (SELECT 1 FROM staff_group_borrower WHERE loan_id=g.loan_id AND client_id=NEW.actor_id)
    AND EXISTS (SELECT 1 FROM lending_group WHERE id=g.group_id AND status='ACTIVE')
    AND NOT EXISTS (
     SELECT 1 FROM staff_group_borrower person JOIN members m ON m.id=person.client_id
     WHERE person.loan_id=g.loan_id AND (person.signed_on>p.effective_date OR m.status<>'ACTIVE'
       OR m.sacco_id<>g.sacco_id OR m.station_id<>g.station_id))
    AND NOT EXISTS (SELECT 1 FROM staff_group_guarantor WHERE loan_id=g.loan_id AND signed_on>p.effective_date)
    -- The recorded net cash already excludes mandatory deductions. Only additional
    -- elections evidenced in this funding journal reduce that amount further.
    AND (SELECT sum(credit-debit) FROM gl_journal_line WHERE journal_id=j.id AND account_id=p.money_account_id)
      = (l.financial_snapshot->>'recordedNetCashEstimate')::numeric - (
        SELECT coalesce(sum(e.amount),0)
        FROM loan_charge_event e JOIN loan_charge_term t ON t.id=e.term_id
        WHERE e.loan_id=g.loan_id AND e.posting_id=p.id AND e.journal_id=j.id
          AND e.kind='DEDUCT' AND t.timing='SEPARATE')
 ) THEN
  RAISE EXCEPTION 'simpleGroup.error.funding';
 END IF;
 RETURN NULL;
END $$;
