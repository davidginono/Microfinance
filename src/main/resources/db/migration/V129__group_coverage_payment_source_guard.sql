-- Preserve V128 history; use the receipt posting link and avoid the trigger OLD record alias.
CREATE OR REPLACE FUNCTION lending_coverage_payment_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE c lending_group_coverage_case;p accountant_loan_post;x lending_group_contribution;prior lending_group_coverage_payment;total numeric;
BEGIN
 IF TG_TABLE_NAME='lending_group_coverage_payment_correction' THEN
  SELECT * INTO prior FROM lending_group_coverage_payment WHERE id=NEW.payment_id;
  SELECT * INTO c FROM lending_group_coverage_case WHERE id=prior.case_id;
  PERFORM 1 FROM loan_applications WHERE id=c.loan_id FOR UPDATE;
  IF prior.id IS NULL OR prior.actor_id=NEW.actor_id OR NEW.recorded_at<prior.recorded_at
   OR NOT EXISTS(SELECT 1 FROM members WHERE id=NEW.actor_id AND sacco_id=c.sacco_id AND station_id=c.station_id AND status='ACTIVE')
  THEN RAISE EXCEPTION 'Coverage attribution correction requires a different scoped staff member';END IF;
  RETURN NEW;
 END IF;
 SELECT * INTO c FROM lending_group_coverage_case WHERE id=NEW.case_id;
 SELECT * INTO p FROM accountant_loan_post WHERE id=NEW.posting_id;
 PERFORM 1 FROM loan_applications WHERE id=c.loan_id FOR UPDATE;
 IF c.id IS NULL OR p.id IS NULL OR p.kind<>'PAYMENT' OR p.state<>'POSTED' OR p.loan_id<>c.loan_id OR p.sacco_id<>c.sacco_id OR p.station_id<>c.station_id
  OR p.recorded_at<c.recorded_at OR p.effective_date<c.call_date OR NEW.recorded_at<p.recorded_at
  OR EXISTS(SELECT 1 FROM accountant_loan_post WHERE reverses_id=p.id)
  OR NOT EXISTS(SELECT 1 FROM members WHERE id=NEW.actor_id AND sacco_id=c.sacco_id AND station_id=c.station_id AND status='ACTIVE')
 THEN RAISE EXCEPTION 'Coverage received requires its original posted loan payment';END IF;
 IF NEW.contribution_id IS NOT NULL THEN
  SELECT x0.* INTO x FROM lending_group_contribution x0 JOIN lending_group_receipt_loan r ON r.receipt_id=x0.receipt_id AND r.sacco_id=x0.sacco_id AND r.station_id=x0.station_id AND r.posting_id=p.id
   WHERE x0.id=NEW.contribution_id AND x0.loan_id=c.loan_id AND x0.sacco_id=c.sacco_id AND x0.station_id=c.station_id AND x0.payer_id=c.client_id AND x0.reverses_id IS NULL;
  IF x.id IS NULL OR ROW(NEW.amount,NEW.principal,NEW.interest,NEW.charges) IS DISTINCT FROM ROW(x.amount,x.principal,x.interest,x.charges)
   OR NEW.payer_text IS DISTINCT FROM x.payer_number||' - '||x.payer_name
  THEN RAISE EXCEPTION 'Coverage must retain the identified contribution and exact posted components';END IF;
 ELSE
  IF EXISTS(SELECT 1 FROM lending_group_receipt_loan WHERE posting_id=p.id)
   OR ROW(NEW.amount,NEW.principal,NEW.interest,NEW.charges) IS DISTINCT FROM ROW(p.amount,p.principal_amount,p.interest_amount,p.charge_amount)
   OR NEW.payer_text IS DISTINCT FROM (SELECT coalesce(nullif(r.payer_name,''),(SELECT min(payer) FROM lending_group_shared_movement WHERE posting_id=p.id),p.notes,'') FROM accountant_loan_post original LEFT JOIN accountant_loan_receipt_row rr ON rr.posting_id=original.id LEFT JOIN accountant_loan_receipt r ON r.id=rr.receipt_id WHERE original.id=p.id)
   OR length(trim(NEW.payer_text))=0
  THEN RAISE EXCEPTION 'Coverage must identify a complete actual payer payment';END IF;
 END IF;
 IF EXISTS(SELECT 1 FROM lending_group_coverage_payment linked_source WHERE linked_source.posting_id=p.id AND linked_source.contribution_id IS NOT DISTINCT FROM NEW.contribution_id
   AND NOT EXISTS(SELECT 1 FROM lending_group_coverage_payment_correction correction WHERE correction.payment_id=linked_source.id))
 THEN RAISE EXCEPTION 'A posted payer source cannot be attributed to two coverage cases';END IF;
 SELECT coalesce(sum(b.received),0) INTO total FROM lending_group_coverage_balance b WHERE b.loan_id=c.loan_id AND b.source_id=c.source_id AND b.source_kind=c.source_kind;
 IF total+NEW.amount>c.source_cap THEN RAISE EXCEPTION 'Coverage cash attribution exceeds the retained source ceiling';END IF;
 RETURN NEW;
END $$;
