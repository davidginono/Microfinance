-- Member attribution for earned-interest concessions; no cash, repricing or historical backfill.
CREATE TABLE lending_group_shared_waiver (
 waiver_id uuid NOT NULL REFERENCES loan_interest_waiver(id),cycle_id uuid NOT NULL,client_id uuid NOT NULL,
 sacco_id varchar(255) NOT NULL,station_id varchar(255) NOT NULL,amount numeric(18,2) NOT NULL CHECK(amount>=0),
 reverses_waiver_id uuid REFERENCES loan_interest_waiver(id),PRIMARY KEY(waiver_id,client_id),
 FOREIGN KEY(cycle_id,client_id) REFERENCES lending_group_shared_allocation(cycle_id,client_id),
 FOREIGN KEY(cycle_id,sacco_id,station_id) REFERENCES lending_group_shared_document(cycle_id,sacco_id,station_id)
);
CREATE INDEX ix_shared_waiver_balance ON lending_group_shared_waiver(cycle_id,client_id,waiver_id);
CREATE TRIGGER shared_waiver_immutable BEFORE UPDATE OR DELETE ON lending_group_shared_waiver FOR EACH ROW EXECUTE FUNCTION gl_protect_evidence();
CREATE FUNCTION lending_group_shared_waivers_complete(loan uuid,skip uuid DEFAULT NULL) RETURNS boolean LANGUAGE sql STABLE AS $$
 SELECT NOT EXISTS(
  SELECT 1 FROM loan_interest_waiver w JOIN lending_group_shared_facility f ON f.loan_id=w.loan_id
  LEFT JOIN LATERAL(SELECT count(*) n,sum(amount) amount FROM lending_group_shared_waiver x WHERE x.waiver_id=w.id) a ON true
  WHERE w.loan_id=loan AND w.id IS DISTINCT FROM skip
   AND (a.n<>(SELECT count(*) FROM lending_group_shared_allocation WHERE cycle_id=f.cycle_id) OR a.amount IS DISTINCT FROM w.amount))
$$;
CREATE FUNCTION lending_group_shared_waiver_insert() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE source record;
BEGIN
 SELECT w.*,w.xmin owner_xid,f.cycle_id INTO source FROM loan_interest_waiver w
 JOIN lending_group_shared_facility f ON f.loan_id=w.loan_id AND f.sacco_id=w.sacco_id AND f.station_id=w.station_id WHERE w.id=NEW.waiver_id;
 IF NOT FOUND OR source.owner_xid::text::numeric<>mod(pg_current_xact_id()::text::numeric,4294967296)
  OR ROW(NEW.cycle_id,NEW.sacco_id,NEW.station_id,NEW.reverses_waiver_id) IS DISTINCT FROM ROW(source.cycle_id,source.sacco_id,source.station_id,source.reverses_id)
 THEN RAISE EXCEPTION 'Shared interest waiver attribution requires its newly recorded scoped source'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER shared_waiver_source BEFORE INSERT ON lending_group_shared_waiver FOR EACH ROW EXECUTE FUNCTION lending_group_shared_waiver_insert();
CREATE FUNCTION lending_group_shared_waiver_projection(loan uuid) RETURNS void LANGUAGE plpgsql AS $$
DECLARE f lending_group_shared_facility;expected numeric;attributed numeric;
BEGIN
 SELECT * INTO f FROM lending_group_shared_facility WHERE loan_id=loan;IF f.cycle_id IS NULL THEN RETURN;END IF;
 SELECT coalesce(sum(interest_waived),0) INTO expected FROM loan_ledger_installments WHERE loan_application_id=loan;
 SELECT coalesce(sum(CASE WHEN w.kind='REVERSAL' THEN -x.amount ELSE x.amount END),0) INTO attributed
 FROM lending_group_shared_waiver x JOIN loan_interest_waiver w ON w.id=x.waiver_id WHERE x.cycle_id=f.cycle_id;
 IF NOT lending_group_shared_waivers_complete(loan) OR expected<>attributed
 THEN RAISE EXCEPTION 'Shared interest waivers must agree with their owning ledger and complete member evidence'; END IF;
 IF EXISTS(SELECT 1 FROM lending_group_shared_allocation s
  LEFT JOIN LATERAL(SELECT coalesce(sum(CASE WHEN w.kind='REVERSAL' THEN -x.amount ELSE x.amount END),0) amount
    FROM lending_group_shared_waiver x JOIN loan_interest_waiver w ON w.id=x.waiver_id WHERE x.cycle_id=s.cycle_id AND x.client_id=s.client_id) waived ON true
  LEFT JOIN LATERAL(SELECT coalesce(sum(CASE WHEN p.kind='REVERSAL' THEN -(x.interest+x.excluded_interest) ELSE x.interest+x.excluded_interest END),0) amount
    FROM lending_group_shared_movement x JOIN accountant_loan_post p ON p.id=x.posting_id WHERE x.cycle_id=s.cycle_id AND x.client_id=s.client_id) paid ON true
  WHERE s.cycle_id=f.cycle_id AND (waived.amount<0 OR waived.amount+paid.amount>s.interest))
 THEN RAISE EXCEPTION 'Shared waived interest cannot exceed a remaining member component'; END IF;
END $$;
CREATE FUNCTION lending_group_shared_waiver_commit() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE w loan_interest_waiver;f lending_group_shared_facility;
BEGIN
 SELECT * INTO w FROM loan_interest_waiver WHERE id=NEW.id;SELECT * INTO f FROM lending_group_shared_facility WHERE loan_id=w.loan_id;
 IF f.cycle_id IS NULL THEN RETURN NULL;END IF;
 IF NOT lending_group_shared_waivers_complete(w.loan_id) THEN RAISE EXCEPTION 'Shared interest waiver requires complete member attribution';END IF;
 IF w.kind='REVERSAL' THEN
  IF EXISTS(SELECT 1 FROM lending_group_shared_waiver x LEFT JOIN lending_group_shared_waiver o ON o.waiver_id=w.reverses_id AND o.client_id=x.client_id
    WHERE x.waiver_id=w.id AND ROW(x.amount,x.reverses_waiver_id) IS DISTINCT FROM ROW(o.amount,o.waiver_id))
  THEN RAISE EXCEPTION 'Shared waiver reversal must restore the original member amounts';END IF;
 ELSE
  IF EXISTS(WITH remaining AS(
    SELECT s.client_id,s.ordinal,s.interest-coalesce(paid.amount,0)-coalesce(waived.amount,0) interest
    FROM lending_group_shared_allocation s
    LEFT JOIN LATERAL(SELECT sum(CASE WHEN p.kind='REVERSAL' THEN -(x.interest+x.excluded_interest) ELSE x.interest+x.excluded_interest END) amount
      FROM lending_group_shared_movement x JOIN accountant_loan_post p ON p.id=x.posting_id JOIN loan_repayment_transactions t ON t.id=p.transaction_id
      WHERE x.cycle_id=s.cycle_id AND x.client_id=s.client_id AND t.sequence<w.sequence) paid ON true
    LEFT JOIN LATERAL(SELECT sum(CASE WHEN v.kind='REVERSAL' THEN -x.amount ELSE x.amount END) amount
      FROM lending_group_shared_waiver x JOIN loan_interest_waiver v ON v.id=x.waiver_id
      WHERE x.cycle_id=s.cycle_id AND x.client_id=s.client_id AND v.sequence<w.sequence) waived ON true
    WHERE s.cycle_id=f.cycle_id
   ),weights AS(SELECT *,sum(interest) OVER(ORDER BY ordinal) cumulative,sum(interest) OVER() total FROM remaining)
   SELECT 1 FROM weights e JOIN lending_group_shared_waiver x ON x.waiver_id=w.id AND x.client_id=e.client_id
   WHERE x.amount IS DISTINCT FROM coalesce(trunc(w.amount*e.cumulative/nullif(e.total,0),2),0)-coalesce(trunc(w.amount*(e.cumulative-e.interest)/nullif(e.total,0),2),0))
  THEN RAISE EXCEPTION 'Shared waived interest must follow the proportional remaining-interest rule';END IF;
 END IF;
 PERFORM lending_group_shared_waiver_projection(w.loan_id);RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER shared_interest_waiver_commit AFTER INSERT ON loan_interest_waiver DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION lending_group_shared_waiver_commit();
-- Validate future cash allocations against interest remaining after earlier concessions.
DO $$ DECLARE definition text;marker text;BEGIN
 SELECT pg_get_functiondef('lending_group_shared_movement_commit()'::regprocedure) INTO definition;
 marker:='s.interest-coalesce(a.interest,0)-coalesce(a.excluded,0) interest';
 IF strpos(definition,marker)=0 THEN RAISE EXCEPTION 'Shared remaining-interest marker missing';END IF;
 definition:=replace(definition,marker,$rule$s.interest-coalesce(a.interest,0)-coalesce(a.excluded,0)-coalesce((
  SELECT sum(CASE WHEN w.kind='REVERSAL' THEN -v.amount ELSE v.amount END) FROM lending_group_shared_waiver v JOIN loan_interest_waiver w ON w.id=v.waiver_id
  WHERE v.cycle_id=s.cycle_id AND v.client_id=s.client_id AND w.sequence<coalesce((SELECT sequence FROM loan_repayment_transactions WHERE id=p.transaction_id),9223372036854775807)),0) interest$rule$);
 EXECUTE definition;
 SELECT pg_get_functiondef('lending_group_shared_projection(uuid)'::regprocedure) INTO definition;
 marker:='SELECT * INTO l FROM loan_applications WHERE id=loan;';
 IF strpos(definition,marker)=0 THEN RAISE EXCEPTION 'Shared projection marker missing';END IF;
 EXECUTE replace(definition,marker,marker||E'\n PERFORM lending_group_shared_waiver_projection(loan);');
END $$;

CREATE OR REPLACE VIEW lending_shared_credit_obligation AS
SELECT f.loan_id,f.cycle_id,f.sacco_id,f.station_id,s.client_id,l.status,f.liability_rule,
  l.status IN ('DISBURSED','PAR','DEFAULTED','WRITTEN_OFF') funded,
  l.status NOT IN ('DISBURSED','PAR','DEFAULTED','WRITTEN_OFF') pending,
  CASE WHEN l.status IN ('DISBURSED','PAR','DEFAULTED','WRITTEN_OFF') THEN
    greatest(0,s.principal-paid.principal)+greatest(0,s.interest-paid.interest-paid.excluded-coalesce(waived.amount,0))+greatest(0,s.charges-s.deductions-paid.charges)
    ELSE s.principal+s.interest+s.charges END allocated_obligation,
  CASE WHEN f.liability_rule='JOINT_AND_SEVERAL' THEN least(s.liability_cap,
    CASE WHEN l.status IN ('DISBURSED','PAR','DEFAULTED','WRITTEN_OFF') THEN
      greatest(0,l.amount-b.principal_paid)+coalesce(schedule.interest,0)+coalesce(charges.outstanding,0)
      ELSE (f.snapshot->>'totalPayable')::numeric END)
    ELSE CASE WHEN l.status IN ('DISBURSED','PAR','DEFAULTED','WRITTEN_OFF') THEN
      greatest(0,s.principal-paid.principal)+greatest(0,s.interest-paid.interest-paid.excluded-coalesce(waived.amount,0))+greatest(0,s.charges-s.deductions-paid.charges)
      ELSE s.principal+s.interest+s.charges END END maximum_liability,
  CASE WHEN l.status IN ('DISBURSED','PAR','DEFAULTED','WRITTEN_OFF') AND
    s.principal=paid.principal AND s.interest=paid.interest+paid.excluded+coalesce(waived.amount,0) AND s.charges-s.deductions=paid.charges
    THEN 0 ELSE s.monthly_payment END monthly_payment,
  l.status IN ('DISBURSED','PAR','DEFAULTED','WRITTEN_OFF') AND
    (b.loan_application_id IS NULL OR execution.loan_id IS NULL OR coalesce(schedule.installments,0)=0
      OR NOT lending_group_shared_waivers_complete(f.loan_id) OR s.principal<paid.principal OR s.interest<paid.interest+paid.excluded+coalesce(waived.amount,0)
      OR s.charges-s.deductions<paid.charges) missing_history
FROM lending_group_shared_facility f JOIN lending_group_shared_allocation s ON s.cycle_id=f.cycle_id
JOIN loan_applications l ON l.id=f.loan_id
LEFT JOIN loan_ledgers b ON b.loan_application_id=f.loan_id
LEFT JOIN lending_group_shared_funding execution ON execution.loan_id=f.loan_id
LEFT JOIN LATERAL(SELECT outstanding FROM loan_charge_balance WHERE loan_id=f.loan_id AND sacco_id=f.sacco_id AND station_id=f.station_id OFFSET 0) charges ON true
LEFT JOIN LATERAL (
 SELECT coalesce(sum(CASE WHEN p.kind='REVERSAL' THEN -x.principal ELSE x.principal END),0) principal,
   coalesce(sum(CASE WHEN p.kind='REVERSAL' THEN -x.interest ELSE x.interest END),0) interest,
   coalesce(sum(CASE WHEN p.kind='REVERSAL' THEN -x.charges ELSE x.charges END),0) charges,
   coalesce(sum(CASE WHEN p.kind='REVERSAL' THEN -x.excluded_interest ELSE x.excluded_interest END),0) excluded
 FROM lending_group_shared_movement x JOIN accountant_loan_post p ON p.id=x.posting_id
 WHERE x.cycle_id=f.cycle_id AND x.client_id=s.client_id
) paid ON true
LEFT JOIN LATERAL(SELECT coalesce(sum(CASE WHEN w.kind='REVERSAL' THEN -x.amount ELSE x.amount END),0) amount
 FROM lending_group_shared_waiver x JOIN loan_interest_waiver w ON w.id=x.waiver_id WHERE x.cycle_id=f.cycle_id AND x.client_id=s.client_id) waived ON true
LEFT JOIN LATERAL (
 SELECT count(*) installments,sum(interest-interest_paid-interest_excluded-interest_waived) interest,sum(interest_waived) waived
 FROM loan_ledger_installments WHERE loan_application_id=f.loan_id
) schedule ON true
WHERE l.status NOT IN ('DRAFT','WITHDRAWN','PAID','DISBURSEMENT_REVERSED','MANAGER_REJECTED','LOAN_OFFICER_REJECTED',
  'CHAIRPERSON_REJECTED','BOARD_REJECTED','CREDIT_COMMITTEE_REJECTED','ACCOUNTANT_REJECTED','REJECTED');
