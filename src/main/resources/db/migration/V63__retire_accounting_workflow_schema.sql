-- Retire the removed accounting modules without deleting financial evidence.
-- ALTER ... SET SCHEMA preserves table OIDs, indexes, rows and foreign keys.
SET LOCAL lock_timeout = '10s';
CREATE SCHEMA accounting_archive;
REVOKE ALL ON SCHEMA accounting_archive FROM PUBLIC;
COMMENT ON SCHEMA accounting_archive IS 'Read-only financial evidence from modules retired on 2026-10-15. Not an application workflow schema.';

DO $$
DECLARE table_name text; item record;
BEGIN
 FOREACH table_name IN ARRAY ARRAY[
  'accounting_business_control_entry',
  'accounting_business_document',
  'accounting_business_opening',
  'accounting_business_opening_review',
  'accounting_close_decision',
  'accounting_close_review',
  'accounting_fixed_asset',
  'accounting_policies',
  'accounting_policy_approvals',
  'accounting_release_branch_sources',
  'accounting_release_cash_sources',
  'accounting_release_decisions',
  'accounting_release_invalidations',
  'accounting_release_requests',
  'accounting_supplier',
  'cash_flow_allocation_reviews',
  'cash_flow_allocations',
  'financial_statement_institution_sources',
  'financial_statement_results',
  'financial_statement_templates',
  'financial_statement_versions',
  'gl_cutover_coverage',
  'gl_operational_bridge',
  'gl_source_cancellation',
  'reconciliation_allocation',
  'reconciliation_certificate',
  'reconciliation_certificate_decision',
  'reconciliation_certificate_statement',
  'reconciliation_exception',
  'reconciliation_exception_decision',
  'reconciliation_format',
  'reconciliation_match',
  'reconciliation_match_decision',
  'reconciliation_statement',
  'reconciliation_statement_file',
  'reconciliation_statement_line',
  'reconciliation_timing_source',
  'regulatory_statement_formats',
  'regulatory_statement_reviews',
  'regulatory_statement_submissions',
  'statement_output_artifacts',
  'statement_output_reviews',
  'statement_output_sets'
 ] LOOP
  EXECUTE format('ALTER TABLE public.%I SET SCHEMA accounting_archive',table_name);
  -- Remove workflow logic, retaining PostgreSQL's internal foreign-key triggers.
  FOR item IN SELECT tgname FROM pg_trigger
   WHERE tgrelid=format('accounting_archive.%I',table_name)::regclass AND NOT tgisinternal LOOP
   EXECUTE format('DROP TRIGGER %I ON accounting_archive.%I',item.tgname,table_name);
  END LOOP;
 END LOOP;
END $$;
ALTER VIEW public.reconciliation_active_allocation SET SCHEMA accounting_archive;

CREATE FUNCTION accounting_archive.reject_changes() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Archived accounting evidence is read-only'; END $$;
DO $$
DECLARE item record;
BEGIN
 FOR item IN SELECT tablename FROM pg_tables WHERE schemaname='accounting_archive' LOOP
  EXECUTE format('CREATE TRIGGER archive_read_only BEFORE INSERT OR UPDATE OR DELETE OR TRUNCATE ON accounting_archive.%I FOR EACH STATEMENT EXECUTE FUNCTION accounting_archive.reject_changes()',item.tablename);
 END LOOP;
END $$;
REVOKE INSERT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER ON ALL TABLES IN SCHEMA accounting_archive FROM PUBLIC;

-- Retired workflows must not run through shared posting/period tables.
DROP TRIGGER gl_source_reversal_validation ON public.gl_journal;
DROP TRIGGER operational_bridge_post_owner ON public.gl_journal;
DROP TRIGGER accounting_period_decision ON public.accounting_period;

CREATE OR REPLACE FUNCTION gl_validate_post() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE p accounting_period; cnt integer; difference numeric; original gl_journal;
BEGIN
 IF NEW.state<>'POSTED' THEN RETURN NEW; END IF;
 SELECT * INTO p FROM accounting_period WHERE id=NEW.period_id FOR SHARE;
 IF NEW.source_type IN ('LOAN_RECORD_DISBURSEMENT','LOAN_RECORD_PAYMENT','LOAN_RECORD_REVERSAL') THEN
  IF NOT NEW.direct_post OR p.id IS NULL OR p.sacco_id<>NEW.sacco_id OR p.state<>'OPEN' OR NEW.effective_date NOT BETWEEN p.starts_on AND p.ends_on OR NEW.effective_date>CURRENT_DATE
  THEN RAISE EXCEPTION 'Open valid loan posting period required'; END IF;
  IF NOT EXISTS(SELECT 1 FROM accountant_loan_post x WHERE x.journal_id=NEW.id AND x.state='POSTED' AND x.sacco_id=NEW.sacco_id AND x.station_id=NEW.station_id
    AND x.actor_id=NEW.maker_id AND x.id::text=NEW.source_reference AND x.request_key=NEW.request_key AND x.payload_hash=NEW.payload_hash AND x.effective_date=NEW.effective_date
    AND NEW.source_type='LOAN_RECORD_'||x.kind AND x.recorded_at=NEW.posted_at)
  THEN RAISE EXCEPTION 'Direct loan source required'; END IF;
  IF EXISTS(SELECT 1 FROM gl_journal_line z JOIN gl_account a ON a.id=z.account_id WHERE z.journal_id=NEW.id AND NOT a.active)
  THEN RAISE EXCEPTION 'Active accounts required'; END IF;
  SELECT count(*),sum(debit-credit) INTO cnt,difference FROM gl_journal_line WHERE journal_id=NEW.id;
  IF cnt NOT BETWEEN 2 AND 3 OR difference<>0 THEN RAISE EXCEPTION 'Balanced loan lines required'; END IF;
  IF NOT EXISTS(SELECT 1 FROM accountant_loan_post x JOIN gl_account m ON m.id=x.money_account_id JOIN gl_account a ON a.id=x.principal_account_id
   WHERE x.journal_id=NEW.id AND m.kind='POSTING' AND m.type='ASSET' AND m.purpose IN ('CASH','BANK','MOBILE_MONEY')
    AND a.kind='CONTROL' AND a.type='ASSET' AND a.purpose='LOAN_PRINCIPAL'
    AND (x.interest_amount=0 OR EXISTS(SELECT 1 FROM gl_account i WHERE i.id=x.interest_account_id AND i.kind='POSTING' AND i.purpose='CLEARING')))
  THEN RAISE EXCEPTION 'Loan account classifications required'; END IF;
  IF EXISTS((SELECT account_id,debit,credit FROM gl_journal_line WHERE journal_id=NEW.id)
   EXCEPT ALL (SELECT x.money_account_id,CASE WHEN x.kind='PAYMENT' THEN x.amount ELSE 0 END,CASE WHEN x.kind='PAYMENT' THEN 0 ELSE x.amount END FROM accountant_loan_post x WHERE x.journal_id=NEW.id
    UNION ALL SELECT x.principal_account_id,CASE WHEN x.kind='PAYMENT' THEN 0 ELSE x.principal_amount END,CASE WHEN x.kind='PAYMENT' THEN x.principal_amount ELSE 0 END FROM accountant_loan_post x WHERE x.journal_id=NEW.id AND x.principal_amount>0
    UNION ALL SELECT x.interest_account_id,CASE WHEN x.kind='PAYMENT' THEN 0 ELSE x.interest_amount END,CASE WHEN x.kind='PAYMENT' THEN x.interest_amount ELSE 0 END FROM accountant_loan_post x WHERE x.journal_id=NEW.id AND x.interest_amount>0))
   OR cnt<>(SELECT 1+(CASE WHEN principal_amount>0 THEN 1 ELSE 0 END)+(CASE WHEN interest_amount>0 THEN 1 ELSE 0 END) FROM accountant_loan_post WHERE journal_id=NEW.id)
  THEN RAISE EXCEPTION 'Loan allocation lines must match source'; END IF;
  IF NEW.source_type='LOAN_RECORD_REVERSAL' THEN
   SELECT * INTO original FROM gl_journal WHERE id=NEW.reverses_id FOR UPDATE;
   IF original.state<>'POSTED' OR original.source_type<>'LOAN_RECORD_PAYMENT' OR NEW.effective_date<original.effective_date
    OR NOT EXISTS(SELECT 1 FROM accountant_loan_post x JOIN accountant_loan_post o ON o.id=x.reverses_id WHERE x.journal_id=NEW.id AND o.journal_id=original.id)
    OR EXISTS((SELECT account_id,debit,credit FROM gl_journal_line WHERE journal_id=NEW.id) EXCEPT ALL (SELECT account_id,credit,debit FROM gl_journal_line WHERE journal_id=original.id))
    OR cnt<>(SELECT count(*) FROM gl_journal_line WHERE journal_id=original.id)
   THEN RAISE EXCEPTION 'Exact linked repayment reversal required'; END IF;
  ELSIF NEW.reverses_id IS NOT NULL THEN RAISE EXCEPTION 'Unexpected reversal'; END IF;
  IF NOT EXISTS(SELECT 1 FROM accounting_outbox WHERE journal_id=NEW.id AND sacco_id=NEW.sacco_id AND station_id=NEW.station_id)
  THEN RAISE EXCEPTION 'Loan outbox required'; END IF;
  RETURN NEW;
 END IF;
 IF NEW.direct_post THEN
  IF p.id IS NULL OR p.state<>'OPEN' OR NEW.effective_date NOT BETWEEN p.starts_on AND p.ends_on THEN RAISE EXCEPTION 'Open period required'; END IF;
  IF NEW.effective_date>CURRENT_DATE THEN RAISE EXCEPTION 'Future posting date is invalid'; END IF;
  IF EXISTS(SELECT 1 FROM gl_journal_line l JOIN gl_account a ON a.id=l.account_id WHERE l.journal_id=NEW.id AND (NOT a.active OR a.kind<>'POSTING'))
  THEN RAISE EXCEPTION 'Core vouchers require ordinary active posting accounts'; END IF;
  SELECT count(*),sum(debit-credit) INTO cnt,difference FROM gl_journal_line WHERE journal_id=NEW.id;
  IF cnt NOT BETWEEN 2 AND 500 OR difference<>0 THEN RAISE EXCEPTION 'Balanced lines required'; END IF;
  IF NOT EXISTS(SELECT 1 FROM accounting_voucher v WHERE v.journal_id=NEW.id AND v.sacco_id=NEW.sacco_id AND v.station_id=NEW.station_id
    AND v.posted_by=NEW.maker_id AND v.number=NEW.source_reference AND v.effective_date=NEW.effective_date
    AND v.posted_at=NEW.posted_at AND v.request_key=NEW.request_key AND v.payload_hash=NEW.payload_hash
    AND ((v.reverses_id IS NULL AND NEW.reverses_id IS NULL AND NEW.source_type='CORE_'||v.type)
      OR (v.reverses_id IS NOT NULL AND NEW.reverses_id IS NOT NULL AND NEW.source_type='CORE_REVERSAL'))
    AND v.transaction_count=(SELECT count(*) FROM accounting_voucher_transaction t WHERE t.voucher_id=v.id)
    AND v.total=(SELECT sum(t.amount) FROM accounting_voucher_transaction t WHERE t.voucher_id=v.id))
  THEN RAISE EXCEPTION 'Voucher and journal must agree'; END IF;
  IF NEW.reverses_id IS NULL AND EXISTS(SELECT 1 FROM accounting_voucher v JOIN accounting_voucher_transaction t ON t.voucher_id=v.id
    JOIN gl_account m ON m.id=v.money_account_id WHERE v.journal_id=NEW.id AND v.type<>'JOURNAL'
    AND (m.purpose NOT IN ('CASH','BANK','MOBILE_MONEY') OR (v.type='RECEIPT' AND t.debit_account_id<>m.id) OR (v.type='PAYMENT' AND t.credit_account_id<>m.id)))
  THEN RAISE EXCEPTION 'Voucher money account direction must agree'; END IF;
  IF EXISTS(
    (SELECT account_id,debit,credit FROM gl_journal_line WHERE journal_id=NEW.id)
    EXCEPT ALL
    (SELECT t.debit_account_id,t.amount,0 FROM accounting_voucher_transaction t JOIN accounting_voucher v ON v.id=t.voucher_id WHERE v.journal_id=NEW.id
     UNION ALL SELECT t.credit_account_id,0,t.amount FROM accounting_voucher_transaction t JOIN accounting_voucher v ON v.id=t.voucher_id WHERE v.journal_id=NEW.id))
   OR cnt<>(SELECT count(*)*2 FROM accounting_voucher_transaction t JOIN accounting_voucher v ON v.id=t.voucher_id WHERE v.journal_id=NEW.id)
  THEN RAISE EXCEPTION 'Voucher transaction lines must match journal'; END IF;
  IF NEW.reverses_id IS NOT NULL THEN
   SELECT * INTO original FROM gl_journal WHERE id=NEW.reverses_id FOR UPDATE;
   IF NOT original.direct_post OR original.state<>'POSTED' OR original.reverses_id IS NOT NULL OR NEW.effective_date<original.effective_date
    OR NOT EXISTS(SELECT 1 FROM accounting_voucher r JOIN accounting_voucher o ON o.id=r.reverses_id WHERE r.journal_id=NEW.id AND o.journal_id=original.id)
   THEN RAISE EXCEPTION 'Invalid core reversal'; END IF;
   IF EXISTS((SELECT account_id,debit,credit FROM gl_journal_line WHERE journal_id=NEW.id)
    EXCEPT ALL(SELECT account_id,credit,debit FROM gl_journal_line WHERE journal_id=original.id))
   OR cnt<>(SELECT count(*) FROM gl_journal_line WHERE journal_id=original.id) THEN RAISE EXCEPTION 'Exact reversal required'; END IF;
  END IF;
  IF NOT EXISTS(SELECT 1 FROM accounting_outbox WHERE journal_id=NEW.id AND sacco_id=NEW.sacco_id AND station_id=NEW.station_id)
  THEN RAISE EXCEPTION 'Accounting outbox required'; END IF;
  RETURN NEW;
 END IF;
 RAISE EXCEPTION 'Legacy accounting workflows are retired';
END $$;

CREATE OR REPLACE FUNCTION gl_block_cancelled_journal() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF EXISTS(SELECT 1 FROM accounting_archive.gl_source_cancellation WHERE journal_id=OLD.id) THEN RAISE EXCEPTION 'Cancelled source journal is immutable'; END IF;
 RETURN NEW;
END; $$;

CREATE OR REPLACE FUNCTION gl_block_cancelled_line() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 PERFORM 1 FROM gl_journal WHERE id=NEW.journal_id FOR UPDATE;
 IF EXISTS(SELECT 1 FROM accounting_archive.gl_source_cancellation WHERE journal_id=NEW.journal_id) THEN RAISE EXCEPTION 'Cancelled source journal cannot receive lines'; END IF;
 RETURN NEW;
END; $$;

CREATE OR REPLACE FUNCTION gl_reserve_reversal() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.reverses_id IS NOT NULL THEN
 PERFORM 1 FROM gl_journal WHERE id=NEW.reverses_id FOR UPDATE;
 IF EXISTS(SELECT 1 FROM gl_journal j WHERE j.reverses_id=NEW.reverses_id
 AND NOT EXISTS(SELECT 1 FROM accounting_archive.gl_source_cancellation c WHERE c.journal_id=j.id)) THEN
 RAISE EXCEPTION 'Original journal already has an active reversal'; END IF;
 END IF;
 RETURN NEW;
END; $$;

CREATE OR REPLACE FUNCTION gl_protect_journal() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' OR OLD.state='POSTED' OR NOT OLD.direct_post THEN RAISE EXCEPTION 'Journal history is immutable'; END IF;
 IF NEW.direct_post IS DISTINCT FROM OLD.direct_post OR
 ROW(NEW.id,NEW.sacco_id,NEW.station_id,NEW.policy_id,NEW.policy_version,NEW.period_id,NEW.source_type,NEW.source_reference,
  NEW.request_key,NEW.payload_hash,NEW.currency,NEW.evidence_reference,NEW.reason,NEW.effective_date,NEW.maker_id,NEW.recorded_at,NEW.reverses_id)
 IS DISTINCT FROM ROW(OLD.id,OLD.sacco_id,OLD.station_id,OLD.policy_id,OLD.policy_version,OLD.period_id,OLD.source_type,OLD.source_reference,
  OLD.request_key,OLD.payload_hash,OLD.currency,OLD.evidence_reference,OLD.reason,OLD.effective_date,OLD.maker_id,OLD.recorded_at,OLD.reverses_id)
 THEN RAISE EXCEPTION 'Journal payload is immutable'; END IF;
 IF OLD.state<>'DRAFT' OR NEW.state<>'POSTED' OR NEW.checker_id IS NOT NULL THEN RAISE EXCEPTION 'Invalid direct posting transition'; END IF;
 RETURN NEW;
END $$;

CREATE OR REPLACE FUNCTION gl_protect_lines() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE j gl_journal; a gl_account;
BEGIN
 IF TG_OP<>'INSERT' THEN RAISE EXCEPTION 'Journal lines are append-only'; END IF;
 SELECT * INTO j FROM gl_journal WHERE id=NEW.journal_id FOR UPDATE;
 SELECT * INTO a FROM gl_account WHERE id=NEW.account_id;
 IF NOT j.direct_post OR j.state<>'DRAFT' OR NOT a.active OR a.kind='HEADING' THEN RAISE EXCEPTION 'Invalid journal account or state'; END IF;
 IF j.source_type='MANUAL' AND a.kind='CONTROL' THEN RAISE EXCEPTION 'Manual journals cannot bypass controls'; END IF;
 RETURN NEW;
END; $$;

CREATE FUNCTION gl_active_source_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NOT NEW.direct_post THEN RAISE EXCEPTION 'Legacy accounting workflows are retired'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER gl_active_source_guard BEFORE INSERT ON public.gl_journal
 FOR EACH ROW EXECUTE FUNCTION gl_active_source_guard();

CREATE OR REPLACE FUNCTION gl_validate_period() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Periods cannot be deleted'; END IF;
 IF TG_OP='UPDATE' THEN
  IF NEW IS DISTINCT FROM OLD THEN RAISE EXCEPTION 'Period management is retired; existing dates and closed states are protected'; END IF;
 ELSE
  IF NEW.policy_id IS NOT NULL OR NEW.state<>'OPEN' OR NEW.closed_by IS NOT NULL OR NEW.closed_at IS NOT NULL
   THEN RAISE EXCEPTION 'Only a core posting date bucket may be created'; END IF;
  PERFORM pg_advisory_xact_lock(hashtextextended('GL_SETUP/' || NEW.sacco_id,0));
  IF EXISTS(SELECT 1 FROM accounting_period WHERE sacco_id=NEW.sacco_id AND starts_on<=NEW.ends_on AND ends_on>=NEW.starts_on)
   THEN RAISE EXCEPTION 'Accounting periods cannot overlap'; END IF;
 END IF;
 RETURN NEW;
END $$;

CREATE OR REPLACE FUNCTION accounting_branch_cohort_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE old_scope text; new_scope text; retained boolean;
BEGIN
 IF TG_OP='UPDATE' AND NEW.sacco_id IS NOT DISTINCT FROM OLD.sacco_id
    AND NEW.station_id IS NOT DISTINCT FROM OLD.station_id
    AND NEW.active IS NOT DISTINCT FROM OLD.active
    AND NEW.access_status IS NOT DISTINCT FROM OLD.access_status THEN RETURN NEW; END IF;
 IF TG_OP<>'INSERT' THEN old_scope:=OLD.sacco_id; END IF;
 IF TG_OP<>'DELETE' THEN new_scope:=NEW.sacco_id; END IF;
 -- Deterministic ordering also protects an unreferenced branch identifier move.
 IF old_scope IS NOT NULL AND new_scope IS NOT NULL AND old_scope<>new_scope THEN
   PERFORM pg_advisory_xact_lock(hashtextextended('GL_SETUP/'||least(old_scope,new_scope),0));
   PERFORM pg_advisory_xact_lock(hashtextextended('GL_SETUP/'||greatest(old_scope,new_scope),0));
 ELSE
   PERFORM pg_advisory_xact_lock(hashtextextended('GL_SETUP/'||coalesce(old_scope,new_scope),0));
 END IF;
 IF TG_OP='DELETE' OR (TG_OP='UPDATE' AND (NEW.sacco_id<>OLD.sacco_id OR NEW.station_id<>OLD.station_id)) THEN
   retained:=EXISTS(SELECT 1 FROM gl_journal WHERE sacco_id=OLD.sacco_id AND station_id=OLD.station_id)
     OR EXISTS(SELECT 1 FROM accounting_archive.reconciliation_statement WHERE sacco_id=OLD.sacco_id AND station_id=OLD.station_id)
     OR EXISTS(SELECT 1 FROM accounting_archive.accounting_close_review WHERE sacco_id=OLD.sacco_id AND station_id=OLD.station_id)
     OR EXISTS(SELECT 1 FROM loan_ledgers WHERE sacco_id=OLD.sacco_id AND station_id=OLD.station_id);
   IF retained THEN RAISE EXCEPTION 'Retained financial branch identity cannot be changed or deleted'; END IF;
 END IF;
 IF TG_OP='DELETE' THEN RETURN OLD; END IF;
 RETURN NEW;
END $$;

-- These functions no longer have workflow triggers or callers. No CASCADE.
DO $$
DECLARE item record;
BEGIN
 FOR item IN SELECT p.oid::regprocedure signature FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
 WHERE n.nspname='public' AND p.proname = ANY(ARRAY[
  'accounting_validate_period_decision',
  'business_opening_assert_reviewable',
  'business_opening_validate_import',
  'business_opening_validate_review',
  'cash_flow_protect_history',
  'cash_flow_validate_insert',
  'check_accounting_release_decision',
  'check_financial_institution_source',
  'check_financial_statement_source',
  'check_regulatory_reviewer',
  'check_statement_output_review',
  'gl_validate_account_mappings',
  'gl_validate_source_cancellation',
  'gl_validate_source_reversal',
  'protect_accounting_policy_history',
  'protect_accounting_release_evidence',
  'protect_business_asset_basis',
  'protect_business_document',
  'protect_financial_statement_evidence',
  'protect_financial_statement_version',
  'reconciliation_certificate_statement_guard',
  'reconciliation_file_source_guard',
  'reconciliation_format_review_guard',
  'reconciliation_immutable',
  'reconciliation_protect_format',
  'reconciliation_protect_import_rows',
  'reconciliation_reject_opening_allocation',
  'reconciliation_require_reviewed_timing',
  'reconciliation_review_statement_file',
  'reconciliation_review_timing_source',
  'reconciliation_valid_timing_pair',
  'reconciliation_validate_allocation',
  'reconciliation_validate_decision',
  'reconciliation_validate_match',
  'reconciliation_validate_statement',
  'reconciliation_validate_timing_source',
  'require_accounting_release_source_proof',
  'require_business_operational_bridge',
  'require_posted_operational_bridge_owner',
  'validate_accounting_policy',
  'validate_accounting_policy_approval',
  'validate_accounting_release_branch_source',
  'validate_accounting_release_cash_source',
  'validate_business_asset_register',
  'validate_business_operational_bridge',
  'validate_business_posted_source',
  'validate_business_source_correction'
 ]) LOOP
  EXECUTE format('DROP FUNCTION %s',item.signature);
 END LOOP;
END $$;
