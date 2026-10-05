-- No accounts, policies, openings, or approval claims are seeded.
CREATE TABLE accounting_account (
 id uuid PRIMARY KEY, sacco_id varchar(255) NOT NULL REFERENCES registered_saccos(sacco_id),
 code varchar(40) NOT NULL CHECK (code ~ '^[A-Z0-9][A-Z0-9_.-]{0,39}$'), name varchar(160) NOT NULL,
 kind varchar(16) NOT NULL CHECK (kind IN ('ASSET','LIABILITY','EQUITY','INCOME','EXPENSE')),
 normal_balance varchar(8) NOT NULL CHECK (normal_balance IN ('DEBIT','CREDIT')),
 parent_id uuid, usage varchar(12) NOT NULL CHECK (usage IN ('HEADING','POSTING','CONTROL')),
 category varchar(40) NOT NULL CHECK(category IN ('CASH','BANK','MOBILE_MONEY','CLEARING','SUSPENSE','LOAN_PRINCIPAL','INTEREST_RECEIVABLE','FEE_RECEIVABLE','ALLOWANCE','PAYABLE','FUNDING','CAPITAL','INCOME','EXPENSE','FIXED_ASSET','ACCUMULATED_DEPRECIATION','TAX','PREPAYMENT','CUSTOMER_ADVANCE','INTERNAL_TRANSFER','RETAINED_EARNINGS','OTHER')), active boolean NOT NULL,
 created_by uuid NOT NULL REFERENCES members(id), recorded_at timestamptz NOT NULL,
 UNIQUE(sacco_id,code), UNIQUE(sacco_id,id),
 FOREIGN KEY(sacco_id,parent_id) REFERENCES accounting_account(sacco_id,id)
);
CREATE INDEX ix_accounting_accounts_page ON accounting_account(sacco_id,code,id);
CREATE FUNCTION accounting_account_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP = 'DELETE' THEN RAISE EXCEPTION 'Accounts must be deactivated, never deleted'; END IF;
 IF NEW.category<>'OTHER' AND (
   (NEW.category IN ('CASH','BANK','MOBILE_MONEY','LOAN_PRINCIPAL','INTEREST_RECEIVABLE','FEE_RECEIVABLE','FIXED_ASSET','PREPAYMENT') AND (NEW.kind<>'ASSET' OR NEW.normal_balance<>'DEBIT')) OR
   (NEW.category IN ('ALLOWANCE','ACCUMULATED_DEPRECIATION') AND (NEW.kind<>'ASSET' OR NEW.normal_balance<>'CREDIT')) OR
   (NEW.category IN ('PAYABLE','FUNDING','TAX','CUSTOMER_ADVANCE') AND (NEW.kind<>'LIABILITY' OR NEW.normal_balance<>'CREDIT')) OR
   (NEW.category IN ('CAPITAL','RETAINED_EARNINGS') AND (NEW.kind<>'EQUITY' OR NEW.normal_balance<>'CREDIT')) OR
   (NEW.category='INCOME' AND (NEW.kind<>'INCOME' OR NEW.normal_balance<>'CREDIT')) OR
   (NEW.category='EXPENSE' AND (NEW.kind<>'EXPENSE' OR NEW.normal_balance<>'DEBIT')) OR
   (NEW.category IN ('CLEARING','SUSPENSE','INTERNAL_TRANSFER') AND NOT ((NEW.kind='ASSET' AND NEW.normal_balance='DEBIT') OR (NEW.kind='LIABILITY' AND NEW.normal_balance='CREDIT')))
  ) THEN RAISE EXCEPTION 'Account classification must agree with purpose'; END IF;
 IF NEW.category IN ('LOAN_PRINCIPAL','INTEREST_RECEIVABLE','FEE_RECEIVABLE','ALLOWANCE') AND NEW.usage='POSTING' THEN RAISE EXCEPTION 'Loan balances require control accounts'; END IF;
 IF TG_OP='INSERT' AND NOT EXISTS(SELECT 1 FROM members WHERE id=NEW.created_by AND sacco_id=NEW.sacco_id) THEN RAISE EXCEPTION 'Account maker must belong to institution'; END IF;
 IF TG_OP = 'UPDATE' AND ROW(NEW.id,NEW.sacco_id,NEW.code,NEW.kind,NEW.normal_balance,NEW.usage,NEW.category,NEW.parent_id,NEW.created_by,NEW.recorded_at)
  IS DISTINCT FROM ROW(OLD.id,OLD.sacco_id,OLD.code,OLD.kind,OLD.normal_balance,OLD.usage,OLD.category,OLD.parent_id,OLD.created_by,OLD.recorded_at) THEN
  RAISE EXCEPTION 'Account classification and hierarchy are immutable; create a new account';
 END IF;
 IF NEW.parent_id IS NOT NULL THEN
  IF NEW.parent_id = NEW.id OR EXISTS(WITH RECURSIVE ancestors AS (
    SELECT id,parent_id FROM accounting_account WHERE id=NEW.parent_id AND sacco_id=NEW.sacco_id
    UNION ALL SELECT a.id,a.parent_id FROM accounting_account a JOIN ancestors p ON a.id=p.parent_id
   ) SELECT 1 FROM ancestors WHERE id=NEW.id) THEN RAISE EXCEPTION 'Account hierarchy cycle'; END IF;
  IF NOT EXISTS(SELECT 1 FROM accounting_account WHERE id=NEW.parent_id AND sacco_id=NEW.sacco_id AND usage='HEADING' AND kind=NEW.kind)
   THEN RAISE EXCEPTION 'Parent must be a heading of the same institution and account type'; END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER accounting_account_protected BEFORE INSERT OR UPDATE OR DELETE ON accounting_account FOR EACH ROW EXECUTE FUNCTION accounting_account_guard();

CREATE TABLE accounting_period (
 id uuid PRIMARY KEY, sacco_id varchar(255) NOT NULL REFERENCES registered_saccos(sacco_id),
 starts_on date NOT NULL, ends_on date NOT NULL, state varchar(8) NOT NULL CHECK(state IN ('OPEN','CLOSED')),
 created_by uuid NOT NULL REFERENCES members(id), recorded_at timestamptz NOT NULL,
 closed_by uuid REFERENCES members(id), closed_at timestamptz, closing_evidence varchar(500),
 reopened_by uuid REFERENCES members(id), reopened_at timestamptz, reopening_evidence varchar(500),
 UNIQUE(sacco_id,id), CHECK(ends_on>=starts_on AND ends_on-starts_on<=366),
 CHECK(state='OPEN' OR (closed_by IS NOT NULL AND closed_at IS NOT NULL AND length(trim(closing_evidence))>0))
);
CREATE INDEX ix_accounting_period_dates ON accounting_period(sacco_id,starts_on,ends_on);
CREATE FUNCTION accounting_period_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Accounting periods cannot be deleted'; END IF;
 IF TG_OP='UPDATE' AND ROW(NEW.id,NEW.sacco_id,NEW.starts_on,NEW.ends_on) IS DISTINCT FROM ROW(OLD.id,OLD.sacco_id,OLD.starts_on,OLD.ends_on)
  THEN RAISE EXCEPTION 'Period boundaries are immutable'; END IF;
 IF TG_OP='INSERT' THEN
  PERFORM pg_advisory_xact_lock(hashtextextended(NEW.sacco_id || ':accounting-period-configuration',0));
  IF EXISTS(SELECT 1 FROM accounting_period WHERE sacco_id=NEW.sacco_id AND starts_on<=NEW.ends_on AND ends_on>=NEW.starts_on)
   THEN RAISE EXCEPTION 'Accounting periods cannot overlap'; END IF;
 END IF;
 IF TG_OP='UPDATE' AND OLD.state='CLOSED' AND NEW.state='OPEN' AND
  (NEW.reopened_by IS NULL OR NEW.reopened_at IS NULL OR COALESCE(length(trim(NEW.reopening_evidence)),0)=0)
  THEN RAISE EXCEPTION 'Reopening requires recorded reviewer and evidence'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER accounting_period_protected BEFORE INSERT OR UPDATE OR DELETE ON accounting_period FOR EACH ROW EXECUTE FUNCTION accounting_period_guard();

CREATE TABLE accounting_journal (
 id uuid PRIMARY KEY, sacco_id varchar(255) NOT NULL REFERENCES registered_saccos(sacco_id), station_id varchar(255) NOT NULL,
 period_id uuid NOT NULL, effective_date date NOT NULL, recorded_at timestamptz NOT NULL,
 currency varchar(3) NOT NULL CHECK(currency='TZS'), policy_id uuid NOT NULL REFERENCES accounting_policy(id), policy_version integer NOT NULL CHECK(policy_version>0), policy_hash varchar(64) NOT NULL,
 event_type varchar(40) NOT NULL, source_kind varchar(40) NOT NULL, source_reference varchar(120) NOT NULL,
 request_key uuid NOT NULL, payload_hash varchar(64) NOT NULL,
 description varchar(500) NOT NULL, evidence_reference varchar(500) NOT NULL CHECK(length(trim(evidence_reference))>0),
 state varchar(12) NOT NULL CHECK(state IN ('DRAFT','APPROVED','POSTED','REVERSED')),
 maker_id uuid NOT NULL REFERENCES members(id), approved_by uuid REFERENCES members(id), approved_at timestamptz,
 posted_by uuid REFERENCES members(id), posted_at timestamptz,
 reverses_journal_id uuid UNIQUE, reversal_reason varchar(500),
 UNIQUE(sacco_id,id), UNIQUE(sacco_id,station_id,request_key), UNIQUE(sacco_id,station_id,source_kind,source_reference),
 FOREIGN KEY(sacco_id,period_id) REFERENCES accounting_period(sacco_id,id),
 FOREIGN KEY(sacco_id,policy_id) REFERENCES accounting_policy(sacco_id,id),
 FOREIGN KEY(sacco_id,reverses_journal_id) REFERENCES accounting_journal(sacco_id,id),
 CHECK(state='DRAFT' OR (approved_by IS NOT NULL AND approved_at IS NOT NULL AND approved_by<>maker_id)),
 CHECK(state IN ('DRAFT','APPROVED') OR (posted_by IS NOT NULL AND posted_at IS NOT NULL)),
 CHECK((reverses_journal_id IS NULL AND reversal_reason IS NULL) OR (reverses_journal_id IS NOT NULL AND length(trim(reversal_reason))>0))
);
CREATE INDEX ix_accounting_journal_branch_page ON accounting_journal(sacco_id,station_id,recorded_at DESC,id);
CREATE UNIQUE INDEX ux_accounting_branch_posted_opening ON accounting_journal(sacco_id,station_id) WHERE source_kind='OPENING' AND state='POSTED';
CREATE INDEX ix_accounting_journal_reports ON accounting_journal(sacco_id,station_id,effective_date,posted_at,id) WHERE state IN ('POSTED','REVERSED');
CREATE TABLE accounting_journal_line (
 id uuid PRIMARY KEY, journal_id uuid NOT NULL, sacco_id varchar(255) NOT NULL, account_id uuid NOT NULL,
 line_number integer NOT NULL CHECK(line_number BETWEEN 1 AND 200), debit numeric(18,2) NOT NULL, credit numeric(18,2) NOT NULL, memo varchar(240) NOT NULL,
 FOREIGN KEY(sacco_id,journal_id) REFERENCES accounting_journal(sacco_id,id), FOREIGN KEY(sacco_id,account_id) REFERENCES accounting_account(sacco_id,id),
 CHECK((debit>0 AND credit=0) OR (credit>0 AND debit=0)), UNIQUE(journal_id,line_number)
);
CREATE INDEX ix_accounting_line_activity ON accounting_journal_line(sacco_id,account_id,journal_id);

CREATE FUNCTION accounting_line_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE j accounting_journal; a accounting_account;
BEGIN
 IF TG_OP<>'INSERT' THEN RAISE EXCEPTION 'Journal lines are immutable; create a correcting journal'; END IF;
 SELECT * INTO STRICT j FROM accounting_journal WHERE id=NEW.journal_id AND sacco_id=NEW.sacco_id FOR UPDATE;
 IF j.state<>'DRAFT' THEN RAISE EXCEPTION 'Only draft journals accept lines'; END IF;
 SELECT * INTO STRICT a FROM accounting_account WHERE id=NEW.account_id AND sacco_id=NEW.sacco_id FOR SHARE;
 IF NOT a.active OR a.usage='HEADING' THEN RAISE EXCEPTION 'Posting requires an active posting account'; END IF;
 IF j.source_kind='MANUAL' AND a.usage='CONTROL' THEN RAISE EXCEPTION 'Manual journals cannot alter control accounts'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER accounting_line_protected BEFORE INSERT OR UPDATE OR DELETE ON accounting_journal_line FOR EACH ROW EXECUTE FUNCTION accounting_line_guard();

CREATE FUNCTION accounting_journal_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE p accounting_period; original accounting_journal; policy accounting_policy;
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Journals cannot be deleted'; END IF;
 IF TG_OP='INSERT' AND NEW.state<>'DRAFT' THEN RAISE EXCEPTION 'Journals start in draft'; END IF;
 SELECT * INTO STRICT policy FROM accounting_policy WHERE id=NEW.policy_id AND sacco_id=NEW.sacco_id;
 IF policy.state<>'APPROVED' OR policy.authority<>'LOCAL' OR policy.policy_version<>NEW.policy_version OR policy.content_hash<>NEW.policy_hash
  OR (NOT (NEW.source_kind='OPENING' AND NEW.event_type='OPENING_BALANCE' AND (policy.content_json::jsonb->>'openingDate')::date=NEW.effective_date
       AND NOT EXISTS(SELECT 1 FROM accounting_policy WHERE sacco_id=NEW.sacco_id AND state='APPROVED' AND policy_version<policy.policy_version))
      AND (policy.effective_from>NEW.effective_date OR EXISTS(SELECT 1 FROM accounting_policy WHERE sacco_id=NEW.sacco_id AND state='APPROVED' AND effective_from<=NEW.effective_date AND effective_from>policy.effective_from)))
  THEN RAISE EXCEPTION 'Applicable approved local policy required'; END IF;
 IF COALESCE((policy.content_json::jsonb->'postingRules'->NEW.event_type->>'enabled')::boolean,false) IS NOT TRUE
  THEN RAISE EXCEPTION 'Event is disabled in approved accounting policy'; END IF;
 IF (TG_OP='INSERT' AND NOT EXISTS(SELECT 1 FROM members WHERE id=NEW.maker_id AND sacco_id=NEW.sacco_id AND station_id=NEW.station_id))
  OR (TG_OP='UPDATE' AND NEW.state='APPROVED' AND NOT EXISTS(SELECT 1 FROM members WHERE id=NEW.approved_by AND sacco_id=NEW.sacco_id AND station_id=NEW.station_id))
  OR (TG_OP='UPDATE' AND NEW.state='POSTED' AND NOT EXISTS(SELECT 1 FROM members WHERE id=NEW.posted_by AND sacco_id=NEW.sacco_id AND station_id=NEW.station_id))
  THEN RAISE EXCEPTION 'Journal staff must belong to its institution and branch'; END IF;
 IF TG_OP='UPDATE' THEN
  IF ROW(NEW.id,NEW.sacco_id,NEW.station_id,NEW.period_id,NEW.effective_date,NEW.recorded_at,NEW.currency,NEW.policy_id,NEW.policy_version,NEW.policy_hash,
         NEW.event_type,NEW.source_kind,NEW.source_reference,NEW.request_key,NEW.payload_hash,NEW.description,NEW.evidence_reference,NEW.maker_id,NEW.reverses_journal_id,NEW.reversal_reason)
   IS DISTINCT FROM ROW(OLD.id,OLD.sacco_id,OLD.station_id,OLD.period_id,OLD.effective_date,OLD.recorded_at,OLD.currency,OLD.policy_id,OLD.policy_version,OLD.policy_hash,
         OLD.event_type,OLD.source_kind,OLD.source_reference,OLD.request_key,OLD.payload_hash,OLD.description,OLD.evidence_reference,OLD.maker_id,OLD.reverses_journal_id,OLD.reversal_reason)
   THEN RAISE EXCEPTION 'Journal content is immutable; create a new draft'; END IF;
  IF NOT ((OLD.state='DRAFT' AND NEW.state='APPROVED') OR (OLD.state='APPROVED' AND NEW.state='POSTED') OR (OLD.state='POSTED' AND NEW.state='REVERSED'))
   THEN RAISE EXCEPTION 'Invalid journal state transition'; END IF;
  IF OLD.state<>'DRAFT' AND ROW(NEW.approved_by,NEW.approved_at) IS DISTINCT FROM ROW(OLD.approved_by,OLD.approved_at)
   THEN RAISE EXCEPTION 'Approval is immutable'; END IF;
  IF OLD.state='POSTED' AND ROW(NEW.posted_by,NEW.posted_at) IS DISTINCT FROM ROW(OLD.posted_by,OLD.posted_at)
   THEN RAISE EXCEPTION 'Posting attribution is immutable'; END IF;
  IF NEW.state='REVERSED' AND NOT EXISTS(SELECT 1 FROM accounting_journal WHERE reverses_journal_id=OLD.id AND state='POSTED')
   THEN RAISE EXCEPTION 'Posted linked reversal required'; END IF;
  IF NEW.state='POSTED' THEN
   SELECT * INTO STRICT p FROM accounting_period WHERE id=NEW.period_id AND sacco_id=NEW.sacco_id FOR SHARE;
   IF p.state<>'OPEN' OR NEW.effective_date NOT BETWEEN p.starts_on AND p.ends_on THEN RAISE EXCEPTION 'Accounting period is closed or invalid'; END IF;
   IF NEW.reverses_journal_id IS NOT NULL THEN
    SELECT * INTO STRICT original FROM accounting_journal WHERE id=NEW.reverses_journal_id AND sacco_id=NEW.sacco_id AND station_id=NEW.station_id FOR UPDATE;
    IF original.state<>'POSTED' OR NEW.maker_id IN (original.maker_id,original.posted_by) THEN RAISE EXCEPTION 'Independent reversal of a posted journal required'; END IF;
    IF EXISTS(SELECT account_id,sum(debit) AS d,sum(credit) AS c FROM accounting_journal_line WHERE journal_id=original.id GROUP BY account_id
      EXCEPT SELECT account_id,sum(credit),sum(debit) FROM accounting_journal_line WHERE journal_id=NEW.id GROUP BY account_id)
     OR EXISTS(SELECT account_id,sum(credit),sum(debit) FROM accounting_journal_line WHERE journal_id=NEW.id GROUP BY account_id
      EXCEPT SELECT account_id,sum(debit),sum(credit) FROM accounting_journal_line WHERE journal_id=original.id GROUP BY account_id)
     THEN RAISE EXCEPTION 'Reversal must exactly offset the original accounts'; END IF;
   END IF;
  END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER accounting_journal_protected BEFORE INSERT OR UPDATE OR DELETE ON accounting_journal FOR EACH ROW EXECUTE FUNCTION accounting_journal_guard();
CREATE FUNCTION accounting_journal_balance_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE jid uuid; j accounting_journal;
BEGIN
 jid:=CASE WHEN TG_TABLE_NAME='accounting_journal' THEN NEW.id ELSE NEW.journal_id END;
 SELECT * INTO STRICT j FROM accounting_journal WHERE id=jid;
 IF j.state<>'DRAFT' AND (SELECT count(*)<2 OR count(*)>200 OR sum(debit)<>sum(credit) FROM accounting_journal_line WHERE journal_id=jid)
  THEN RAISE EXCEPTION 'Approved and posted journals require 2-200 balanced lines'; END IF;
 IF j.source_kind='OPENING' AND j.state<>'DRAFT' AND NOT EXISTS(SELECT 1 FROM accounting_opening_batch b WHERE b.journal_id=j.id AND b.sacco_id=j.sacco_id AND b.station_id=j.station_id AND b.state IN ('APPROVED','POSTED') AND b.reviewed_by=j.approved_by)
  THEN RAISE EXCEPTION 'Opening requires independently reviewed import'; END IF;
 RETURN NEW;
END $$;
CREATE CONSTRAINT TRIGGER accounting_journal_balanced AFTER INSERT OR UPDATE ON accounting_journal DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION accounting_journal_balance_guard();
CREATE CONSTRAINT TRIGGER accounting_journal_lines_balanced AFTER INSERT ON accounting_journal_line DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION accounting_journal_balance_guard();

CREATE TABLE accounting_opening_batch (
 id uuid PRIMARY KEY, sacco_id varchar(255) NOT NULL, station_id varchar(255) NOT NULL, cutoff date NOT NULL,
 journal_id uuid UNIQUE, kind varchar(20) NOT NULL CHECK(kind IN ('JOURNAL','ZERO_CERTIFICATE')),
 policy_id uuid NOT NULL, policy_version integer NOT NULL CHECK(policy_version>0), policy_hash varchar(64) NOT NULL,
 request_key uuid NOT NULL, payload_hash varchar(64) NOT NULL, source_evidence varchar(500) NOT NULL,
 reconciliation_evidence varchar(500), state varchar(12) NOT NULL CHECK(state IN ('PREVIEW','APPROVED','POSTED')),
 maker_id uuid NOT NULL REFERENCES members(id), recorded_at timestamptz NOT NULL, reviewed_by uuid REFERENCES members(id), reviewed_at timestamptz,
 FOREIGN KEY(sacco_id,journal_id) REFERENCES accounting_journal(sacco_id,id), FOREIGN KEY(sacco_id,policy_id) REFERENCES accounting_policy(sacco_id,id), UNIQUE(sacco_id,station_id,request_key),
 CHECK((kind='JOURNAL' AND journal_id IS NOT NULL) OR (kind='ZERO_CERTIFICATE' AND journal_id IS NULL AND state<>'POSTED')),
 CHECK(state='PREVIEW' OR (reviewed_by IS NOT NULL AND reviewed_by<>maker_id AND reviewed_at IS NOT NULL AND reconciliation_evidence IS NOT NULL AND length(trim(reconciliation_evidence))>0))
);
CREATE INDEX ix_accounting_opening_branch_page ON accounting_opening_batch(sacco_id,station_id,recorded_at DESC,id);
CREATE FUNCTION accounting_opening_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE policy accounting_policy;
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Opening imports cannot be deleted'; END IF;
 SELECT * INTO STRICT policy FROM accounting_policy WHERE id=NEW.policy_id AND sacco_id=NEW.sacco_id;
 IF policy.state<>'APPROVED' OR policy.authority<>'LOCAL' OR policy.policy_version<>NEW.policy_version OR policy.content_hash<>NEW.policy_hash
  OR (policy.content_json::jsonb->>'openingDate')::date<>NEW.cutoff
  OR EXISTS(SELECT 1 FROM accounting_policy WHERE sacco_id=NEW.sacco_id AND state='APPROVED' AND policy_version<policy.policy_version)
  OR COALESCE((policy.content_json::jsonb->'postingRules'->'OPENING_BALANCE'->>'enabled')::boolean,false) IS NOT TRUE
  THEN RAISE EXCEPTION 'Opening requires first approved local policy and exact approved cutoff'; END IF;
 IF TG_OP='INSERT' AND (NEW.state<>'PREVIEW' OR NOT EXISTS(SELECT 1 FROM members WHERE id=NEW.maker_id AND sacco_id=NEW.sacco_id AND station_id=NEW.station_id))
  THEN RAISE EXCEPTION 'Opening must begin as scoped maker preview'; END IF;
 IF TG_OP='UPDATE' THEN
  IF ROW(NEW.id,NEW.sacco_id,NEW.station_id,NEW.cutoff,NEW.journal_id,NEW.kind,NEW.policy_id,NEW.policy_version,NEW.policy_hash,NEW.request_key,NEW.payload_hash,NEW.source_evidence,NEW.maker_id,NEW.recorded_at)
   IS DISTINCT FROM ROW(OLD.id,OLD.sacco_id,OLD.station_id,OLD.cutoff,OLD.journal_id,OLD.kind,OLD.policy_id,OLD.policy_version,OLD.policy_hash,OLD.request_key,OLD.payload_hash,OLD.source_evidence,OLD.maker_id,OLD.recorded_at)
   THEN RAISE EXCEPTION 'Opening provenance is immutable'; END IF;
  IF NOT ((OLD.state='PREVIEW' AND NEW.state='APPROVED') OR (OLD.state='APPROVED' AND NEW.state='POSTED')) THEN RAISE EXCEPTION 'Invalid opening transition'; END IF;
  IF OLD.state='APPROVED' AND ROW(NEW.reviewed_by,NEW.reviewed_at,NEW.reconciliation_evidence) IS DISTINCT FROM ROW(OLD.reviewed_by,OLD.reviewed_at,OLD.reconciliation_evidence)
   THEN RAISE EXCEPTION 'Opening review is immutable'; END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER accounting_opening_protected BEFORE INSERT OR UPDATE OR DELETE ON accounting_opening_batch FOR EACH ROW EXECUTE FUNCTION accounting_opening_guard();
CREATE TABLE accounting_cutover (
 sacco_id varchar(255) NOT NULL, station_id varchar(255) NOT NULL, cutoff date NOT NULL, opening_batch_id uuid NOT NULL REFERENCES accounting_opening_batch(id),
 source_recorded_cutoff timestamptz NOT NULL,
 reviewed_by uuid NOT NULL REFERENCES members(id), reviewed_at timestamptz NOT NULL, evidence_reference varchar(500) NOT NULL,
 PRIMARY KEY(sacco_id,station_id)
);
CREATE TABLE accounting_audit_event (
 id uuid PRIMARY KEY, sacco_id varchar(255) NOT NULL, station_id varchar(255) NOT NULL, journal_id uuid,
 action varchar(40) NOT NULL, actor_id uuid NOT NULL REFERENCES members(id), evidence_reference varchar(500) NOT NULL, recorded_at timestamptz NOT NULL,
 FOREIGN KEY(sacco_id,journal_id) REFERENCES accounting_journal(sacco_id,id)
);
CREATE TABLE accounting_outbox (
 id uuid PRIMARY KEY, sacco_id varchar(255) NOT NULL, station_id varchar(255) NOT NULL, journal_id uuid NOT NULL,
 event_type varchar(40) NOT NULL, recorded_at timestamptz NOT NULL,
 FOREIGN KEY(sacco_id,journal_id) REFERENCES accounting_journal(sacco_id,id), UNIQUE(journal_id,event_type)
);
CREATE FUNCTION accounting_append_only_guard() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'Accounting audit, outbox and cutover history is append-only'; END $$;
CREATE TRIGGER accounting_audit_immutable BEFORE UPDATE OR DELETE ON accounting_audit_event FOR EACH ROW EXECUTE FUNCTION accounting_append_only_guard();
CREATE TRIGGER accounting_outbox_immutable BEFORE UPDATE OR DELETE ON accounting_outbox FOR EACH ROW EXECUTE FUNCTION accounting_append_only_guard();
CREATE TRIGGER accounting_cutover_immutable BEFORE UPDATE OR DELETE ON accounting_cutover FOR EACH ROW EXECUTE FUNCTION accounting_append_only_guard();
