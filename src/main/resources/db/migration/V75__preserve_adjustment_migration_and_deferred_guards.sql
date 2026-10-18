-- V69 retains its originally applied checksum. Install its later corrections as a new revision.
-- Keep V71 charge-aware reversals and V70/V71 source guards intact. No financial rows are rewritten.
CREATE OR REPLACE FUNCTION loan_adjustment_validate_journal(j gl_journal) RETURNS void LANGUAGE plpgsql AS $$
DECLARE q loan_adjustment_request; d loan_adjustment_decision; l loan_ledgers; a loan_applications;
 b record; p accountant_loan_post; dr gl_account; cr gl_account; period accounting_period; original gl_journal;
BEGIN
 SELECT * INTO q FROM loan_adjustment_request WHERE id=j.source_reference::uuid;
 SELECT * INTO d FROM loan_adjustment_decision WHERE request_id=q.id;
 SELECT * INTO a FROM loan_applications WHERE id=q.loan_id;
 SELECT * INTO l FROM loan_ledgers WHERE loan_application_id=q.loan_id FOR UPDATE;
 -- The GL constraint trigger runs at commit, after this adjustment is posted.
 -- Compare the frozen request against earlier events, excluding its own effects.
 WITH prior AS (
  SELECT r.*,x.journal_id,x.event_sequence FROM loan_adjustment_request r
  JOIN loan_adjustment_decision x ON x.request_id=r.id
  JOIN gl_journal g ON g.id=x.journal_id AND g.state='POSTED'
  WHERE r.loan_id=q.loan_id AND x.event_sequence<d.event_sequence
 ) SELECT coalesce(sum(allowance_change),0) allowance,
  coalesce(-sum(principal_change) FILTER(WHERE kind IN ('WRITE_OFF','WRITE_BACK')),0) written_off,
  max(effective_date) last_date,
  (SELECT id FROM prior ORDER BY event_sequence DESC LIMIT 1) last_adjustment,
  (SELECT CASE WHEN allowance_change>0 THEN credit_account_id ELSE debit_account_id END
   FROM prior WHERE kind='PROVISION' ORDER BY event_sequence DESC LIMIT 1) allowance_account,
  (SELECT journal_id FROM prior WHERE kind='WRITE_OFF' ORDER BY event_sequence DESC LIMIT 1) write_off_journal
 INTO b FROM prior;
 SELECT * INTO p FROM accountant_loan_post WHERE loan_id=q.loan_id AND kind='DISBURSEMENT' AND state='POSTED';
 SELECT * INTO period FROM accounting_period WHERE id=j.period_id FOR SHARE;
 IF q.id IS NULL OR d.decision IS DISTINCT FROM 'APPROVED' OR d.journal_id IS DISTINCT FROM j.id OR NOT j.direct_post
 OR j.policy_id IS NOT NULL OR j.checker_id IS NOT NULL OR j.currency<>'TZS' OR j.sacco_id<>q.sacco_id OR j.station_id<>q.station_id
 OR j.maker_id<>d.actor_id OR d.actor_id=q.actor_id OR j.effective_date<>q.effective_date OR d.effective_date<>q.effective_date
 OR j.request_key<>d.request_key OR j.payload_hash<>q.payload_hash OR j.reason<>q.reason OR j.evidence_reference<>q.evidence
 OR j.recorded_at<>d.recorded_at OR j.posted_at<>d.recorded_at OR j.reverses_id IS DISTINCT FROM q.reverses_journal_id
 OR l.loan_application_id IS NULL OR p.id IS NULL OR a.sacco_id<>q.sacco_id OR a.station_id<>q.station_id
 OR l.sacco_id<>q.sacco_id OR l.station_id<>q.station_id OR l.principal<>a.amount OR a.top_up_source_loan_id IS NOT NULL
 OR period.id IS NULL OR period.sacco_id<>q.sacco_id OR period.state<>'OPEN' OR q.effective_date NOT BETWEEN period.starts_on AND period.ends_on
 OR q.effective_date>CURRENT_DATE OR q.effective_date<l.disbursement_date OR q.effective_date<l.last_payment_date OR q.effective_date<b.last_date
 OR q.outstanding_before<>l.principal-l.principal_paid OR q.repayment_sequence<>l.next_sequence
 OR q.allowance_before<>b.allowance OR q.written_off_before<>b.written_off OR q.last_adjustment IS DISTINCT FROM b.last_adjustment
 THEN RAISE EXCEPTION 'Adjustment requires current scoped source, independent decision and open period'; END IF;
 IF NOT EXISTS(SELECT 1 FROM members m WHERE m.id=q.actor_id AND m.sacco_id=q.sacco_id AND m.station_id=q.station_id)
 OR NOT EXISTS(SELECT 1 FROM members m WHERE m.id=d.actor_id AND m.sacco_id=q.sacco_id AND m.station_id=q.station_id)
 THEN RAISE EXCEPTION 'Adjustment staff scope mismatch'; END IF;
 SELECT * INTO dr FROM gl_account WHERE id=q.debit_account_id FOR SHARE;
 SELECT * INTO cr FROM gl_account WHERE id=q.credit_account_id FOR SHARE;
 IF NOT dr.active OR NOT cr.active OR dr.sacco_id<>q.sacco_id OR cr.sacco_id<>q.sacco_id
 OR ((q.kind<>'DISBURSEMENT_REVERSAL' OR NOT EXISTS(SELECT 1 FROM loan_charge_term WHERE loan_id=q.loan_id)) AND (
 (SELECT count(*) FROM gl_journal_line WHERE journal_id=j.id)<>2
 OR NOT EXISTS(SELECT 1 FROM gl_journal_line WHERE journal_id=j.id AND account_id=dr.id AND debit=q.amount AND credit=0)
 OR NOT EXISTS(SELECT 1 FROM gl_journal_line WHERE journal_id=j.id AND account_id=cr.id AND credit=q.amount AND debit=0)))
 OR NOT EXISTS(SELECT 1 FROM accounting_outbox WHERE journal_id=j.id AND sacco_id=q.sacco_id AND station_id=q.station_id)
 THEN RAISE EXCEPTION 'Exact active balanced adjustment accounts and outbox required'; END IF;
 IF q.kind='PROVISION' THEN
  IF a.status NOT IN ('DISBURSED','PAR','DEFAULTED','PAID') OR b.written_off<>0 OR b.allowance+q.allowance_change<0
    OR b.allowance+q.allowance_change>q.outstanding_before
    OR NOT ((q.allowance_change>0 AND dr.type='EXPENSE' AND dr.kind='POSTING' AND dr.purpose='EXPENSE'
      AND cr.type='ASSET' AND cr.kind='CONTROL' AND cr.purpose='ALLOWANCE'
      AND (b.allowance=0 OR cr.id=b.allowance_account))
     OR (q.allowance_change<0 AND cr.type='EXPENSE' AND cr.kind='POSTING' AND cr.purpose='EXPENSE'
      AND dr.type='ASSET' AND dr.kind='CONTROL' AND dr.purpose='ALLOWANCE' AND dr.id=b.allowance_account))
  THEN RAISE EXCEPTION 'Invalid provision target or mapping'; END IF;
 ELSIF q.kind='WRITE_OFF' THEN
  IF a.status<>'WRITTEN_OFF' OR q.amount<>q.outstanding_before OR q.amount<>b.allowance OR b.written_off<>0
   OR dr.id<>b.allowance_account OR cr.id<>p.principal_account_id
  THEN RAISE EXCEPTION 'Full principal write-off requires its exact allowance'; END IF;
 ELSIF q.kind='WRITE_BACK' THEN
  IF a.status<>'DEFAULTED' OR q.amount<>b.written_off OR q.amount<>q.outstanding_before
   OR dr.id<>p.principal_account_id OR cr.id<>b.allowance_account OR q.reverses_journal_id<>b.write_off_journal
  THEN RAISE EXCEPTION 'Write-back must restore the linked write-off'; END IF;
 ELSIF q.kind='DISBURSEMENT_REVERSAL' THEN
  IF a.status<>'DISBURSEMENT_REVERSED' OR q.amount<>l.principal OR l.next_sequence<>1 OR l.principal_paid<>0 OR l.interest_paid<>0
   OR EXISTS(SELECT 1 FROM loan_repayment_transactions WHERE loan_application_id=q.loan_id)
   OR b.allowance<>0 OR b.written_off<>0 OR dr.id<>p.money_account_id OR cr.id<>p.principal_account_id
   OR q.reverses_journal_id<>p.journal_id OR p.actor_id=d.actor_id OR EXISTS(SELECT 1 FROM loan_charge_balance WHERE loan_id=q.loan_id AND (assessed<>0 OR outstanding<>0))
  THEN RAISE EXCEPTION 'Untouched disbursement and independent correction required'; END IF;
 END IF;
 IF q.reverses_journal_id IS NOT NULL THEN
  SELECT * INTO original FROM gl_journal WHERE id=q.reverses_journal_id FOR UPDATE;
  IF original.state<>'POSTED' OR original.maker_id=d.actor_id OR j.effective_date<original.effective_date
   OR (SELECT count(*) FROM gl_journal_line WHERE journal_id=original.id)<>(SELECT count(*) FROM gl_journal_line WHERE journal_id=j.id)
   OR EXISTS((SELECT account_id,debit,credit FROM gl_journal_line WHERE journal_id=j.id)
     EXCEPT ALL(SELECT account_id,credit,debit FROM gl_journal_line WHERE journal_id=original.id))
  THEN RAISE EXCEPTION 'Linked adjustment must exactly reverse original journal'; END IF;
 END IF;
END $$;

CREATE OR REPLACE FUNCTION loan_adjustment_payment_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 PERFORM 1 FROM loan_ledgers WHERE loan_application_id=NEW.loan_application_id FOR UPDATE;
 IF EXISTS(SELECT 1 FROM loan_applications WHERE id=NEW.loan_application_id AND status IN ('WRITTEN_OFF','DISBURSEMENT_REVERSED'))
 THEN RAISE EXCEPTION 'Written-off or reversed loans cannot receive ordinary repayment mutations'; END IF;
 IF NEW.payment_date<(SELECT max(q.effective_date) FROM loan_adjustment_request q JOIN loan_adjustment_decision d ON d.request_id=q.id
  JOIN gl_journal j ON j.id=d.journal_id AND j.state='POSTED' WHERE q.loan_id=NEW.loan_application_id)
 THEN RAISE EXCEPTION 'Repayment date cannot precede a posted loan adjustment'; END IF;
 RETURN NEW;
END $$;
