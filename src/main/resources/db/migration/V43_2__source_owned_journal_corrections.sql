-- Retained source cancellation is independent evidence, never a deleted or fictitiously posted journal.
CREATE TABLE gl_source_cancellation (
 journal_id uuid PRIMARY KEY, sacco_id varchar(255) NOT NULL, station_id varchar(255) NOT NULL,
 maker_id uuid NOT NULL REFERENCES members(id), checker_id uuid NOT NULL REFERENCES members(id),
 source_event varchar(40) NOT NULL, source_reference varchar(160) NOT NULL,
 previous_state varchar(10) NOT NULL CHECK(previous_state IN ('DRAFT','APPROVED')),
 evidence_reference varchar(500) NOT NULL CHECK(length(trim(evidence_reference))>0),
 payload_hash varchar(64) NOT NULL, recorded_at timestamptz NOT NULL,
 CHECK(maker_id<>checker_id),
 FOREIGN KEY(journal_id,sacco_id,station_id) REFERENCES gl_journal(id,sacco_id,station_id)
);
CREATE INDEX ix_gl_source_cancellation_scope ON gl_source_cancellation(sacco_id,station_id,recorded_at,journal_id);
CREATE INDEX ix_gl_source_cancellation_checker ON gl_source_cancellation(checker_id);
CREATE TRIGGER gl_source_cancellation_immutable BEFORE UPDATE OR DELETE ON gl_source_cancellation FOR EACH ROW EXECUTE FUNCTION gl_protect_evidence();
CREATE FUNCTION gl_validate_source_cancellation() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE j gl_journal; p accounting_period; expected text;
BEGIN
 SELECT * INTO j FROM gl_journal WHERE id=NEW.journal_id FOR UPDATE;
 SELECT * INTO p FROM accounting_period WHERE id=j.period_id FOR SHARE;
 expected:=CASE j.source_type WHEN 'SOURCE_REVERSAL' THEN 'REVERSAL' ELSE j.source_type END;
 IF j.state NOT IN ('DRAFT','APPROVED') OR j.source_type IN ('MANUAL','OPENING','REVERSAL','OPERATIONAL_BRIDGE')
 OR j.source_type NOT IN ('DISBURSEMENT','REPAYMENT','INTEREST_ACCRUAL','FEE','REFUND','ADVANCE','SETTLEMENT','TOP_UP','EXPENSE','FUNDING','CAPITAL','PROVISION','WRITE_OFF','RECOVERY','SOURCE_REVERSAL')
 OR NEW.source_event<>expected OR NEW.source_reference<>j.source_reference OR NEW.previous_state<>j.state
 OR NEW.maker_id<>j.maker_id OR NEW.payload_hash<>j.payload_hash OR NEW.sacco_id<>j.sacco_id OR NEW.station_id<>j.station_id
 OR p.state<>'OPEN' OR j.effective_date NOT BETWEEN p.starts_on AND p.ends_on
 THEN RAISE EXCEPTION 'Cancellation must retain an unposted scoped source event in its open period'; END IF;
 IF NOT EXISTS(SELECT 1 FROM members WHERE id=NEW.checker_id AND sacco_id=j.sacco_id AND station_id=j.station_id)
 OR NOT EXISTS(SELECT 1 FROM accounting_policies ap JOIN accounting_policy_approvals a ON a.policy_id=ap.id
 WHERE ap.id=j.policy_id AND ap.sacco_id=j.sacco_id AND ap.policy_version=j.policy_version AND a.sacco_id=j.sacco_id AND a.decision='APPROVED' AND ap.authoritative_ledger='LOCAL_GL')
 THEN RAISE EXCEPTION 'Independent historical source scope and approved policy are required'; END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER gl_source_cancellation_validation BEFORE INSERT ON gl_source_cancellation FOR EACH ROW EXECUTE FUNCTION gl_validate_source_cancellation();
CREATE FUNCTION gl_block_cancelled_journal() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF EXISTS(SELECT 1 FROM gl_source_cancellation WHERE journal_id=OLD.id) THEN RAISE EXCEPTION 'Cancelled source journal is immutable'; END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER gl_cancelled_journal_protection BEFORE UPDATE ON gl_journal FOR EACH ROW EXECUTE FUNCTION gl_block_cancelled_journal();
CREATE FUNCTION gl_block_cancelled_line() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 PERFORM 1 FROM gl_journal WHERE id=NEW.journal_id FOR UPDATE;
 IF EXISTS(SELECT 1 FROM gl_source_cancellation WHERE journal_id=NEW.journal_id) THEN RAISE EXCEPTION 'Cancelled source journal cannot receive lines'; END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER gl_cancelled_line_protection BEFORE INSERT ON gl_journal_line FOR EACH ROW EXECUTE FUNCTION gl_block_cancelled_line();

-- Reserve the original under its row lock. A trusted cancellation may release the reservation without erasing history.
ALTER TABLE gl_journal DROP CONSTRAINT gl_journal_reverses_id_key;
CREATE INDEX ix_gl_journal_reverses_id ON gl_journal(reverses_id) WHERE reverses_id IS NOT NULL;
CREATE FUNCTION gl_reserve_reversal() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.reverses_id IS NOT NULL THEN
 PERFORM 1 FROM gl_journal WHERE id=NEW.reverses_id FOR UPDATE;
 IF EXISTS(SELECT 1 FROM gl_journal j WHERE j.reverses_id=NEW.reverses_id
 AND NOT EXISTS(SELECT 1 FROM gl_source_cancellation c WHERE c.journal_id=j.id)) THEN
 RAISE EXCEPTION 'Original journal already has an active reversal'; END IF;
 END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER gl_reversal_reservation BEFORE INSERT ON gl_journal FOR EACH ROW EXECUTE FUNCTION gl_reserve_reversal();

CREATE FUNCTION gl_validate_source_reversal() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE j gl_journal; o gl_journal;
BEGIN
 SELECT * INTO j FROM gl_journal WHERE id=NEW.id;
 IF j.source_type<>'SOURCE_REVERSAL' THEN RETURN NEW; END IF;
 IF j.reverses_id IS NULL THEN RAISE EXCEPTION 'Source reversal requires its original journal'; END IF;
 SELECT * INTO o FROM gl_journal WHERE id=j.reverses_id FOR UPDATE;
 IF o.state<>'POSTED' OR o.reverses_id IS NOT NULL OR o.source_type IN ('MANUAL','OPENING','REVERSAL','OPERATIONAL_BRIDGE','SOURCE_REVERSAL')
 OR j.effective_date<o.effective_date OR j.maker_id IN(o.maker_id,o.checker_id)
 OR (j.checker_id IS NOT NULL AND j.checker_id IN(o.maker_id,o.checker_id))
 THEN RAISE EXCEPTION 'Source correction must be linked and independently reviewed'; END IF;
 IF NOT EXISTS(SELECT 1 FROM accounting_policies ap JOIN accounting_policy_approvals a ON a.policy_id=ap.id
 WHERE ap.id=j.policy_id AND ap.sacco_id=j.sacco_id AND a.sacco_id=j.sacco_id AND ap.policy_version=j.policy_version
 AND a.decision='APPROVED' AND ap.authoritative_ledger='LOCAL_GL' AND ap.effective_from<=j.effective_date
 AND ap.posting_matrix_json::jsonb->'REVERSAL'->>'permission'='ALLOWED')
 THEN RAISE EXCEPTION 'Approved explicit reversal treatment is required'; END IF;
 IF EXISTS((SELECT account_id,debit,credit FROM gl_journal_line WHERE journal_id=j.id)
 EXCEPT ALL(SELECT account_id,credit,debit FROM gl_journal_line WHERE journal_id=o.id))
 OR EXISTS((SELECT account_id,credit,debit FROM gl_journal_line WHERE journal_id=o.id)
 EXCEPT ALL(SELECT account_id,debit,credit FROM gl_journal_line WHERE journal_id=j.id))
 THEN RAISE EXCEPTION 'Source reversal must exactly swap every original line'; END IF;
 RETURN NEW;
END; $$;
CREATE CONSTRAINT TRIGGER gl_source_reversal_validation AFTER INSERT OR UPDATE ON gl_journal DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION gl_validate_source_reversal();

CREATE OR REPLACE FUNCTION gl_validate_post() RETURNS trigger LANGUAGE plpgsql AS $$
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
 posting_event := CASE NEW.source_type WHEN 'OPENING' THEN 'OPENING_BALANCE' WHEN 'MANUAL' THEN 'MANUAL_JOURNAL' WHEN 'SOURCE_REVERSAL' THEN 'REVERSAL' ELSE NEW.source_type END;
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
