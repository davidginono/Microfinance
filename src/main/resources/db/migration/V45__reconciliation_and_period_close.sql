-- Evidence-only import. No statement creates a journal, receipt, or loan allocation.
CREATE TABLE reconciliation_format (
 id uuid PRIMARY KEY,sacco_id varchar(255) NOT NULL,name varchar(120) NOT NULL,
 version varchar(30) NOT NULL CHECK(version='TZS_CSV_V1'),maker_id uuid NOT NULL REFERENCES members(id),
 checker_id uuid REFERENCES members(id),evidence varchar(500) NOT NULL,approval_evidence varchar(500),approved_at timestamptz,
 CHECK(checker_id IS NULL OR checker_id<>maker_id),UNIQUE(id,sacco_id)
);
CREATE TABLE reconciliation_statement (
 id uuid PRIMARY KEY,sacco_id varchar(255) NOT NULL,station_id varchar(255) NOT NULL,
 account_id uuid NOT NULL,format_id uuid NOT NULL,request_key uuid NOT NULL,payload_hash varchar(64) NOT NULL,
 starts_on date NOT NULL,ends_on date NOT NULL,opening_balance numeric(18,2) NOT NULL,closing_balance numeric(18,2) NOT NULL,
 filename varchar(160) NOT NULL,file_checksum varchar(64) NOT NULL,evidence varchar(500) NOT NULL,
 maker_id uuid NOT NULL REFERENCES members(id),imported_at timestamptz NOT NULL,
 CHECK(ends_on>=starts_on),FOREIGN KEY(account_id,sacco_id) REFERENCES gl_account(id,sacco_id),
 FOREIGN KEY(format_id,sacco_id) REFERENCES reconciliation_format(id,sacco_id),UNIQUE(id,sacco_id,station_id),
 UNIQUE(sacco_id,station_id,request_key),UNIQUE(sacco_id,station_id,account_id,file_checksum,starts_on,ends_on)
);
CREATE TABLE reconciliation_statement_line (
 id uuid PRIMARY KEY,statement_id uuid NOT NULL REFERENCES reconciliation_statement(id),row_number integer NOT NULL CHECK(row_number>0),
 effective_date date NOT NULL,reference varchar(160) NOT NULL,amount numeric NOT NULL CHECK(amount<>0 AND abs(amount)<10000000000000000 AND scale(amount)<=2),
 kind varchar(20) NOT NULL CHECK(kind IN('RECEIPT','DISBURSEMENT','TRANSFER','SETTLEMENT','CHARGE','REVERSAL')),duplicate boolean NOT NULL,
 UNIQUE(statement_id,row_number)
);
CREATE INDEX ix_recon_statement_scope ON reconciliation_statement(sacco_id,station_id,ends_on,id);
CREATE INDEX ix_recon_statement_lines ON reconciliation_statement_line(statement_id,row_number);
CREATE TABLE reconciliation_match (
 id uuid PRIMARY KEY,sacco_id varchar(255) NOT NULL,station_id varchar(255) NOT NULL,account_id uuid NOT NULL,
 kind varchar(20) NOT NULL CHECK(kind IN('EXACT','SPLIT','BATCH','SETTLEMENT','CHARGE','REVERSAL')),
 maker_id uuid NOT NULL REFERENCES members(id),evidence varchar(500) NOT NULL,created_at timestamptz NOT NULL,
 reverses_id uuid UNIQUE REFERENCES reconciliation_match(id),FOREIGN KEY(account_id,sacco_id) REFERENCES gl_account(id,sacco_id)
);
CREATE INDEX ix_recon_match_scope ON reconciliation_match(sacco_id,station_id,account_id,id);
CREATE TABLE reconciliation_allocation (
 match_id uuid NOT NULL REFERENCES reconciliation_match(id),statement_line_id uuid NOT NULL REFERENCES reconciliation_statement_line(id),
 journal_line_id uuid NOT NULL REFERENCES gl_journal_line(id),amount numeric NOT NULL CHECK(amount>0 AND amount<10000000000000000 AND scale(amount)<=2),
 PRIMARY KEY(match_id,statement_line_id,journal_line_id)
);
CREATE INDEX ix_recon_allocation_statement ON reconciliation_allocation(statement_line_id,match_id);
CREATE INDEX ix_recon_allocation_journal ON reconciliation_allocation(journal_line_id,match_id);
CREATE TABLE reconciliation_match_decision (
 match_id uuid PRIMARY KEY REFERENCES reconciliation_match(id),checker_id uuid NOT NULL REFERENCES members(id),
 decision varchar(10) NOT NULL CHECK(decision IN('APPROVED','REJECTED')),evidence varchar(500) NOT NULL,decided_at timestamptz NOT NULL
);
CREATE VIEW reconciliation_active_allocation AS
 SELECT a.* FROM reconciliation_allocation a JOIN reconciliation_match m ON m.id=a.match_id
 JOIN reconciliation_match_decision d ON d.match_id=m.id AND d.decision='APPROVED'
 WHERE m.reverses_id IS NULL AND NOT EXISTS(SELECT 1 FROM reconciliation_match r
 JOIN reconciliation_match_decision rd ON rd.match_id=r.id AND rd.decision='APPROVED' WHERE r.reverses_id=m.id);
CREATE TABLE reconciliation_exception (
 id uuid PRIMARY KEY,sacco_id varchar(255) NOT NULL,station_id varchar(255) NOT NULL,statement_line_id uuid NOT NULL REFERENCES reconciliation_statement_line(id),
 kind varchar(20) NOT NULL CHECK(kind IN('UNMATCHED','DUPLICATE','PARTIAL','REVERSED','TIMING','CHANNEL_FEE')),
 assigned_to uuid NOT NULL REFERENCES members(id),maker_id uuid NOT NULL REFERENCES members(id),evidence varchar(500) NOT NULL,created_at timestamptz NOT NULL
);
CREATE INDEX ix_recon_exception_scope ON reconciliation_exception(sacco_id,station_id,statement_line_id,id);
CREATE TABLE reconciliation_exception_decision (
 exception_id uuid PRIMARY KEY REFERENCES reconciliation_exception(id),checker_id uuid NOT NULL REFERENCES members(id),
 evidence varchar(500) NOT NULL,decided_at timestamptz NOT NULL
);
CREATE TABLE reconciliation_certificate (
 id uuid PRIMARY KEY,sacco_id varchar(255) NOT NULL,station_id varchar(255) NOT NULL,account_id uuid NOT NULL,
 as_of date NOT NULL,kind varchar(20) NOT NULL CHECK(kind IN('PHYSICAL_CASH','STATEMENT','CLEARING','LOAN_CONTROL','SUPPLIER','FUNDING','OPENING')),
 source_balance numeric(18,2) NOT NULL,ledger_balance numeric(18,2) NOT NULL,difference numeric(18,2) NOT NULL,
 evidence varchar(500) NOT NULL,maker_id uuid NOT NULL REFERENCES members(id),created_at timestamptz NOT NULL,
 FOREIGN KEY(account_id,sacco_id) REFERENCES gl_account(id,sacco_id),CHECK(difference=source_balance-ledger_balance)
);
CREATE INDEX ix_recon_cert_scope ON reconciliation_certificate(sacco_id,station_id,as_of,account_id);
CREATE TABLE reconciliation_certificate_decision (
 certificate_id uuid PRIMARY KEY REFERENCES reconciliation_certificate(id),checker_id uuid NOT NULL REFERENCES members(id),
 evidence varchar(500) NOT NULL,decided_at timestamptz NOT NULL
);
CREATE TABLE accounting_close_review (
 id uuid PRIMARY KEY,sacco_id varchar(255) NOT NULL,station_id varchar(255) NOT NULL,period_id uuid NOT NULL,
 version integer NOT NULL CHECK(version>0),action varchar(10) NOT NULL CHECK(action IN('CLOSE','REOPEN')),
 snapshot_json text NOT NULL,checksum varchar(64) NOT NULL,evidence varchar(500) NOT NULL,
 maker_id uuid NOT NULL REFERENCES members(id),recorded_at timestamptz NOT NULL,
 FOREIGN KEY(period_id,sacco_id) REFERENCES accounting_period(id,sacco_id),UNIQUE(sacco_id,station_id,period_id,version)
);
CREATE INDEX ix_close_scope ON accounting_close_review(sacco_id,station_id,period_id,version);
CREATE TABLE accounting_close_decision (
 review_id uuid PRIMARY KEY REFERENCES accounting_close_review(id),checker_id uuid NOT NULL REFERENCES members(id),
 evidence varchar(500) NOT NULL,decided_at timestamptz NOT NULL
);
CREATE FUNCTION reconciliation_immutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Reconciliation evidence and closing history are append-only'; END; $$;
DO $$ DECLARE t text; BEGIN FOREACH t IN ARRAY ARRAY['reconciliation_statement','reconciliation_statement_line','reconciliation_match',
 'reconciliation_allocation','reconciliation_match_decision','reconciliation_exception','reconciliation_exception_decision',
 'reconciliation_certificate','reconciliation_certificate_decision','accounting_close_review','accounting_close_decision'] LOOP
 EXECUTE format('CREATE TRIGGER %I BEFORE UPDATE OR DELETE ON %I FOR EACH ROW EXECUTE FUNCTION reconciliation_immutable()',t||'_immutable',t);
 END LOOP; END; $$;
CREATE FUNCTION reconciliation_validate_decision() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE maker uuid; institution text; branch text;
BEGIN
 IF TG_TABLE_NAME='reconciliation_match_decision' THEN SELECT maker_id,sacco_id,station_id INTO maker,institution,branch FROM reconciliation_match WHERE id=NEW.match_id;
 ELSIF TG_TABLE_NAME='reconciliation_exception_decision' THEN SELECT maker_id,sacco_id,station_id INTO maker,institution,branch FROM reconciliation_exception WHERE id=NEW.exception_id;
 ELSIF TG_TABLE_NAME='reconciliation_certificate_decision' THEN SELECT maker_id,sacco_id,station_id INTO maker,institution,branch FROM reconciliation_certificate WHERE id=NEW.certificate_id;
 ELSE SELECT maker_id,sacco_id,station_id INTO maker,institution,branch FROM accounting_close_review WHERE id=NEW.review_id;
 END IF;
 IF maker=NEW.checker_id OR NOT EXISTS(SELECT 1 FROM members WHERE id=NEW.checker_id AND sacco_id=institution AND station_id=branch)
 THEN RAISE EXCEPTION 'Independent in-scope reconciliation review required'; END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER recon_match_review BEFORE INSERT ON reconciliation_match_decision FOR EACH ROW EXECUTE FUNCTION reconciliation_validate_decision();
CREATE TRIGGER recon_exception_review BEFORE INSERT ON reconciliation_exception_decision FOR EACH ROW EXECUTE FUNCTION reconciliation_validate_decision();
CREATE TRIGGER recon_certificate_review BEFORE INSERT ON reconciliation_certificate_decision FOR EACH ROW EXECUTE FUNCTION reconciliation_validate_decision();
CREATE TRIGGER accounting_close_review_check BEFORE INSERT ON accounting_close_decision FOR EACH ROW EXECUTE FUNCTION reconciliation_validate_decision();
CREATE FUNCTION reconciliation_protect_format() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' OR OLD.checker_id IS NOT NULL OR NEW.checker_id IS NULL OR NEW.checker_id=OLD.maker_id
 OR ROW(NEW.id,NEW.sacco_id,NEW.name,NEW.version,NEW.maker_id,NEW.evidence) IS DISTINCT FROM ROW(OLD.id,OLD.sacco_id,OLD.name,OLD.version,OLD.maker_id,OLD.evidence)
 THEN RAISE EXCEPTION 'Approved statement format is immutable'; END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER recon_format_immutable BEFORE UPDATE OR DELETE ON reconciliation_format FOR EACH ROW EXECUTE FUNCTION reconciliation_protect_format();
CREATE FUNCTION reconciliation_validate_statement() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE total numeric;
BEGIN
 IF NOT EXISTS(SELECT 1 FROM reconciliation_format f WHERE f.id=NEW.format_id AND f.sacco_id=NEW.sacco_id AND f.checker_id IS NOT NULL)
 OR NOT EXISTS(SELECT 1 FROM gl_account a WHERE a.id=NEW.account_id AND a.sacco_id=NEW.sacco_id AND a.purpose IN('BANK','MOBILE_MONEY') AND a.active)
 OR NOT EXISTS(SELECT 1 FROM members WHERE id=NEW.maker_id AND sacco_id=NEW.sacco_id AND station_id=NEW.station_id)
 THEN RAISE EXCEPTION 'Reviewed format and scoped money account required'; END IF;
 SELECT coalesce(sum(amount),0) INTO total FROM reconciliation_statement_line WHERE statement_id=NEW.id;
 IF NEW.opening_balance+total<>NEW.closing_balance OR NOT EXISTS(SELECT 1 FROM reconciliation_statement_line WHERE statement_id=NEW.id)
 OR EXISTS(SELECT 1 FROM reconciliation_statement_line WHERE statement_id=NEW.id AND effective_date NOT BETWEEN NEW.starts_on AND NEW.ends_on)
 THEN RAISE EXCEPTION 'Statement dates and opening/movement/closing must reconcile'; END IF;
 RETURN NEW;
END; $$;
CREATE CONSTRAINT TRIGGER recon_statement_validation AFTER INSERT ON reconciliation_statement DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION reconciliation_validate_statement();
CREATE FUNCTION reconciliation_protect_import_rows() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM reconciliation_statement WHERE id=NEW.statement_id AND xmin=pg_current_xact_id()::text::xid)
 THEN RAISE EXCEPTION 'Statement rows must be inserted atomically with their immutable import'; END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER recon_import_row_transaction BEFORE INSERT ON reconciliation_statement_line FOR EACH ROW EXECUTE FUNCTION reconciliation_protect_import_rows();
CREATE FUNCTION reconciliation_validate_allocation() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE m reconciliation_match; s reconciliation_statement; sl reconciliation_statement_line; jl gl_journal_line; j gl_journal;
BEGIN
 SELECT * INTO m FROM reconciliation_match WHERE id=NEW.match_id;
 IF m.reverses_id IS NOT NULL OR EXISTS(SELECT 1 FROM reconciliation_match_decision WHERE match_id=m.id) THEN RAISE EXCEPTION 'Cannot add allocations after review or to reversal'; END IF;
 SELECT * INTO sl FROM reconciliation_statement_line WHERE id=NEW.statement_line_id;
 SELECT * INTO s FROM reconciliation_statement WHERE id=sl.statement_id;
 SELECT * INTO jl FROM gl_journal_line WHERE id=NEW.journal_line_id;
 SELECT * INTO j FROM gl_journal WHERE id=jl.journal_id;
 IF ROW(m.sacco_id,m.station_id,m.account_id) IS DISTINCT FROM ROW(s.sacco_id,s.station_id,s.account_id)
 OR ROW(m.sacco_id,m.station_id,m.account_id) IS DISTINCT FROM ROW(j.sacco_id,j.station_id,jl.account_id)
 OR j.state<>'POSTED' OR sign(sl.amount)<>sign(jl.debit-jl.credit)
 THEN RAISE EXCEPTION 'Match must use scoped posted account lines with equal directions'; END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER recon_allocation_validation BEFORE INSERT ON reconciliation_allocation FOR EACH ROW EXECUTE FUNCTION reconciliation_validate_allocation();
CREATE FUNCTION reconciliation_validate_match() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE m reconciliation_match;
BEGIN
 SELECT * INTO m FROM reconciliation_match WHERE id=NEW.match_id;
 PERFORM id FROM gl_account WHERE id=m.account_id FOR UPDATE;
 IF NEW.decision='APPROVED' AND m.reverses_id IS NULL THEN
 IF NOT EXISTS(SELECT 1 FROM reconciliation_allocation WHERE match_id=m.id)
 OR EXISTS(SELECT 1 FROM reconciliation_statement_line sl JOIN reconciliation_allocation a ON a.statement_line_id=sl.id WHERE a.match_id=m.id
 AND abs(sl.amount)<(SELECT coalesce(sum(x.amount),0) FROM reconciliation_active_allocation x WHERE x.statement_line_id=sl.id))
 OR EXISTS(SELECT 1 FROM gl_journal_line jl JOIN reconciliation_allocation a ON a.journal_line_id=jl.id WHERE a.match_id=m.id
 AND abs(jl.debit-jl.credit)<(SELECT coalesce(sum(x.amount),0) FROM reconciliation_active_allocation x WHERE x.journal_line_id=jl.id))
 THEN RAISE EXCEPTION 'Approved matching cannot overallocate statement or journal lines'; END IF;
 ELSIF NEW.decision='APPROVED' THEN
 IF NOT EXISTS(SELECT 1 FROM reconciliation_match o JOIN reconciliation_match_decision d ON d.match_id=o.id AND d.decision='APPROVED'
 WHERE o.id=m.reverses_id AND o.reverses_id IS NULL AND m.maker_id NOT IN(o.maker_id,d.checker_id) AND NEW.checker_id NOT IN(o.maker_id,d.checker_id))
 THEN RAISE EXCEPTION 'Matching reversal needs independent review of approved original'; END IF;
 END IF;
 RETURN NEW;
END; $$;
CREATE CONSTRAINT TRIGGER recon_match_capacities AFTER INSERT ON reconciliation_match_decision DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION reconciliation_validate_match();
CREATE FUNCTION accounting_validate_period_decision() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.state IS DISTINCT FROM OLD.state THEN
 IF NEW.state='CLOSED' THEN
 IF NEW.closed_by IS NULL OR NEW.closed_at IS NULL OR EXISTS(SELECT 1 FROM sacco_stations b WHERE b.sacco_id=NEW.sacco_id AND b.active
 AND NOT EXISTS(SELECT 1 FROM accounting_close_review r JOIN accounting_close_decision d ON d.review_id=r.id
 WHERE r.period_id=NEW.id AND r.station_id=b.station_id AND r.action='CLOSE'
 AND r.recorded_at>coalesce((SELECT max(rd.decided_at) FROM accounting_close_review rr JOIN accounting_close_decision rd ON rd.review_id=rr.id WHERE rr.period_id=NEW.id AND rr.action='REOPEN'),'-infinity')))
 THEN RAISE EXCEPTION 'All active branches need reviewed closing evidence'; END IF;
 ELSE
 IF NOT EXISTS(SELECT 1 FROM accounting_close_review r JOIN accounting_close_decision d ON d.review_id=r.id
 WHERE r.period_id=NEW.id AND r.action='REOPEN' AND d.decided_at>=OLD.closed_at)
 THEN RAISE EXCEPTION 'Independent reopening decision is required'; END IF;
 END IF;
 END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER accounting_period_decision BEFORE UPDATE ON accounting_period FOR EACH ROW EXECUTE FUNCTION accounting_validate_period_decision();
