-- Retain bounded source bytes separately from registries. Legacy imports are never backfilled.
CREATE TABLE reconciliation_statement_file (
 statement_id uuid PRIMARY KEY REFERENCES reconciliation_statement(id),
 origin varchar(10) NOT NULL CHECK(origin IN('TEXT','UPLOAD')),
 content text NOT NULL CHECK(octet_length(content)<=1000000),
 no_movement boolean NOT NULL,recorded_at timestamptz NOT NULL
);
CREATE TRIGGER recon_file_immutable BEFORE UPDATE OR DELETE ON reconciliation_statement_file FOR EACH ROW EXECUTE FUNCTION reconciliation_immutable();
CREATE FUNCTION reconciliation_file_source_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE s reconciliation_statement;
BEGIN
 SELECT * INTO s FROM reconciliation_statement WHERE id=NEW.statement_id;
 IF s.id IS NULL OR NOT EXISTS(SELECT 1 FROM reconciliation_statement WHERE id=NEW.statement_id AND xmin=pg_current_xact_id()::text::xid)
 OR encode(sha256(convert_to(NEW.content,'UTF8')),'hex')<>s.file_checksum
 THEN RAISE EXCEPTION 'Statement file must be retained atomically with its exact immutable checksum'; END IF;
 IF NEW.no_movement AND (NEW.origin<>'UPLOAD' OR btrim(NEW.content,E'\r\n')<>'date,reference,amount,kind'
 OR NOT EXISTS(SELECT 1 FROM reconciliation_format f WHERE f.id=s.format_id AND f.sacco_id=s.sacco_id AND f.checker_id<>f.maker_id AND f.approval_evidence IS NOT NULL AND f.approved_at IS NOT NULL)
 OR s.opening_balance<>s.closing_balance
 OR NOT EXISTS(SELECT 1 FROM accounting_period WHERE sacco_id=s.sacco_id AND starts_on=s.starts_on AND ends_on=s.ends_on AND state='OPEN'))
 THEN RAISE EXCEPTION 'Zero movement requires an uploaded header, equal balances and exact open reporting period'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER recon_file_source_guard BEFORE INSERT ON reconciliation_statement_file FOR EACH ROW EXECUTE FUNCTION reconciliation_file_source_guard();
CREATE OR REPLACE FUNCTION reconciliation_validate_statement() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE total numeric; row_count bigint; zero_file boolean;
BEGIN
 IF NOT EXISTS(SELECT 1 FROM reconciliation_format f WHERE f.id=NEW.format_id AND f.sacco_id=NEW.sacco_id AND f.checker_id IS NOT NULL)
 OR NOT EXISTS(SELECT 1 FROM gl_account a WHERE a.id=NEW.account_id AND a.sacco_id=NEW.sacco_id AND a.purpose IN('BANK','MOBILE_MONEY') AND a.active)
 OR NOT EXISTS(SELECT 1 FROM members WHERE id=NEW.maker_id AND sacco_id=NEW.sacco_id AND station_id=NEW.station_id)
 THEN RAISE EXCEPTION 'Reviewed format and scoped money account required'; END IF;
 SELECT coalesce(sum(amount),0),count(*) INTO total,row_count FROM reconciliation_statement_line WHERE statement_id=NEW.id;
 SELECT coalesce(bool_or(no_movement),false) INTO zero_file FROM reconciliation_statement_file WHERE statement_id=NEW.id;
 IF NEW.opening_balance+total<>NEW.closing_balance OR (row_count=0 AND NOT zero_file) OR (row_count<>0 AND zero_file)
 OR EXISTS(SELECT 1 FROM reconciliation_statement_line WHERE statement_id=NEW.id AND effective_date NOT BETWEEN NEW.starts_on AND NEW.ends_on)
 THEN RAISE EXCEPTION 'Statement dates and opening/movement/closing must reconcile'; END IF;
 RETURN NEW;
END $$;
CREATE TABLE reconciliation_certificate_statement (
 certificate_id uuid PRIMARY KEY REFERENCES reconciliation_certificate(id),
 statement_id uuid NOT NULL REFERENCES reconciliation_statement(id),recorded_at timestamptz NOT NULL
);
CREATE INDEX ix_recon_certificate_statement ON reconciliation_certificate_statement(statement_id);
CREATE TRIGGER recon_certificate_statement_immutable BEFORE UPDATE OR DELETE ON reconciliation_certificate_statement FOR EACH ROW EXECUTE FUNCTION reconciliation_immutable();
CREATE FUNCTION reconciliation_certificate_statement_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE c reconciliation_certificate; s reconciliation_statement;
BEGIN
 SELECT * INTO c FROM reconciliation_certificate WHERE id=NEW.certificate_id;
 SELECT * INTO s FROM reconciliation_statement WHERE id=NEW.statement_id;
 IF c.id IS NULL OR s.id IS NULL OR c.kind<>'STATEMENT'
 OR ROW(c.sacco_id,c.station_id,c.account_id,c.as_of,c.source_balance) IS DISTINCT FROM ROW(s.sacco_id,s.station_id,s.account_id,s.ends_on,s.closing_balance)
 OR NOT EXISTS(SELECT 1 FROM reconciliation_certificate WHERE id=c.id AND xmin=pg_current_xact_id()::text::xid)
 THEN RAISE EXCEPTION 'Certificate must atomically retain its exact scoped statement source'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER recon_certificate_statement_guard BEFORE INSERT ON reconciliation_certificate_statement FOR EACH ROW EXECUTE FUNCTION reconciliation_certificate_statement_guard();
CREATE FUNCTION reconciliation_review_statement_file() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE c reconciliation_certificate; s reconciliation_statement;
BEGIN
 SELECT * INTO c FROM reconciliation_certificate WHERE id=NEW.certificate_id;
 IF c.kind='STATEMENT' THEN
   SELECT st.* INTO s FROM reconciliation_certificate_statement link JOIN reconciliation_statement st ON st.id=link.statement_id WHERE link.certificate_id=c.id;
   IF s.id IS NULL OR s.maker_id=NEW.checker_id THEN RAISE EXCEPTION 'Statement certificate requires retained source and independent source reviewer'; END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER recon_review_statement_file BEFORE INSERT ON reconciliation_certificate_decision FOR EACH ROW EXECUTE FUNCTION reconciliation_review_statement_file();

-- An approved parser definition is retained along with the source files it governs.
CREATE FUNCTION reconciliation_format_review_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF OLD.checker_id IS NOT NULL THEN RAISE EXCEPTION 'Approved statement format evidence is append-only'; END IF;
 IF TG_OP='DELETE' THEN RETURN OLD; END IF;
 IF NEW.checker_id IS NOT NULL AND (NEW.checker_id=NEW.maker_id OR NEW.approval_evidence IS NULL OR NEW.approved_at IS NULL
 OR NOT EXISTS(SELECT 1 FROM members WHERE id=NEW.checker_id AND sacco_id=NEW.sacco_id))
 THEN RAISE EXCEPTION 'Statement format requires scoped independent approval evidence'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER recon_format_review_guard BEFORE UPDATE OR DELETE ON reconciliation_format FOR EACH ROW EXECUTE FUNCTION reconciliation_format_review_guard();
