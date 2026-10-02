-- Deactivation removes access, never an institution's historical closing obligation.
CREATE OR REPLACE FUNCTION accounting_validate_period_decision() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.state IS DISTINCT FROM OLD.state THEN
 IF NEW.state='CLOSED' THEN
 IF NEW.closed_by IS NULL OR NEW.closed_at IS NULL OR EXISTS(
 SELECT 1 FROM (
 SELECT station_id FROM sacco_stations WHERE sacco_id=NEW.sacco_id AND active
 UNION SELECT station_id FROM gl_journal WHERE sacco_id=NEW.sacco_id AND state='POSTED' AND effective_date<=NEW.ends_on
 UNION SELECT station_id FROM loan_ledgers WHERE sacco_id=NEW.sacco_id AND disbursement_date<=NEW.ends_on
 UNION SELECT station_id FROM reconciliation_statement WHERE sacco_id=NEW.sacco_id AND starts_on<=NEW.ends_on
 ) b WHERE NOT EXISTS(SELECT 1 FROM accounting_close_review r JOIN accounting_close_decision d ON d.review_id=r.id
 WHERE r.sacco_id=NEW.sacco_id AND r.period_id=NEW.id AND r.station_id=b.station_id AND r.action='CLOSE'
 AND r.recorded_at>coalesce((SELECT max(rd.decided_at) FROM accounting_close_review rr JOIN accounting_close_decision rd ON rd.review_id=rr.id WHERE rr.period_id=NEW.id AND rr.action='REOPEN'),'-infinity')))
 THEN RAISE EXCEPTION 'All active and historical branches need reviewed closing evidence'; END IF;
 ELSE
 IF NOT EXISTS(SELECT 1 FROM accounting_close_review r JOIN accounting_close_decision d ON d.review_id=r.id
 WHERE r.sacco_id=NEW.sacco_id AND r.period_id=NEW.id AND r.action='REOPEN' AND d.decided_at>=OLD.closed_at)
 THEN RAISE EXCEPTION 'Independent reopening decision is required'; END IF;
 END IF;
 END IF;
 RETURN NEW;
END; $$;
