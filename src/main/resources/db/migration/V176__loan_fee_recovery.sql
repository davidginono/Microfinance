
ALTER TABLE loan_charge_policy ADD COLUMN fee_recovery_account_id uuid;
ALTER TABLE loan_charge_policy ADD FOREIGN KEY (fee_recovery_account_id,sacco_id) REFERENCES gl_account(id,sacco_id);

CREATE TABLE loan_fee_funding_receipt (
    funding_id uuid PRIMARY KEY REFERENCES accountant_loan_post(id),
    receipt_id uuid NOT NULL UNIQUE REFERENCES accountant_loan_post(id),
    CHECK(funding_id<>receipt_id)
);
CREATE TRIGGER loan_fee_funding_receipt_immutable BEFORE UPDATE OR DELETE ON loan_fee_funding_receipt FOR EACH ROW EXECUTE FUNCTION gl_protect_evidence();

CREATE FUNCTION loan_fee_funding_receipt_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE f accountant_loan_post; r accountant_loan_post;
BEGIN
 SELECT * INTO f FROM accountant_loan_post WHERE id=NEW.funding_id;
 SELECT * INTO r FROM accountant_loan_post WHERE id=NEW.receipt_id;
 IF f.state<>'POSTED' OR r.state<>'POSTED' OR f.kind<>'DISBURSEMENT' OR r.kind<>'PAYMENT'
 OR f.loan_id<>r.loan_id OR f.sacco_id<>r.sacco_id OR f.station_id<>r.station_id
 OR f.actor_id<>r.actor_id OR f.effective_date<>r.effective_date OR r.transaction_id IS NOT NULL
 OR r.principal_amount<>0 OR r.interest_amount<>0 OR r.charge_amount<>r.amount
 OR EXISTS(SELECT 1 FROM loan_charge_event e JOIN loan_charge_term t ON t.id=e.term_id WHERE e.journal_id=r.journal_id AND t.kind NOT IN ('APPLICATION','PROCESSING'))
 THEN RAISE EXCEPTION 'Funding fee receipt must match actual funding and fee collection'; END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER loan_fee_funding_receipt_commit AFTER INSERT ON loan_fee_funding_receipt
DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION loan_fee_funding_receipt_guard();

CREATE TABLE loan_fee_recovery_review (
    id uuid PRIMARY KEY,
    loan_id uuid NOT NULL REFERENCES accountant_loan_record(loan_id),
    sacco_id varchar(255) NOT NULL,
    station_id varchar(255) NOT NULL,
    maker_id uuid NOT NULL REFERENCES members(id),
    request_key uuid NOT NULL,
    evidence varchar(500) NOT NULL CHECK(length(btrim(evidence))>0),
    holding_account_id uuid NOT NULL,
    principal_snapshot numeric(18,2) NOT NULL CHECK(principal_snapshot>0),
    posting_snapshot bigint NOT NULL,
    amounts jsonb NOT NULL CHECK(jsonb_typeof(amounts)='object'),
    recorded_at timestamptz NOT NULL,
    UNIQUE(sacco_id,station_id,request_key),
    FOREIGN KEY (holding_account_id,sacco_id) REFERENCES gl_account(id,sacco_id)
);

CREATE TABLE loan_fee_recovery_decision (
    review_id uuid PRIMARY KEY REFERENCES loan_fee_recovery_review(id),
    reviewer_id uuid NOT NULL REFERENCES members(id),
    journal_id uuid NOT NULL UNIQUE REFERENCES gl_journal(id),
    reason varchar(500) NOT NULL CHECK(length(btrim(reason))>0),
    decided_at timestamptz NOT NULL
);

CREATE TABLE loan_fee_recovery_event (
    id uuid PRIMARY KEY,
    term_id uuid NOT NULL REFERENCES loan_charge_term(id),
    journal_id uuid NOT NULL REFERENCES gl_journal(id),
    kind varchar(16) NOT NULL CHECK(kind IN ('FUND','CUTOVER','RECOVER','UNDO_RECOVERY','UNDO_FUND')),
    amount numeric(18,2) NOT NULL CHECK(amount>0),
    holding_account_id uuid NOT NULL REFERENCES gl_account(id),
    debit_line_id uuid NOT NULL UNIQUE REFERENCES gl_journal_line(id),
    credit_line_id uuid NOT NULL UNIQUE REFERENCES gl_journal_line(id),
    reverses_id uuid UNIQUE REFERENCES loan_fee_recovery_event(id),
    CHECK(debit_line_id<>credit_line_id),
    CHECK((kind IN ('UNDO_RECOVERY','UNDO_FUND'))=(reverses_id IS NOT NULL)),
    UNIQUE(term_id,journal_id)
);
CREATE UNIQUE INDEX loan_fee_recovery_term_opening ON loan_fee_recovery_event(term_id) WHERE kind IN ('FUND','CUTOVER');
CREATE INDEX loan_fee_recovery_term_events ON loan_fee_recovery_event(term_id,journal_id);
CREATE INDEX loan_fee_recovery_journal_events ON loan_fee_recovery_event(journal_id);
CREATE INDEX loan_fee_recovery_loan_reviews ON loan_fee_recovery_review(loan_id,recorded_at DESC,id);

CREATE TRIGGER loan_fee_recovery_review_immutable BEFORE UPDATE OR DELETE ON loan_fee_recovery_review FOR EACH ROW EXECUTE FUNCTION gl_protect_evidence();
CREATE TRIGGER loan_fee_recovery_decision_immutable BEFORE UPDATE OR DELETE ON loan_fee_recovery_decision FOR EACH ROW EXECUTE FUNCTION gl_protect_evidence();
CREATE TRIGGER loan_fee_recovery_event_immutable BEFORE UPDATE OR DELETE ON loan_fee_recovery_event FOR EACH ROW EXECUTE FUNCTION gl_protect_evidence();

-- Existing loan validators continue to verify the exact cash, debt and standalone charge lines.
-- Only independently checked, balanced fee-recovery pairs are excluded from that comparison.
CREATE VIEW loan_base_journal_line AS
SELECT l.* FROM gl_journal_line l
WHERE NOT EXISTS(SELECT 1 FROM loan_fee_recovery_event e WHERE e.debit_line_id=l.id)
  AND NOT EXISTS(SELECT 1 FROM loan_fee_recovery_event e WHERE e.credit_line_id=l.id);

CREATE FUNCTION loan_fee_cutover_validate(j gl_journal) RETURNS void LANGUAGE plpgsql AS $$
DECLARE r loan_fee_recovery_review; d loan_fee_recovery_decision; p accounting_period;
BEGIN
 SELECT * INTO d FROM loan_fee_recovery_decision WHERE journal_id=j.id;
 SELECT * INTO r FROM loan_fee_recovery_review WHERE id=d.review_id;
 SELECT * INTO p FROM accounting_period WHERE id=j.period_id FOR SHARE;
 IF d.review_id IS NULL OR d.reviewer_id=r.maker_id OR j.maker_id<>d.reviewer_id
 OR j.source_reference<>r.id::text OR j.sacco_id<>r.sacco_id OR j.station_id<>r.station_id
 OR j.reverses_id IS NOT NULL OR NOT j.direct_post OR p.id IS NULL OR p.sacco_id<>j.sacco_id
 OR p.state<>'OPEN' OR j.effective_date NOT BETWEEN p.starts_on AND p.ends_on OR j.effective_date>CURRENT_DATE
 OR NOT EXISTS(SELECT 1 FROM accountant_loan_record a WHERE a.loan_id=r.loan_id AND a.sacco_id=r.sacco_id AND a.station_id=r.station_id)
 OR NOT EXISTS(SELECT 1 FROM accounting_outbox o WHERE o.journal_id=j.id AND o.sacco_id=j.sacco_id AND o.station_id=j.station_id)
 OR EXISTS(SELECT 1 FROM loan_base_journal_line l WHERE l.journal_id=j.id)
 OR (SELECT count(*) FROM loan_fee_recovery_event e WHERE e.journal_id=j.id) NOT BETWEEN 1 AND 2
 OR (SELECT count(*) FROM jsonb_each(r.amounts))<>(SELECT count(*) FROM loan_fee_recovery_event e WHERE e.journal_id=j.id)
 THEN RAISE EXCEPTION 'Reviewed fee cutover evidence and balanced entries required'; END IF;
END $$;

CREATE FUNCTION loan_fee_recovery_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE t loan_charge_term; j gl_journal; o gl_journal; p accountant_loan_post;
        d gl_journal_line; c gl_journal_line; opening loan_fee_recovery_event;
        old_event loan_fee_recovery_event; review loan_fee_recovery_review; available numeric; debt numeric;
        debit_account uuid; credit_account uuid;
BEGIN
 SELECT * INTO t FROM loan_charge_term WHERE id=NEW.term_id;
 PERFORM 1 FROM accountant_loan_record WHERE loan_id=t.loan_id AND sacco_id=t.sacco_id AND station_id=t.station_id FOR UPDATE;
 SELECT * INTO j FROM gl_journal WHERE id=NEW.journal_id;
 SELECT * INTO p FROM accountant_loan_post WHERE journal_id=j.id;
 SELECT * INTO d FROM gl_journal_line WHERE id=NEW.debit_line_id;
 SELECT * INTO c FROM gl_journal_line WHERE id=NEW.credit_line_id;
 SELECT * INTO opening FROM loan_fee_recovery_event WHERE term_id=t.id AND kind IN ('FUND','CUTOVER');
 IF NOT FOUND OR t.kind NOT IN ('APPLICATION','PROCESSING') OR j.state IS DISTINCT FROM 'POSTED'
 OR j.sacco_id<>t.sacco_id OR j.station_id<>t.station_id OR NEW.holding_account_id<>opening.holding_account_id
 OR NEW.holding_account_id=t.liability_account_id
 OR NOT EXISTS(SELECT 1 FROM gl_account WHERE id=NEW.holding_account_id AND sacco_id=t.sacco_id AND active
                   AND type='LIABILITY' AND kind IN ('POSTING','CONTROL') AND purpose='CLEARING')
 OR NOT EXISTS(SELECT 1 FROM gl_account WHERE id=t.liability_account_id AND sacco_id=t.sacco_id AND active)
 OR EXISTS(SELECT 1 FROM loan_fee_recovery_event WHERE debit_line_id=NEW.credit_line_id OR credit_line_id=NEW.debit_line_id)
 THEN RAISE EXCEPTION 'Scoped fee recovery accounts and source required'; END IF;

 IF NEW.kind IN ('FUND','CUTOVER','UNDO_RECOVERY') THEN
  debit_account:=t.liability_account_id; credit_account:=NEW.holding_account_id;
 ELSE
  debit_account:=NEW.holding_account_id; credit_account:=t.liability_account_id;
 END IF;
 IF d.journal_id IS DISTINCT FROM j.id OR c.journal_id IS DISTINCT FROM j.id
 OR d.sacco_id<>j.sacco_id OR c.sacco_id<>j.sacco_id OR d.station_id<>j.station_id OR c.station_id<>j.station_id
 OR d.account_id<>debit_account OR c.account_id<>credit_account OR d.debit<>NEW.amount OR c.credit<>NEW.amount OR d.credit<>0 OR c.debit<>0
 THEN RAISE EXCEPTION 'Fee recovery journal pair must match its allocation'; END IF;

 IF NEW.kind='FUND' THEN
  IF j.source_type<>'LOAN_CHARGE_FUNDING' OR p.kind IS DISTINCT FROM 'DISBURSEMENT' OR p.loan_id<>t.loan_id
  OR NOT EXISTS(SELECT 1 FROM loan_charge_event e WHERE e.term_id=t.id AND e.journal_id=j.id AND e.kind='DEDUCT' AND e.amount=NEW.amount)
  OR NOT EXISTS(SELECT 1 FROM loan_charge_contract k JOIN loan_charge_policy q ON q.id=k.policy_id
                WHERE k.loan_id=t.loan_id AND q.fee_recovery_account_id=NEW.holding_account_id)
  THEN RAISE EXCEPTION 'Fee recovery opening must match actual funding deductions'; END IF;
 ELSIF NEW.kind='CUTOVER' THEN
  IF j.source_type<>'LOAN_FEE_CUTOVER' THEN RAISE EXCEPTION 'Reviewed fee cutover journal required'; END IF;
  PERFORM loan_fee_cutover_validate(j);
  SELECT r.* INTO review FROM loan_fee_recovery_review r JOIN loan_fee_recovery_decision a ON a.review_id=r.id WHERE a.journal_id=j.id;
  IF review.loan_id<>t.loan_id OR review.holding_account_id<>NEW.holding_account_id
  OR (review.amounts->>t.kind)::numeric IS DISTINCT FROM NEW.amount
  OR NEW.amount > (SELECT coalesce(sum(CASE WHEN e.kind='DEDUCT' THEN e.amount ELSE -e.amount END),0)
     FROM loan_charge_event e WHERE e.term_id=t.id AND e.kind IN ('DEDUCT','DEDUCT_UNDO'))
  THEN RAISE EXCEPTION 'Fee cutover must be backed by recorded deductions'; END IF;
 ELSIF NEW.kind='RECOVER' THEN
  IF j.source_type NOT IN ('LOAN_RECORD_PAYMENT','LOAN_CHARGE_RECEIPT') OR p.kind IS DISTINCT FROM 'PAYMENT' OR p.loan_id<>t.loan_id
  OR (SELECT sum(e.amount) FROM loan_fee_recovery_event e WHERE e.journal_id=j.id)>p.principal_amount
  OR j.effective_date<(SELECT effective_date FROM gl_journal WHERE id=opening.journal_id)
  THEN RAISE EXCEPTION 'Fee recovery must fit within the posted principal payment'; END IF;
 ELSE
  SELECT * INTO old_event FROM loan_fee_recovery_event WHERE id=NEW.reverses_id;
  SELECT * INTO o FROM gl_journal WHERE id=old_event.journal_id;
  IF old_event.term_id IS DISTINCT FROM t.id OR old_event.amount<>NEW.amount OR j.reverses_id IS DISTINCT FROM o.id
  OR j.effective_date<o.effective_date OR j.maker_id=o.maker_id
  OR (NEW.kind='UNDO_RECOVERY' AND (old_event.kind<>'RECOVER' OR j.source_type NOT IN ('LOAN_RECORD_REVERSAL','LOAN_CHARGE_REVERSAL')
       OR p.kind IS DISTINCT FROM 'REVERSAL' OR p.loan_id<>t.loan_id))
  OR (NEW.kind='UNDO_FUND' AND (old_event.kind<>'FUND' OR j.source_type<>'LOAN_ADJUSTMENT'))
  THEN RAISE EXCEPTION 'Exact independent fee recovery reversal required'; END IF;
 END IF;

 SELECT sum(CASE WHEN kind IN ('RECOVER','UNDO_FUND') THEN -amount ELSE amount END)
 INTO available FROM loan_fee_recovery_event WHERE term_id=t.id;
 SELECT principal-principal_paid INTO debt FROM loan_ledgers WHERE loan_application_id=t.loan_id;
 IF available<0 OR available>opening.amount
 OR (NEW.kind='CUTOVER' AND (SELECT sum(e.amount) FROM loan_fee_recovery_event e WHERE e.journal_id=j.id)>debt)
 THEN RAISE EXCEPTION 'Fee recovery balance cannot exceed evidenced principal'; END IF;
 RETURN NULL;
END $$;

CREATE FUNCTION loan_fee_recovery_draft_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM gl_journal WHERE id=NEW.journal_id AND state='DRAFT')
 THEN RAISE EXCEPTION 'Recovery evidence must be recorded with the originating journal'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER loan_fee_recovery_draft BEFORE INSERT ON loan_fee_recovery_event
FOR EACH ROW EXECUTE FUNCTION loan_fee_recovery_draft_guard();

CREATE CONSTRAINT TRIGGER loan_fee_recovery_event_commit AFTER INSERT ON loan_fee_recovery_event
DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION loan_fee_recovery_guard();

CREATE FUNCTION loan_fee_recovery_outstanding(loan uuid) RETURNS numeric LANGUAGE sql STABLE AS $$
 SELECT coalesce(sum(CASE WHEN e.kind IN ('RECOVER','UNDO_FUND') THEN -e.amount ELSE e.amount END),0)
 FROM loan_fee_recovery_event e JOIN loan_charge_term t ON t.id=e.term_id WHERE t.loan_id=loan
$$;

CREATE FUNCTION loan_fee_recovery_debt_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE debt numeric;
BEGIN
 SELECT principal-principal_paid INTO debt FROM loan_ledgers WHERE loan_application_id=NEW.loan_application_id;
 IF loan_fee_recovery_outstanding(NEW.loan_application_id)>coalesce(debt,0)
 THEN RAISE EXCEPTION 'Loan principal cannot close with unrecovered withheld fees'; END IF;
 RETURN NULL;
END $$;

CREATE CONSTRAINT TRIGGER loan_fee_recovery_debt AFTER UPDATE OF principal_paid ON loan_ledgers
DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION loan_fee_recovery_debt_guard();

CREATE FUNCTION loan_fee_recovery_post_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE p accountant_loan_post; recovered numeric;
BEGIN
 SELECT * INTO p FROM accountant_loan_post WHERE id=NEW.id;
 IF p.kind='PAYMENT' THEN
  SELECT coalesce(sum(amount),0) INTO recovered FROM loan_fee_recovery_event WHERE journal_id=p.journal_id AND kind='RECOVER';
  IF recovered<>least(p.principal_amount,loan_fee_recovery_outstanding(p.loan_id)+recovered)
  THEN RAISE EXCEPTION 'The principal payment must recover withheld fees first'; END IF;
 END IF;
 IF p.kind='REVERSAL' AND EXISTS(SELECT 1 FROM accountant_loan_post o JOIN loan_fee_recovery_event e ON e.journal_id=o.journal_id
     WHERE o.id=p.reverses_id AND e.kind='RECOVER' AND NOT EXISTS(SELECT 1 FROM loan_fee_recovery_event u WHERE u.journal_id=p.journal_id AND u.reverses_id=e.id))
 THEN RAISE EXCEPTION 'A repayment correction must restore its fee recovery'; END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER loan_fee_recovery_post AFTER INSERT ON accountant_loan_post
DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION loan_fee_recovery_post_guard();

DO $$
DECLARE definition text; identity text;
BEGIN
 SELECT pg_get_constraintdef(oid) INTO identity FROM pg_constraint WHERE conrelid='gl_journal'::regclass AND conname='gl_core_identity';
 ALTER TABLE gl_journal DROP CONSTRAINT gl_core_identity;
 EXECUTE 'ALTER TABLE gl_journal ADD CONSTRAINT gl_core_identity CHECK (('||
    substring(identity from 8 for length(identity)-8)||') OR (direct_post AND policy_id IS NULL AND checker_id IS NULL AND source_type=''LOAN_FEE_CUTOVER''))';

 SELECT pg_get_functiondef('loan_charge_validate(gl_journal)'::regprocedure) INTO definition;
 EXECUTE replace(definition,'FROM gl_journal_line WHERE journal_id=', 'FROM loan_base_journal_line WHERE journal_id=');
 SELECT pg_get_functiondef('gl_validate_post()'::regprocedure) INTO definition;
 definition:=replace(definition,'FROM gl_journal_line WHERE journal_id=', 'FROM loan_base_journal_line WHERE journal_id=');
 IF strpos(definition,'IF NEW.state<>''POSTED'' THEN RETURN NEW; END IF;')=0 THEN RAISE EXCEPTION 'Missing GL validation entry'; END IF;
 definition:=replace(definition,'IF NEW.state<>''POSTED'' THEN RETURN NEW; END IF;',
    'IF NEW.state<>''POSTED'' THEN RETURN NEW; END IF; IF NEW.source_type=''LOAN_FEE_CUTOVER'' THEN PERFORM loan_fee_cutover_validate(NEW); RETURN NEW; END IF;');
 EXECUTE definition;
 SELECT pg_get_functiondef('core_post_commit_guard()'::regprocedure) INTO definition;
 IF strpos(definition,'SELECT 1 FROM gl_journal j JOIN accounting_voucher')=0 THEN RAISE EXCEPTION 'Missing core posting guard'; END IF;
 definition:=replace(definition,'IF NOT EXISTS(SELECT 1 FROM gl_journal j JOIN accounting_voucher',
    'IF EXISTS (SELECT 1 FROM gl_journal j WHERE j.id=journal AND j.state=''POSTED'' AND j.source_type=''LOAN_FEE_CUTOVER'') THEN PERFORM loan_fee_cutover_validate((SELECT j FROM gl_journal j WHERE j.id=journal)); RETURN NULL; END IF; IF NOT EXISTS(SELECT 1 FROM gl_journal j JOIN accounting_voucher');
 EXECUTE definition;
END $$;
