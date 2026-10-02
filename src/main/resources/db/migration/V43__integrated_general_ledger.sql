-- No opening balances or chart definitions are seeded. Approved evidence is required.
CREATE TABLE gl_account (
 id uuid PRIMARY KEY, sacco_id varchar(255) NOT NULL, code varchar(40) NOT NULL,
 name varchar(160) NOT NULL, type varchar(20) NOT NULL CHECK(type IN ('ASSET','LIABILITY','EQUITY','INCOME','EXPENSE')),
 normal_balance varchar(6) NOT NULL CHECK(normal_balance IN ('DEBIT','CREDIT')),
 kind varchar(10) NOT NULL CHECK(kind IN ('HEADING','POSTING','CONTROL')),
 purpose varchar(40) NOT NULL, parent_id uuid, active boolean NOT NULL DEFAULT true,
 maker_id uuid NOT NULL REFERENCES members(id), created_at timestamptz NOT NULL,
 UNIQUE(sacco_id,code), UNIQUE(id,sacco_id),
 FOREIGN KEY(parent_id,sacco_id) REFERENCES gl_account(id,sacco_id), CHECK(parent_id IS DISTINCT FROM id)
);
CREATE TABLE accounting_period (
 id uuid PRIMARY KEY, sacco_id varchar(255) NOT NULL, starts_on date NOT NULL, ends_on date NOT NULL,
 state varchar(10) NOT NULL CHECK(state IN ('OPEN','CLOSED')), policy_id uuid NOT NULL REFERENCES accounting_policies(id),
 created_by uuid NOT NULL REFERENCES members(id), created_at timestamptz NOT NULL,
 closed_by uuid REFERENCES members(id), closed_at timestamptz,
 CHECK(ends_on>=starts_on), UNIQUE(id,sacco_id), UNIQUE(sacco_id,starts_on,ends_on)
);
CREATE INDEX ix_accounting_period_dates ON accounting_period(sacco_id,starts_on,ends_on);
CREATE TABLE gl_journal (
 id uuid PRIMARY KEY, sacco_id varchar(255) NOT NULL, station_id varchar(255) NOT NULL,
 policy_id uuid NOT NULL REFERENCES accounting_policies(id), policy_version integer NOT NULL CHECK(policy_version>0),
 period_id uuid NOT NULL, source_type varchar(40) NOT NULL, source_reference varchar(160) NOT NULL,
 request_key uuid NOT NULL, payload_hash varchar(64) NOT NULL, currency varchar(3) NOT NULL CHECK(currency='TZS'),
 state varchar(10) NOT NULL CHECK(state IN ('DRAFT','APPROVED','POSTED')),
 evidence_reference varchar(500) NOT NULL, approval_evidence_reference varchar(500), reason varchar(500), effective_date date NOT NULL,
 maker_id uuid NOT NULL REFERENCES members(id), checker_id uuid REFERENCES members(id),
 recorded_at timestamptz NOT NULL, checked_at timestamptz, posted_at timestamptz,
 reverses_id uuid UNIQUE, UNIQUE(id,sacco_id,station_id), UNIQUE(sacco_id,station_id,request_key),
 UNIQUE(sacco_id,station_id,source_type,source_reference), FOREIGN KEY(period_id,sacco_id) REFERENCES accounting_period(id,sacco_id),
 FOREIGN KEY(reverses_id,sacco_id,station_id) REFERENCES gl_journal(id,sacco_id,station_id),
 CHECK(checker_id IS NULL OR checker_id<>maker_id), CHECK(state='DRAFT' OR (checker_id IS NOT NULL AND approval_evidence_reference IS NOT NULL AND length(trim(approval_evidence_reference))>0)),
 CHECK(state<>'POSTED' OR posted_at IS NOT NULL), CHECK(reverses_id IS NULL OR reason IS NOT NULL)
);
CREATE INDEX ix_gl_journal_scope_date ON gl_journal(sacco_id,station_id,effective_date,id) WHERE state='POSTED';
CREATE TABLE gl_journal_line (
 id uuid PRIMARY KEY, journal_id uuid NOT NULL, sacco_id varchar(255) NOT NULL, station_id varchar(255) NOT NULL,
 account_id uuid NOT NULL, debit numeric NOT NULL CHECK(debit>=0 AND debit<10000000000000000 AND scale(debit)<=2), credit numeric NOT NULL CHECK(credit>=0 AND credit<10000000000000000 AND scale(credit)<=2),
 CHECK((debit>0 AND credit=0) OR (credit>0 AND debit=0)),
 FOREIGN KEY(journal_id,sacco_id,station_id) REFERENCES gl_journal(id,sacco_id,station_id),
 FOREIGN KEY(account_id,sacco_id) REFERENCES gl_account(id,sacco_id)
);
CREATE INDEX ix_gl_line_account ON gl_journal_line(sacco_id,station_id,account_id,journal_id);
CREATE TABLE accounting_outbox (
 id uuid PRIMARY KEY, journal_id uuid NOT NULL UNIQUE REFERENCES gl_journal(id), sacco_id varchar(255) NOT NULL,
 station_id varchar(255) NOT NULL, event_type varchar(40) NOT NULL, created_at timestamptz NOT NULL,
 state varchar(15) NOT NULL DEFAULT 'PENDING' CHECK(state IN ('PENDING','ACKNOWLEDGED','FAILED')),
 attempts integer NOT NULL DEFAULT 0 CHECK(attempts>=0), acknowledged_at timestamptz, error_code varchar(80)
);
CREATE TABLE gl_cutover_coverage (
 id uuid PRIMARY KEY, sacco_id varchar(255) NOT NULL, station_id varchar(255) NOT NULL,
 opening_journal_id uuid NOT NULL UNIQUE, evidence_reference varchar(500) NOT NULL, reconciled_through date NOT NULL,
 maker_id uuid NOT NULL REFERENCES members(id), checker_id uuid NOT NULL REFERENCES members(id), recorded_at timestamptz NOT NULL,
 complete boolean NOT NULL, CHECK(maker_id<>checker_id),
 FOREIGN KEY(opening_journal_id,sacco_id,station_id) REFERENCES gl_journal(id,sacco_id,station_id),
 UNIQUE(sacco_id,station_id)
);
CREATE TABLE gl_operational_bridge (
 voucher_id uuid PRIMARY KEY, sacco_id varchar(255) NOT NULL, station_id varchar(255) NOT NULL,
 journal_id uuid NOT NULL UNIQUE, evidence_reference varchar(500) NOT NULL,
 FOREIGN KEY(journal_id,sacco_id,station_id) REFERENCES gl_journal(id,sacco_id,station_id)
);
CREATE FUNCTION gl_protect_account() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Deactivate accounts; deletion is prohibited'; END IF;
 IF ROW(NEW.sacco_id,NEW.code,NEW.type,NEW.normal_balance,NEW.kind,NEW.purpose,NEW.parent_id)
  IS DISTINCT FROM ROW(OLD.sacco_id,OLD.code,OLD.type,OLD.normal_balance,OLD.kind,OLD.purpose,OLD.parent_id) THEN
  RAISE EXCEPTION 'Account classification and hierarchy are immutable'; END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER gl_account_protection BEFORE UPDATE OR DELETE ON gl_account FOR EACH ROW EXECUTE FUNCTION gl_protect_account();
CREATE FUNCTION gl_protect_journal() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' OR OLD.state='POSTED' THEN RAISE EXCEPTION 'Journal history is immutable'; END IF;
 IF ROW(NEW.id,NEW.sacco_id,NEW.station_id,NEW.policy_id,NEW.policy_version,NEW.period_id,NEW.source_type,NEW.source_reference,
  NEW.request_key,NEW.payload_hash,NEW.currency,NEW.evidence_reference,NEW.reason,NEW.effective_date,NEW.maker_id,NEW.recorded_at,NEW.reverses_id)
 IS DISTINCT FROM ROW(OLD.id,OLD.sacco_id,OLD.station_id,OLD.policy_id,OLD.policy_version,OLD.period_id,OLD.source_type,OLD.source_reference,
  OLD.request_key,OLD.payload_hash,OLD.currency,OLD.evidence_reference,OLD.reason,OLD.effective_date,OLD.maker_id,OLD.recorded_at,OLD.reverses_id) THEN
 RAISE EXCEPTION 'Journal payload is immutable'; END IF;
 IF NOT ((OLD.state='DRAFT' AND NEW.state='APPROVED') OR (OLD.state='APPROVED' AND NEW.state='POSTED')) THEN
 RAISE EXCEPTION 'Invalid journal transition'; END IF;
 IF NEW.state='POSTED' AND (NEW.checker_id<>OLD.checker_id OR NEW.checked_at<>OLD.checked_at OR NEW.approval_evidence_reference<>OLD.approval_evidence_reference) THEN
 RAISE EXCEPTION 'Journal approval is immutable'; END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER gl_journal_protection BEFORE UPDATE OR DELETE ON gl_journal FOR EACH ROW EXECUTE FUNCTION gl_protect_journal();
CREATE FUNCTION gl_protect_lines() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE j gl_journal; a gl_account;
BEGIN
 IF TG_OP<>'INSERT' THEN RAISE EXCEPTION 'Journal lines are append-only'; END IF;
 SELECT * INTO j FROM gl_journal WHERE id=NEW.journal_id FOR UPDATE;
 SELECT * INTO a FROM gl_account WHERE id=NEW.account_id;
 IF j.state<>'DRAFT' OR NOT a.active OR a.kind='HEADING' THEN RAISE EXCEPTION 'Invalid journal account or state'; END IF;
 IF j.source_type='MANUAL' AND a.kind='CONTROL' THEN RAISE EXCEPTION 'Manual journals cannot bypass controls'; END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER gl_line_protection BEFORE INSERT OR UPDATE OR DELETE ON gl_journal_line FOR EACH ROW EXECUTE FUNCTION gl_protect_lines();
CREATE FUNCTION gl_validate_post() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE p accounting_period; cnt integer; difference numeric; original gl_journal; opening date; posting_event text; account_row gl_account;
BEGIN
 IF NEW.state<>'POSTED' THEN RETURN NEW; END IF;
 SELECT * INTO p FROM accounting_period WHERE id=NEW.period_id FOR SHARE;
 IF p.state<>'OPEN' OR NEW.effective_date NOT BETWEEN p.starts_on AND p.ends_on THEN RAISE EXCEPTION 'Accounting period is closed or invalid'; END IF;
 IF NOT EXISTS(SELECT 1 FROM accounting_policies ap JOIN accounting_policy_approvals aa ON aa.policy_id=ap.id
 WHERE ap.id=NEW.policy_id AND ap.sacco_id=NEW.sacco_id AND aa.sacco_id=NEW.sacco_id AND aa.decision='APPROVED'
 AND ap.authoritative_ledger='LOCAL_GL' AND ap.effective_from<=NEW.effective_date AND ap.policy_version=NEW.policy_version) THEN
 RAISE EXCEPTION 'Approved local accounting policy is required'; END IF;
 SELECT ap.opening_date INTO opening FROM accounting_policies ap WHERE ap.id=NEW.policy_id;
 posting_event := CASE NEW.source_type WHEN 'OPENING' THEN 'OPENING_BALANCE' WHEN 'MANUAL' THEN 'MANUAL_JOURNAL' ELSE NEW.source_type END;
 IF NOT EXISTS(SELECT 1 FROM accounting_policies ap WHERE ap.id=NEW.policy_id AND ap.posting_matrix_json::jsonb->posting_event->>'permission'='ALLOWED') THEN
 RAISE EXCEPTION 'Posting event is disabled by policy'; END IF;
 IF NEW.source_type='OPENING' THEN
 IF NEW.effective_date<>opening OR NOT EXISTS(SELECT 1 FROM gl_cutover_coverage c WHERE c.opening_journal_id=NEW.id AND c.sacco_id=NEW.sacco_id AND c.station_id=NEW.station_id AND c.complete AND c.maker_id=NEW.maker_id AND c.checker_id=NEW.checker_id) THEN
 RAISE EXCEPTION 'Independently reconciled opening evidence is required'; END IF;
 ELSE
 IF NEW.effective_date<=opening OR NOT EXISTS(SELECT 1 FROM gl_cutover_coverage c JOIN gl_journal j ON j.id=c.opening_journal_id WHERE c.sacco_id=NEW.sacco_id AND c.station_id=NEW.station_id AND c.complete AND j.state='POSTED') THEN
 RAISE EXCEPTION 'Reviewed opening boundary is required'; END IF;
 END IF;
 FOR account_row IN SELECT a.* FROM gl_account a JOIN gl_journal_line l ON l.account_id=a.id WHERE l.journal_id=NEW.id FOR SHARE OF a LOOP
 IF NOT account_row.active OR account_row.kind='HEADING' OR (NEW.source_type='MANUAL' AND account_row.kind='CONTROL') THEN
 RAISE EXCEPTION 'Active valid posting accounts are required'; END IF;
 END LOOP;
 SELECT count(*),sum(debit-credit) INTO cnt,difference FROM gl_journal_line WHERE journal_id=NEW.id;
 IF cnt<2 OR difference<>0 THEN RAISE EXCEPTION 'Journal must contain balanced lines'; END IF;
 IF NEW.reverses_id IS NOT NULL THEN
 SELECT * INTO original FROM gl_journal WHERE id=NEW.reverses_id FOR UPDATE;
 IF original.state<>'POSTED' OR NEW.maker_id IN(original.maker_id,original.checker_id) OR NEW.checker_id IN(original.maker_id,original.checker_id)
 THEN RAISE EXCEPTION 'Independent reversal is required'; END IF;
 IF EXISTS((SELECT account_id,sum(debit) debit,sum(credit) credit FROM gl_journal_line WHERE journal_id=NEW.id GROUP BY account_id)
 EXCEPT (SELECT account_id,sum(credit),sum(debit) FROM gl_journal_line WHERE journal_id=NEW.reverses_id GROUP BY account_id))
 OR EXISTS((SELECT account_id,sum(credit) debit,sum(debit) credit FROM gl_journal_line WHERE journal_id=NEW.reverses_id GROUP BY account_id)
 EXCEPT(SELECT account_id,sum(debit),sum(credit) FROM gl_journal_line WHERE journal_id=NEW.id GROUP BY account_id))
 THEN RAISE EXCEPTION 'Reversal must exactly reverse original lines'; END IF;
 END IF;
 IF NOT EXISTS(SELECT 1 FROM accounting_outbox WHERE journal_id=NEW.id AND sacco_id=NEW.sacco_id AND station_id=NEW.station_id)
 THEN RAISE EXCEPTION 'Transactional accounting outbox is required'; END IF;
 RETURN NEW;
END; $$;
CREATE CONSTRAINT TRIGGER gl_post_validation AFTER INSERT OR UPDATE ON gl_journal DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION gl_validate_post();
CREATE FUNCTION gl_protect_evidence() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Cutover and bridge evidence is append-only'; END; $$;
CREATE TRIGGER gl_cutover_immutable BEFORE UPDATE OR DELETE ON gl_cutover_coverage FOR EACH ROW EXECUTE FUNCTION gl_protect_evidence();
CREATE TRIGGER gl_bridge_immutable BEFORE UPDATE OR DELETE ON gl_operational_bridge FOR EACH ROW EXECUTE FUNCTION gl_protect_evidence();

CREATE FUNCTION gl_validate_account_mappings() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE p accounting_policies; mapping record;
BEGIN
 IF NEW.decision<>'APPROVED' THEN RETURN NEW; END IF;
 SELECT * INTO p FROM accounting_policies WHERE id=NEW.policy_id;
 IF p.authoritative_ledger<>'LOCAL_GL' THEN RETURN NEW; END IF;
 FOR mapping IN SELECT * FROM jsonb_each_text(p.account_mappings_json::jsonb) LOOP
 IF NOT EXISTS(SELECT 1 FROM gl_account WHERE id=mapping.value::uuid AND sacco_id=p.sacco_id AND active AND kind<>'HEADING') THEN
 RAISE EXCEPTION 'Approved account mapping must reference an active institution posting account'; END IF;
 END LOOP;
 RETURN NEW;
END; $$;
CREATE CONSTRAINT TRIGGER gl_policy_account_mapping AFTER INSERT ON accounting_policy_approvals
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION gl_validate_account_mappings();
CREATE FUNCTION gl_validate_scope() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM members WHERE id=NEW.maker_id AND sacco_id=NEW.sacco_id AND station_id=NEW.station_id)
 OR (NEW.checker_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM members WHERE id=NEW.checker_id AND sacco_id=NEW.sacco_id AND station_id=NEW.station_id)) THEN
 RAISE EXCEPTION 'Journal staff must belong to its historical institution and branch'; END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER gl_journal_scope BEFORE INSERT OR UPDATE ON gl_journal FOR EACH ROW EXECUTE FUNCTION gl_validate_scope();
CREATE FUNCTION gl_validate_period() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Periods cannot be deleted'; END IF;
 IF TG_OP='UPDATE' THEN
 IF ROW(NEW.id,NEW.sacco_id,NEW.starts_on,NEW.ends_on,NEW.policy_id,NEW.created_by,NEW.created_at)
 IS DISTINCT FROM ROW(OLD.id,OLD.sacco_id,OLD.starts_on,OLD.ends_on,OLD.policy_id,OLD.created_by,OLD.created_at) THEN
 RAISE EXCEPTION 'Period definition is immutable'; END IF;
 ELSE
 PERFORM pg_advisory_xact_lock(hashtextextended('GL_SETUP/' || NEW.sacco_id,0));
 IF EXISTS(SELECT 1 FROM accounting_period WHERE sacco_id=NEW.sacco_id AND starts_on<=NEW.ends_on AND ends_on>=NEW.starts_on) THEN
 RAISE EXCEPTION 'Accounting periods cannot overlap'; END IF;
 END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER gl_period_protection BEFORE INSERT OR UPDATE OR DELETE ON accounting_period FOR EACH ROW EXECUTE FUNCTION gl_validate_period();
