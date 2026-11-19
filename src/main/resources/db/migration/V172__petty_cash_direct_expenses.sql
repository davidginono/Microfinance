-- Designation records evidence, never an additional financial posting.
CREATE TABLE petty_cash_fund (
 sacco_id varchar(255) NOT NULL, station_id varchar(255) NOT NULL, account_id uuid NOT NULL,
 starts_on date NOT NULL, basis varchar(20) NOT NULL CHECK(basis IN ('NEW_ZERO','REVIEWED_OPENING')),
 opening_journal_id uuid REFERENCES gl_journal(id), evidence varchar(500) NOT NULL CHECK(length(trim(evidence))>0),
 configured_by uuid NOT NULL REFERENCES members(id), configured_at timestamptz NOT NULL,
 PRIMARY KEY(sacco_id,station_id), UNIQUE(account_id), FOREIGN KEY(account_id,sacco_id) REFERENCES gl_account(id,sacco_id),
 FOREIGN KEY(sacco_id,station_id) REFERENCES sacco_stations(sacco_id,station_id),
 CHECK((basis='NEW_ZERO')=(opening_journal_id IS NULL))
);
CREATE FUNCTION petty_cash_setup_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE a gl_account; opening gl_journal;
BEGIN
 IF TG_OP<>'INSERT' THEN RAISE EXCEPTION 'Petty cash designation is permanent'; END IF;
 SELECT * INTO a FROM gl_account WHERE id=NEW.account_id FOR UPDATE;
 IF a.sacco_id IS DISTINCT FROM NEW.sacco_id OR NOT a.active OR a.kind<>'POSTING' OR a.type<>'ASSET'
   OR a.normal_balance<>'DEBIT' OR a.purpose<>'CASH' OR gl_account_requires_loan_source(a.id)
   OR NOT EXISTS(SELECT 1 FROM sacco_stations WHERE sacco_id=NEW.sacco_id AND station_id=NEW.station_id)
 THEN RAISE EXCEPTION 'petty.error.account'; END IF;
 PERFORM pg_advisory_xact_lock(hashtextextended('petty-account:'||a.id,0));
 IF NEW.basis='NEW_ZERO' THEN
  IF (a.created_at AT TIME ZONE 'Africa/Dar_es_Salaam')::date<>NEW.starts_on
    OR NEW.starts_on<>(NEW.configured_at AT TIME ZONE 'Africa/Dar_es_Salaam')::date
    OR EXISTS(SELECT 1 FROM gl_journal_line WHERE account_id=a.id)
  THEN RAISE EXCEPTION 'petty.error.coverage'; END IF;
 ELSE
  SELECT j.* INTO opening FROM accounting_archive.gl_cutover_coverage c JOIN gl_journal j ON j.id=c.opening_journal_id
   WHERE c.sacco_id=NEW.sacco_id AND c.station_id=NEW.station_id AND c.complete AND c.maker_id<>c.checker_id
    AND c.opening_journal_id=NEW.opening_journal_id AND j.state='POSTED' AND j.source_type='OPENING'
    AND j.checker_id=c.checker_id AND c.reconciled_through>=j.effective_date
    AND (EXISTS(SELECT 1 FROM gl_journal_line WHERE journal_id=j.id AND account_id=a.id)
      OR (a.created_at>j.posted_at AND NOT EXISTS(SELECT 1 FROM gl_journal_line l JOIN gl_journal x ON x.id=l.journal_id
       WHERE l.account_id=a.id AND x.effective_date<=j.effective_date)));
  IF opening.id IS NULL OR NEW.starts_on<>opening.effective_date+1
    OR EXISTS(SELECT 1 FROM gl_journal_line WHERE account_id=a.id AND station_id<>NEW.station_id)
  THEN RAISE EXCEPTION 'petty.error.coverage'; END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER petty_cash_setup BEFORE INSERT OR UPDATE OR DELETE ON petty_cash_fund FOR EACH ROW EXECUTE FUNCTION petty_cash_setup_guard();
CREATE TRIGGER petty_cash_no_truncate BEFORE TRUNCATE ON petty_cash_fund FOR EACH STATEMENT EXECUTE FUNCTION gl_protect_evidence();

-- All writers, including specialized services and designation, share this lock.
CREATE FUNCTION petty_cash_line_lock() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 PERFORM 1 FROM gl_account WHERE id=NEW.account_id FOR SHARE;
 IF EXISTS(SELECT 1 FROM petty_cash_fund WHERE account_id=NEW.account_id) THEN
  PERFORM pg_advisory_xact_lock(hashtextextended('petty-account:'||NEW.account_id,0));
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER petty_cash_line_lock BEFORE INSERT ON gl_journal_line FOR EACH ROW EXECUTE FUNCTION petty_cash_line_lock();

CREATE FUNCTION petty_cash_check_plan(institution text, branch text, day date, kind text, entries jsonb, assess_balance boolean DEFAULT true)
RETURNS void LANGUAGE plpgsql AS $$
DECLARE fund petty_cash_fund; item record;
BEGIN
 FOR fund IN SELECT f.* FROM petty_cash_fund f WHERE f.account_id IN
  (SELECT (e->>'account')::uuid FROM jsonb_array_elements(entries) e) ORDER BY f.account_id LOOP
  IF fund.sacco_id<>institution OR fund.station_id<>branch THEN RAISE EXCEPTION 'petty.error.scope'; END IF;
  IF day<fund.starts_on THEN RAISE EXCEPTION 'petty.error.coverage'; END IF;
  IF kind NOT IN ('PAYMENT','JOURNAL') THEN RAISE EXCEPTION 'petty.error.expense'; END IF;
  FOR item IN SELECT e->>'row' id FROM jsonb_array_elements(entries) e
   WHERE (e->>'account')::uuid=fund.account_id GROUP BY e->>'row' LOOP
   IF kind='PAYMENT' THEN
    IF (SELECT count(*) FROM jsonb_array_elements(entries) e WHERE e->>'row'=item.id AND e->>'side'='DEBIT')<>1
     OR EXISTS(SELECT 1 FROM jsonb_array_elements(entries) e JOIN gl_account a ON a.id=(e->>'account')::uuid
      WHERE e->>'row'=item.id AND ((e->>'side'='DEBIT' AND (a.type<>'EXPENSE' OR gl_account_requires_loan_source(a.id)))
       OR ((e->>'account')::uuid=fund.account_id AND e->>'side'<>'CREDIT')))
    THEN RAISE EXCEPTION 'petty.error.expense'; END IF;
   ELSE
    IF EXISTS(SELECT 1 FROM jsonb_array_elements(entries) e JOIN gl_account a ON a.id=(e->>'account')::uuid
      WHERE e->>'row'=item.id AND NOT (((e->>'account')::uuid=fund.account_id AND e->>'side'='DEBIT')
       OR (e->>'side'='CREDIT' AND a.id<>fund.account_id AND gl_loan_disbursement_account(a.id,a.sacco_id))))
    THEN RAISE EXCEPTION 'petty.error.topup'; END IF;
   END IF;
  END LOOP;
  IF assess_balance AND EXISTS(WITH daily AS (
    SELECT j.effective_date date,sum(l.debit-l.credit) amount FROM gl_journal j JOIN gl_journal_line l ON l.journal_id=j.id
     WHERE j.sacco_id=institution AND j.station_id=branch AND j.state='POSTED' AND l.account_id=fund.account_id GROUP BY j.effective_date
    UNION ALL SELECT day,sum(CASE e->>'side' WHEN 'DEBIT' THEN (e->>'amount')::numeric ELSE -(e->>'amount')::numeric END)
     FROM jsonb_array_elements(entries) e WHERE (e->>'account')::uuid=fund.account_id),
   balances AS (SELECT date,sum(sum(amount)) OVER(ORDER BY date) balance FROM daily GROUP BY date)
   SELECT 1 FROM balances WHERE date>=day AND balance<0)
  THEN RAISE EXCEPTION 'petty.error.funds'; END IF;
 END LOOP;
END $$;

-- Validation must report a rule failure without aborting a saved approval draft's transaction.
CREATE FUNCTION petty_cash_plan_issue(institution text, branch text, day date, kind text, entries jsonb)
RETURNS text LANGUAGE plpgsql AS $$
BEGIN
 PERFORM petty_cash_check_plan(institution,branch,day,kind,entries);
 RETURN NULL;
EXCEPTION WHEN raise_exception THEN RETURN SQLERRM;
END $$;

CREATE FUNCTION petty_cash_post_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE fund petty_cash_fund; v accounting_voucher; entries jsonb; original gl_journal;
BEGIN
 IF NEW.state<>'POSTED' THEN RETURN NULL; END IF;
 FOR fund IN SELECT f.* FROM petty_cash_fund f WHERE f.account_id IN
  (SELECT l.account_id FROM gl_journal_line l WHERE l.journal_id=NEW.id) ORDER BY f.account_id LOOP
  PERFORM pg_advisory_xact_lock(hashtextextended('petty-account:'||fund.account_id,0));
  IF fund.sacco_id<>NEW.sacco_id OR fund.station_id<>NEW.station_id THEN RAISE EXCEPTION 'petty.error.scope'; END IF;
  IF NEW.effective_date<fund.starts_on THEN RAISE EXCEPTION 'petty.error.coverage'; END IF;
  IF NEW.reverses_id IS NULL THEN
   SELECT * INTO v FROM accounting_voucher WHERE journal_id=NEW.id;
   IF v.id IS NULL THEN RAISE EXCEPTION 'petty.error.expense'; END IF;
   SELECT jsonb_agg(jsonb_build_object('row',p.transaction_id,'account',p.account_id,'side',p.side,'amount',p.amount))
    INTO entries FROM accounting_voucher_posting p WHERE p.voucher_id=v.id;
   PERFORM petty_cash_check_plan(NEW.sacco_id,NEW.station_id,NEW.effective_date,v.type,entries,false);
  ELSE
   SELECT * INTO original FROM gl_journal WHERE id=NEW.reverses_id;
   IF original.state<>'POSTED' OR original.reverses_id IS NOT NULL OR NEW.effective_date<original.effective_date
    OR EXISTS((SELECT account_id,debit,credit FROM gl_journal_line WHERE journal_id=NEW.id)
     EXCEPT ALL (SELECT account_id,credit,debit FROM gl_journal_line WHERE journal_id=original.id))
    OR (SELECT count(*) FROM gl_journal_line WHERE journal_id=NEW.id)<>(SELECT count(*) FROM gl_journal_line WHERE journal_id=original.id)
   THEN RAISE EXCEPTION 'petty.error.reversal'; END IF;
  END IF;
  IF EXISTS(SELECT 1 FROM (
    SELECT j.effective_date date,sum(sum(l.debit-l.credit)) OVER(ORDER BY j.effective_date) balance
    FROM gl_journal j JOIN gl_journal_line l ON l.journal_id=j.id
    WHERE j.sacco_id=fund.sacco_id AND j.station_id=fund.station_id AND j.state='POSTED' AND l.account_id=fund.account_id
    GROUP BY j.effective_date) days WHERE date>=NEW.effective_date AND balance<0)
  THEN RAISE EXCEPTION 'petty.error.funds'; END IF;
 END LOOP;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER petty_cash_post AFTER INSERT OR UPDATE ON gl_journal
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION petty_cash_post_guard();
