-- Existing evidence stays immutable. New date-mismatched approvals require a reviewed exact source pair.
CREATE TABLE reconciliation_timing_source (
 exception_id uuid PRIMARY KEY REFERENCES reconciliation_exception(id),
 journal_line_id uuid NOT NULL REFERENCES gl_journal_line(id),
 original_reversed boolean NOT NULL,
 created_at timestamptz NOT NULL
);
CREATE INDEX ix_recon_timing_pair ON reconciliation_timing_source(journal_line_id,exception_id);
CREATE TRIGGER recon_timing_immutable BEFORE UPDATE OR DELETE ON reconciliation_timing_source FOR EACH ROW EXECUTE FUNCTION reconciliation_immutable();
CREATE FUNCTION reconciliation_valid_timing_pair(exception uuid, line uuid, reversed boolean) RETURNS boolean LANGUAGE sql STABLE AS $$
 SELECT EXISTS(
 SELECT 1 FROM reconciliation_exception e JOIN reconciliation_statement_line sl ON sl.id=e.statement_line_id
 JOIN reconciliation_statement s ON s.id=sl.statement_id
 JOIN gl_journal_line jl ON jl.id=line JOIN gl_journal j ON j.id=jl.journal_id
 WHERE e.id=exception AND e.kind='TIMING'
 AND ROW(e.sacco_id,e.station_id,s.account_id)=ROW(j.sacco_id,j.station_id,jl.account_id)
 AND ROW(e.sacco_id,e.station_id)=ROW(s.sacco_id,s.station_id)
 AND j.state='POSTED' AND j.source_type<>'OPENING' AND sign(sl.amount)=sign(jl.debit-jl.credit)
 AND abs(sl.effective_date-j.effective_date) BETWEEN 1 AND 30
 AND reversed=EXISTS(SELECT 1 FROM gl_journal r WHERE r.reverses_id=j.id AND r.state='POSTED')
 )
$$;
CREATE FUNCTION reconciliation_validate_timing_source() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM reconciliation_exception e WHERE e.id=NEW.exception_id AND e.xmin=pg_current_xact_id()::text::xid)
 OR EXISTS(SELECT 1 FROM reconciliation_exception_decision WHERE exception_id=NEW.exception_id)
 OR NOT reconciliation_valid_timing_pair(NEW.exception_id,NEW.journal_line_id,NEW.original_reversed)
 THEN RAISE EXCEPTION 'Timing source must be an atomic exact scoped posted pair within thirty days'; END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER recon_timing_source_check BEFORE INSERT ON reconciliation_timing_source FOR EACH ROW EXECUTE FUNCTION reconciliation_validate_timing_source();
CREATE FUNCTION reconciliation_review_timing_source() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF EXISTS(SELECT 1 FROM reconciliation_timing_source t WHERE t.exception_id=NEW.exception_id
 AND NOT reconciliation_valid_timing_pair(t.exception_id,t.journal_line_id,t.original_reversed))
 THEN RAISE EXCEPTION 'Timing source changed before independent review'; END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER recon_timing_review_check BEFORE INSERT ON reconciliation_exception_decision FOR EACH ROW EXECUTE FUNCTION reconciliation_review_timing_source();
CREATE FUNCTION reconciliation_require_reviewed_timing() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.decision='APPROVED' AND EXISTS(
 SELECT 1 FROM reconciliation_allocation a JOIN reconciliation_statement_line sl ON sl.id=a.statement_line_id
 JOIN gl_journal_line jl ON jl.id=a.journal_line_id JOIN gl_journal j ON j.id=jl.journal_id
 WHERE a.match_id=NEW.match_id AND (j.source_type='OPENING' OR (sl.effective_date<>j.effective_date
 AND NOT EXISTS(SELECT 1 FROM reconciliation_exception e JOIN reconciliation_exception_decision d ON d.exception_id=e.id
 JOIN reconciliation_timing_source t ON t.exception_id=e.id
 WHERE e.statement_line_id=sl.id AND t.journal_line_id=jl.id
 AND reconciliation_valid_timing_pair(e.id,t.journal_line_id,t.original_reversed)))))
 THEN RAISE EXCEPTION 'Date-mismatched allocations require independent reviewed source-linked timing evidence'; END IF;
 RETURN NEW;
END; $$;
CREATE CONSTRAINT TRIGGER recon_match_timing AFTER INSERT ON reconciliation_match_decision DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION reconciliation_require_reviewed_timing();

CREATE FUNCTION reconciliation_reject_opening_allocation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF EXISTS(SELECT 1 FROM gl_journal_line l JOIN gl_journal j ON j.id=l.journal_id WHERE l.id=NEW.journal_line_id AND j.source_type='OPENING')
 THEN RAISE EXCEPTION 'Imported opening balances are not statement cash movements'; END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER recon_nonopening_allocation BEFORE INSERT ON reconciliation_allocation FOR EACH ROW EXECUTE FUNCTION reconciliation_reject_opening_allocation();
