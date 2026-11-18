-- Cancellation retains contracts, signed papers, decisions and every financial entry.
-- Funding deductions belong to the disbursement; later charge collections are repayments.
CREATE FUNCTION loan_has_repayment_history(application uuid) RETURNS boolean LANGUAGE sql STABLE AS $$
 SELECT EXISTS(SELECT 1 FROM loan_repayment_transactions WHERE loan_application_id=application)
 OR EXISTS(SELECT 1 FROM accountant_loan_post WHERE loan_id=application AND kind='PAYMENT')
 OR EXISTS(SELECT 1 FROM loan_charge_event WHERE loan_id=application AND kind IN('COLLECT','SETTLE'))
$$;

CREATE FUNCTION loan_cancellation_error(application uuid) RETURNS text LANGUAGE plpgsql STABLE AS $$
DECLARE l loan_applications; b loan_adjustment_balance;
BEGIN
 SELECT * INTO l FROM loan_applications WHERE id=application;
 IF l.id IS NULL OR NOT EXISTS(SELECT 1 FROM accountant_loan_record WHERE loan_id=l.id)
 THEN RETURN 'cancellation.error.unverified'; END IF;
 IF loan_has_repayment_history(l.id) THEN RETURN 'cancellation.error.repayments'; END IF;
 IF l.status NOT IN('RECORDED','SUBMITTED','READY_FOR_DISBURSEMENT','REJECTED','DISBURSED','PAR','DEFAULTED')
 THEN RETURN 'cancellation.error.state'; END IF;
 IF EXISTS(SELECT 1 FROM loan_applications child WHERE child.top_up_source_loan_id=l.id
   AND child.status NOT IN('WITHDRAWN','REJECTED','MANAGER_REJECTED','LOAN_OFFICER_REJECTED','CHAIRPERSON_REJECTED','BOARD_REJECTED','CREDIT_COMMITTEE_REJECTED','ACCOUNTANT_REJECTED','DISBURSEMENT_REVERSED','PAID'))
 THEN RETURN 'cancellation.error.dependent'; END IF;
 IF l.status IN('DISBURSED','PAR','DEFAULTED') THEN
  IF l.top_up_source_loan_id IS NOT NULL THEN RETURN 'cancellation.error.topUp'; END IF;
  IF NOT EXISTS(SELECT 1 FROM loan_ledgers ledger JOIN accountant_loan_post p ON p.loan_id=ledger.loan_application_id
    WHERE ledger.loan_application_id=l.id AND ledger.sacco_id=l.sacco_id AND ledger.station_id=l.station_id
    AND p.kind='DISBURSEMENT' AND p.state='POSTED' AND p.sacco_id=l.sacco_id AND p.station_id=l.station_id
    AND ledger.next_sequence=1 AND ledger.principal=l.amount AND ledger.principal_paid=0 AND ledger.interest_paid=0)
  THEN RETURN 'cancellation.error.unverified'; END IF;
  SELECT * INTO b FROM loan_adjustment_balance WHERE loan_id=l.id;
  IF b.loan_id IS NULL THEN RETURN 'cancellation.error.unverified'; END IF;
  IF b.allowance<>0 OR b.written_off<>0 THEN RETURN 'cancellation.error.allowance'; END IF;
 ELSE
  IF EXISTS(SELECT 1 FROM loan_ledgers WHERE loan_application_id=l.id)
   OR EXISTS(SELECT 1 FROM accountant_loan_post WHERE loan_id=l.id)
   OR EXISTS(SELECT 1 FROM loan_charge_event WHERE loan_id=l.id)
  THEN RETURN 'cancellation.error.unverified'; END IF;
 END IF;
 IF EXISTS(SELECT 1 FROM loan_adjustment_request q WHERE q.loan_id=l.id
   AND NOT EXISTS(SELECT 1 FROM loan_adjustment_decision WHERE request_id=q.id))
 THEN RETURN 'cancellation.error.pending'; END IF;
 RETURN NULL;
END $$;

-- Keep the evidence table's compatibility name and all existing group withdrawal behavior.
-- Individual withdrawals require the new explicit permission, with no automatic grants.
CREATE OR REPLACE FUNCTION staff_group_withdrawal_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE l loan_applications;
BEGIN
 SELECT * INTO l FROM loan_applications WHERE id=NEW.loan_id FOR UPDATE;
 IF NOT EXISTS(SELECT 1 FROM members m WHERE m.id=NEW.actor_id AND m.sacco_id=NEW.sacco_id AND m.station_id=NEW.station_id
   AND m.status='ACTIVE' AND (m.staff_access_status='ACTIVE' OR (m.staff_access_status='NONE' AND m.position<>'MEMBER')))
  OR l.id IS NULL OR l.sacco_id<>NEW.sacco_id OR l.station_id<>NEW.station_id
  OR l.status<>NEW.previous_status OR loan_group_terms(l)<>NEW.terms_hash
  OR l.status IN('DISBURSED','PAR','DEFAULTED','PAID','WRITTEN_OFF','DISBURSEMENT_REVERSED','WITHDRAWN')
  OR EXISTS(SELECT 1 FROM loan_ledgers WHERE loan_application_id=l.id)
  OR loan_has_repayment_history(l.id)
 THEN RAISE EXCEPTION 'cancellation.error.changed'; END IF;
 IF l.applicant_member_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM lending_group_participant WHERE loan_id=l.id) THEN
  IF loan_cancellation_error(l.id) IS NOT NULL OR NOT EXISTS(SELECT 1 FROM member_access_claims
    WHERE member_id=NEW.actor_id AND claim_name='LOAN_RECORDING_CANCEL')
  THEN RAISE EXCEPTION 'cancellation.error.changed'; END IF;
 END IF;
 RETURN NEW;
END $$;

-- A direct SQL insert must not reserve cancellation evidence without completing it.
CREATE FUNCTION loan_cancellation_complete() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF NOT EXISTS(SELECT 1 FROM loan_applications WHERE id=NEW.loan_id AND sacco_id=NEW.sacco_id
   AND station_id=NEW.station_id AND status='WITHDRAWN')
 THEN RAISE EXCEPTION 'Complete loan cancellation required'; END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER loan_cancellation_complete AFTER INSERT ON staff_group_withdrawal
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION loan_cancellation_complete();

-- Recheck at posting, including charge-only receipts subsequently reversed.
DO $$ DECLARE definition text; BEGIN
 SELECT pg_get_functiondef('loan_adjustment_validate_journal(gl_journal)'::regprocedure) INTO definition;
 IF strpos(definition,'BEGIN')=0 THEN RAISE EXCEPTION 'Adjustment guard missing'; END IF;
 EXECUTE regexp_replace(definition,'BEGIN',$patch$BEGIN
 IF EXISTS(SELECT 1 FROM loan_adjustment_request request WHERE request.id::text=j.source_reference
   AND request.kind='DISBURSEMENT_REVERSAL' AND loan_has_repayment_history(request.loan_id))
 THEN RAISE EXCEPTION 'cancellation.error.repayments'; END IF;
 $patch$,'i');
END $$;
