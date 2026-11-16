-- A teller may collect an existing separate charge from funding cash with a signed
-- agreement reference. The append-only DEDUCT event records that election. Frozen
-- charge amounts, timing, assessed terms and earlier funding records stay unchanged.
DO $$
DECLARE definition text; previous text; replacement text;
BEGIN
 SELECT pg_get_functiondef('loan_charge_event_guard()'::regprocedure) INTO definition;
 previous:=$old$IF NEW.kind='DEDUCT' AND t.timing<>'DEDUCT' THEN$old$;
 replacement:=$new$IF NEW.kind='DEDUCT' AND t.timing<>'DEDUCT' AND
  (t.timing<>'SEPARATE' OR NEW.amount<>t.amount OR length(trim(coalesce(NEW.evidence,'')))=0
   OR NOT EXISTS(SELECT 1 FROM accountant_loan_post p JOIN gl_journal j ON j.id=p.journal_id
     WHERE p.id=NEW.posting_id AND p.loan_id=NEW.loan_id AND p.sacco_id=NEW.sacco_id
       AND p.station_id=NEW.station_id AND p.actor_id=NEW.actor_id AND p.kind='DISBURSEMENT'
       AND p.state='POSTING' AND p.journal_id=NEW.journal_id AND p.effective_date=NEW.effective_date
       AND j.source_type IN('LOAN_CHARGE_FUNDING','LOAN_TOPUP') AND j.state='DRAFT')) THEN$new$;
 IF strpos(definition,previous)=0 THEN RAISE EXCEPTION 'Expected charge deduction guard missing'; END IF;
 EXECUTE replace(definition,previous,replacement);

 SELECT pg_get_functiondef('loan_charge_validate(gl_journal)'::regprocedure) INTO definition;
 previous:=$old$FROM loan_charge_term WHERE loan_id=p.loan_id AND timing='DEDUCT'$old$;
 replacement:=$new$FROM loan_charge_term charge_term WHERE charge_term.loan_id=p.loan_id AND
   (charge_term.timing='DEDUCT' OR EXISTS(SELECT 1 FROM loan_charge_event e WHERE e.term_id=charge_term.id
      AND e.posting_id=p.id AND e.journal_id=j.id AND e.kind='DEDUCT'))$new$;
 IF strpos(definition,previous)=0 THEN RAISE EXCEPTION 'Expected funding charge reconciliation missing'; END IF;
 EXECUTE replace(definition,previous,replacement);

 SELECT pg_get_functiondef('loan_charge_lifecycle_guard()'::regprocedure) INTO definition;
 previous:=$old$IF agreed>0 AND (assessed IS DISTINCT FROM agreed OR withheld IS DISTINCT FROM$old$;
 replacement:=$new$withheld:=withheld+(SELECT coalesce(sum(e.amount),0) FROM loan_charge_event e
    JOIN loan_charge_term charge_term ON charge_term.id=e.term_id WHERE e.loan_id=NEW.id AND e.journal_id=journal
      AND e.kind='DEDUCT' AND charge_term.timing='SEPARATE');
  IF agreed>0 AND (assessed IS DISTINCT FROM agreed OR withheld IS DISTINCT FROM$new$;
 IF strpos(definition,previous)=0 THEN RAISE EXCEPTION 'Expected funding charge lifecycle reconciliation missing'; END IF;
 EXECUTE replace(definition,previous,replacement);
END $$;
