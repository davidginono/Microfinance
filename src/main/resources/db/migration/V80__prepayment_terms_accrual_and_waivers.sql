-- New servicing terms are explicit and immutable. No existing loan is automatically repriced.
ALTER TABLE loan_product_settings ADD COLUMN prepayment_schedule varchar(32) NOT NULL DEFAULT 'LAST_INSTALMENTS'
 CHECK(prepayment_schedule IN ('NEXT_INSTALMENTS','LAST_INSTALMENTS','PRO_RATA'));
ALTER TABLE loan_product_settings ADD COLUMN partial_prepayment_interest varchar(32) NOT NULL DEFAULT 'RECALCULATE'
 CHECK(partial_prepayment_interest IN ('RECALCULATE','RETAIN_UNTIL_SETTLEMENT'));
ALTER TABLE loan_product_settings ADD COLUMN settlement_day_basis varchar(32) NOT NULL DEFAULT 'ACTUAL_PERIOD'
 CHECK(settlement_day_basis IN ('ACTUAL_PERIOD','THIRTY_360'));
CREATE TABLE loan_prepayment_terms (
 loan_id uuid PRIMARY KEY REFERENCES loan_ledgers(loan_application_id),
 sacco_id varchar(255) NOT NULL, station_id varchar(255) NOT NULL,
 schedule varchar(32) NOT NULL CHECK(schedule IN ('NEXT_INSTALMENTS','LAST_INSTALMENTS','PRO_RATA')),
 partial_interest varchar(32) NOT NULL CHECK(partial_interest IN ('RECALCULATE','RETAIN_UNTIL_SETTLEMENT')),
 day_basis varchar(32) NOT NULL CHECK(day_basis IN ('ACTUAL_PERIOD','THIRTY_360')),
 interest_method varchar(32) NOT NULL CHECK(interest_method IN ('FLAT_RATE','REDUCING_BALANCE')),
 source varchar(24) NOT NULL CHECK(source IN ('CONTRACT','EXPLICIT_ADOPTION')),
 actor_id uuid REFERENCES members(id), reason varchar(500), created_at timestamptz NOT NULL
);
CREATE TRIGGER prepayment_terms_immutable BEFORE UPDATE OR DELETE ON loan_prepayment_terms FOR EACH ROW EXECUTE FUNCTION protect_loan_financial_history();
CREATE TABLE loan_interest_waiver (
 id uuid PRIMARY KEY, loan_id uuid NOT NULL REFERENCES loan_ledgers(loan_application_id),
 sacco_id varchar(255) NOT NULL, station_id varchar(255) NOT NULL,
 sequence bigint NOT NULL, kind varchar(8) NOT NULL CHECK(kind IN ('WAIVER','REVERSAL')),
 request_key uuid NOT NULL, effective_date date NOT NULL, amount numeric(18,2) NOT NULL CHECK(amount>0),
 actor_id uuid NOT NULL REFERENCES members(id), reason varchar(500) NOT NULL CHECK(length(trim(reason))>0),
 reverses_id uuid UNIQUE REFERENCES loan_interest_waiver(id), created_at timestamptz NOT NULL,
 CHECK((kind='WAIVER' AND reverses_id IS NULL) OR (kind='REVERSAL' AND reverses_id IS NOT NULL)),
 UNIQUE(sacco_id,station_id,request_key), UNIQUE(loan_id,sequence)
);
CREATE INDEX ix_interest_waiver_history ON loan_interest_waiver(loan_id,sequence DESC);
CREATE TABLE loan_interest_waiver_part (
 waiver_id uuid NOT NULL REFERENCES loan_interest_waiver(id), installment_id uuid NOT NULL REFERENCES loan_ledger_installments(id),
 amount numeric(18,2) NOT NULL CHECK(amount>0), PRIMARY KEY(waiver_id,installment_id)
);
CREATE TRIGGER interest_waiver_immutable BEFORE UPDATE OR DELETE ON loan_interest_waiver FOR EACH ROW EXECUTE FUNCTION protect_loan_financial_history();
CREATE TRIGGER interest_waiver_part_immutable BEFORE UPDATE OR DELETE ON loan_interest_waiver_part FOR EACH ROW EXECUTE FUNCTION protect_loan_financial_history();
ALTER TABLE loan_ledger_installments ADD COLUMN interest_waived numeric(18,2) NOT NULL DEFAULT 0;
ALTER TABLE loan_ledger_installments DROP CONSTRAINT installment_interest_exclusion;
ALTER TABLE loan_ledger_installments ADD CONSTRAINT installment_interest_exclusion
 CHECK(interest_excluded>=0 AND interest_waived>=0 AND interest_paid+interest_excluded+interest_waived<=interest);

CREATE FUNCTION loan_period_fraction(a date,b date,d date,basis varchar) RETURNS numeric LANGUAGE sql IMMUTABLE AS $$
 SELECT CASE WHEN d<=a THEN 0 WHEN d>=b THEN 1 WHEN basis='THIRTY_360' THEN
 least(1::numeric,greatest(0::numeric,((extract(year from d)-extract(year from a))*360+(extract(month from d)-extract(month from a))*30+least(extract(day from d),30)-least(extract(day from a),30)) /
 greatest(1::numeric,(extract(year from b)-extract(year from a))*360+(extract(month from b)-extract(month from a))*30+least(extract(day from b),30)-least(extract(day from a),30))))
 ELSE (d-a)::numeric/(b-a) END
$$;
-- Earned interest is distinct from cash paid, scheduled interest and unearned rebates.
CREATE FUNCTION loan_interest_earned_gross(installment uuid,as_of date) RETURNS numeric LANGUAGE plpgsql STABLE AS $$
DECLARE i loan_ledger_installments; p loan_prepayment_terms; start_date date; earned numeric; reduction numeric;
BEGIN
 SELECT * INTO STRICT i FROM loan_ledger_installments WHERE id=installment;
 SELECT * INTO p FROM loan_prepayment_terms WHERE loan_id=i.loan_application_id;
 IF NOT FOUND THEN RETURN CASE WHEN i.due_date<=as_of THEN i.interest-i.interest_excluded ELSE 0 END; END IF;
 SELECT coalesce((SELECT due_date FROM loan_ledger_installments WHERE loan_application_id=i.loan_application_id AND installment_number<i.installment_number ORDER BY installment_number DESC LIMIT 1),l.disbursement_date)
 INTO start_date FROM loan_ledgers l WHERE l.loan_application_id=i.loan_application_id;
 earned:=i.interest*loan_period_fraction(start_date,i.due_date,as_of,p.day_basis);
 SELECT coalesce(sum(a.interest_excluded*loan_period_fraction(greatest(t.payment_date,start_date),i.due_date,as_of,p.day_basis)),0)
 INTO reduction FROM loan_repayment_allocations a JOIN loan_repayment_transactions t ON t.id=a.transaction_id
 WHERE a.installment_id=installment AND a.interest_excluded>0 AND t.kind='PAYMENT'
 AND NOT EXISTS(SELECT 1 FROM loan_repayment_transactions r WHERE r.reverses_transaction_id=t.id);
 RETURN greatest(0,least(i.interest-i.interest_excluded,earned-reduction));
END $$;
CREATE FUNCTION loan_interest_earned_exact(installment uuid,as_of date) RETURNS numeric LANGUAGE sql STABLE AS $$
 SELECT greatest(0,loan_interest_earned_gross(i.id,as_of)-i.interest_waived) FROM loan_ledger_installments i WHERE i.id=installment
$$;
CREATE FUNCTION loan_interest_earned(installment uuid,as_of date) RETURNS numeric LANGUAGE sql STABLE AS $$
 SELECT round(loan_interest_earned_exact(installment,as_of),2)
$$;
CREATE FUNCTION loan_interest_due(installment uuid,as_of date) RETURNS numeric LANGUAGE sql STABLE AS $$
 SELECT greatest(0,loan_interest_earned(i.id,as_of)-i.interest_paid) FROM loan_ledger_installments i WHERE i.id=installment
$$;

CREATE OR REPLACE FUNCTION validate_interest_exclusion_transaction() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE original loan_repayment_transactions; effective date; modern boolean;
BEGIN
 IF NEW.future_interest_excluded<>(SELECT coalesce(sum(a.interest_excluded),0) FROM loan_repayment_allocations a WHERE a.transaction_id=NEW.id)
 THEN RAISE EXCEPTION 'Excluded interest must match retained allocations'; END IF;
 IF NEW.future_interest_excluded=0 THEN RETURN NULL; END IF;
 IF NEW.kind NOT IN ('PAYMENT','REVERSAL') THEN RAISE EXCEPTION 'Excluded interest requires a payment or its reversal'; END IF;
 effective:=NEW.payment_date;
 SELECT EXISTS(SELECT 1 FROM loan_prepayment_terms WHERE loan_id=NEW.loan_application_id) INTO modern;
 IF NEW.kind='REVERSAL' THEN
  SELECT * INTO STRICT original FROM loan_repayment_transactions WHERE id=NEW.reverses_transaction_id;
  IF original.loan_application_id<>NEW.loan_application_id OR original.future_interest_excluded<>NEW.future_interest_excluded
  THEN RAISE EXCEPTION 'Excluded interest reversal must match the original payment'; END IF;
  effective:=original.payment_date;
 ELSIF NOT modern THEN
  IF NOT EXISTS(SELECT 1 FROM loan_ledgers l WHERE l.loan_application_id=NEW.loan_application_id AND l.principal_paid=l.principal)
   OR EXISTS(SELECT 1 FROM loan_ledger_installments i WHERE i.loan_application_id=NEW.loan_application_id AND i.due_date<=effective AND i.interest_paid+i.interest_waived<>i.interest)
  THEN RAISE EXCEPTION 'Legacy future interest can only be excluded on full repayment'; END IF;
 ELSIF NEW.principal_amount<=0 THEN RAISE EXCEPTION 'A principal prepayment is required for future interest reduction';
 END IF;
 IF EXISTS(SELECT 1 FROM loan_repayment_allocations a JOIN loan_ledger_installments i ON i.id=a.installment_id
  WHERE a.transaction_id=NEW.id AND a.interest_excluded>0 AND (i.loan_application_id<>NEW.loan_application_id OR i.due_date<=effective))
 THEN RAISE EXCEPTION 'Only unearned interest on the repaid loan may be excluded'; END IF;
 RETURN NULL;
END $$;

CREATE FUNCTION validate_interest_waiver() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE w loan_interest_waiver; original loan_interest_waiver; installment uuid; retained numeric; actual numeric;
BEGIN
 IF TG_TABLE_NAME='loan_ledger_installments' THEN installment:=NEW.id;
 ELSE
  IF TG_TABLE_NAME='loan_interest_waiver' THEN w:=NEW; ELSE SELECT * INTO STRICT w FROM loan_interest_waiver WHERE id=NEW.waiver_id; installment:=NEW.installment_id; END IF;
  IF NOT EXISTS(SELECT 1 FROM loan_ledgers l WHERE l.loan_application_id=w.loan_id AND l.sacco_id=w.sacco_id AND l.station_id=w.station_id AND l.disbursement_date<=w.effective_date AND l.next_sequence>w.sequence)
    OR w.effective_date>CURRENT_DATE
    OR NOT EXISTS(SELECT 1 FROM accounting_period p WHERE p.sacco_id=w.sacco_id AND p.state='OPEN' AND w.effective_date BETWEEN p.starts_on AND p.ends_on)
    OR NOT EXISTS(SELECT 1 FROM members m WHERE m.id=w.actor_id AND m.sacco_id=w.sacco_id AND m.station_id=w.station_id AND m.status='ACTIVE')
    OR EXISTS(SELECT 1 FROM loan_repayment_transactions t WHERE t.loan_application_id=w.loan_id AND t.sequence=w.sequence)
    OR w.amount<>(SELECT coalesce(sum(amount),0) FROM loan_interest_waiver_part WHERE waiver_id=w.id)
    OR EXISTS(SELECT 1 FROM loan_interest_waiver_part a JOIN loan_ledger_installments i ON i.id=a.installment_id WHERE a.waiver_id=w.id AND i.loan_application_id<>w.loan_id)
  THEN RAISE EXCEPTION 'Waiver scope and allocations must match'; END IF;
  IF w.kind='WAIVER' AND EXISTS(SELECT 1 FROM loan_interest_waiver_part a JOIN loan_ledger_installments i ON i.id=a.installment_id WHERE a.waiver_id=w.id AND i.interest_paid+i.interest_waived>round(loan_interest_earned_gross(i.id,w.effective_date),2)) THEN RAISE EXCEPTION 'Only unpaid earned interest may be waived'; END IF;
  IF w.kind='REVERSAL' THEN
   SELECT * INTO STRICT original FROM loan_interest_waiver WHERE id=w.reverses_id;
   IF original.kind<>'WAIVER' OR original.loan_id<>w.loan_id OR original.amount<>w.amount OR original.actor_id=w.actor_id OR w.effective_date<original.effective_date
    OR EXISTS((SELECT installment_id,amount FROM loan_interest_waiver_part WHERE waiver_id=original.id EXCEPT SELECT installment_id,amount FROM loan_interest_waiver_part WHERE waiver_id=w.id)
       UNION ALL (SELECT installment_id,amount FROM loan_interest_waiver_part WHERE waiver_id=w.id EXCEPT SELECT installment_id,amount FROM loan_interest_waiver_part WHERE waiver_id=original.id))
   THEN RAISE EXCEPTION 'Waiver reversal must independently match the original'; END IF;
  END IF;
 END IF;
 IF installment IS NOT NULL THEN
  SELECT interest_waived INTO actual FROM loan_ledger_installments WHERE id=installment;
  SELECT coalesce(sum(CASE WHEN ev.kind='WAIVER' THEN a.amount ELSE -a.amount END),0) INTO retained FROM loan_interest_waiver_part a JOIN loan_interest_waiver ev ON ev.id=a.waiver_id WHERE a.installment_id=installment;
  IF actual<>retained THEN RAISE EXCEPTION 'Waived interest counter must match immutable waiver history'; END IF;
 END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER interest_waiver_valid AFTER INSERT ON loan_interest_waiver DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION validate_interest_waiver();
CREATE CONSTRAINT TRIGGER interest_waiver_part_valid AFTER INSERT ON loan_interest_waiver_part DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION validate_interest_waiver();
CREATE CONSTRAINT TRIGGER interest_waiver_projection AFTER UPDATE ON loan_ledger_installments DEFERRABLE INITIALLY DEFERRED FOR EACH ROW WHEN(NEW.interest_waived IS DISTINCT FROM OLD.interest_waived) EXECUTE FUNCTION validate_interest_waiver();

CREATE FUNCTION validate_prepayment_terms_scope() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM loan_ledgers l WHERE l.loan_application_id=NEW.loan_id AND l.sacco_id=NEW.sacco_id AND l.station_id=NEW.station_id)
 THEN RAISE EXCEPTION 'Servicing terms must belong to the loan institution and branch'; END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER prepayment_terms_scope AFTER INSERT ON loan_prepayment_terms DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION validate_prepayment_terms_scope();
CREATE INDEX ix_interest_waiver_effective_date ON loan_interest_waiver(loan_id,effective_date DESC);

-- Extend the existing top-up checks without changing their scope, cash, account or independent-correction guards.
DO $$ DECLARE definition text; updated text; BEGIN
 SELECT pg_get_functiondef('loan_topup_validate(gl_journal)'::regprocedure) INTO definition;
 updated:=replace(definition,'(due_date<=e.effective_date AND interest_paid<>interest)',
   '(interest_paid<>loan_interest_earned(id,e.effective_date))');
 IF updated=definition THEN RAISE EXCEPTION 'Expected top-up interest settlement guard missing'; END IF;
 definition:=updated;
 updated:=replace(definition,'sum(interest-interest_paid),0) FROM loan_ledger_installments WHERE loan_application_id=o.id AND due_date>e.effective_date',
   'sum(interest-interest_paid-interest_excluded-interest_waived),0) FROM loan_ledger_installments WHERE loan_application_id=o.id');
 IF updated=definition THEN RAISE EXCEPTION 'Expected top-up future interest guard missing'; END IF;
 definition:=updated;
 updated:=replace(definition,'(i.due_date>CASE WHEN e.reverses_id IS NULL THEN e.effective_date ELSE original.effective_date END AND a.interest_amount<>0)',
   '(NOT EXISTS(SELECT 1 FROM loan_prepayment_terms WHERE loan_id=o.id) AND i.due_date>CASE WHEN e.reverses_id IS NULL THEN e.effective_date ELSE original.effective_date END AND a.interest_amount<>0)');
 IF updated=definition THEN RAISE EXCEPTION 'Expected top-up legacy allocation guard missing'; END IF;
 EXECUTE updated;
END $$;
