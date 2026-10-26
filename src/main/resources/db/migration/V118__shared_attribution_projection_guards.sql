-- Prevent an owning ledger/charge mutation from bypassing member attribution.
CREATE FUNCTION lending_group_shared_projection(loan uuid) RETURNS void LANGUAGE plpgsql AS $$
DECLARE f lending_group_shared_facility;b loan_ledgers;l loan_applications;a record;
BEGIN
 SELECT * INTO f FROM lending_group_shared_facility WHERE loan_id=loan;
 IF f.cycle_id IS NULL THEN RETURN; END IF;
 SELECT * INTO b FROM loan_ledgers WHERE loan_application_id=loan;
 IF b.loan_application_id IS NULL THEN RETURN; END IF;
 SELECT * INTO l FROM loan_applications WHERE id=loan;
 SELECT coalesce(sum(CASE WHEN p.kind='REVERSAL' THEN -x.principal ELSE x.principal END),0) principal,
   coalesce(sum(CASE WHEN p.kind='REVERSAL' THEN -x.interest ELSE x.interest END),0) interest,
   coalesce(sum(CASE WHEN p.kind='REVERSAL' THEN -x.charges ELSE x.charges END),0) charges,
   coalesce(sum(CASE WHEN p.kind='REVERSAL' THEN -x.excluded_interest ELSE x.excluded_interest END),0) excluded
 INTO a FROM lending_group_shared_movement x JOIN accountant_loan_post p ON p.id=x.posting_id WHERE x.cycle_id=f.cycle_id;
 IF (l.status<>'DISBURSEMENT_REVERSED' AND b.principal_paid<>a.principal) OR b.interest_paid<>a.interest
   OR a.excluded<>(SELECT coalesce(sum(interest_excluded),0) FROM loan_ledger_installments WHERE loan_application_id=loan)
   OR (l.status<>'DISBURSEMENT_REVERSED' AND EXISTS(SELECT 1 FROM loan_charge_balance c WHERE c.loan_id=loan
     AND c.outstanding<>(f.snapshot->>'charges')::numeric-(f.snapshot->>'deductions')::numeric-a.charges))
 THEN RAISE EXCEPTION 'Shared member movements must agree with authoritative ledger and charge counters'; END IF;
END $$;
CREATE FUNCTION lending_group_shared_projection_commit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF TG_TABLE_NAME='loan_ledgers' THEN PERFORM lending_group_shared_projection(NEW.loan_application_id);
 ELSE PERFORM lending_group_shared_projection(NEW.loan_id); END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER shared_ledger_projection AFTER INSERT OR UPDATE ON loan_ledgers DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION lending_group_shared_projection_commit();
CREATE CONSTRAINT TRIGGER shared_charge_projection AFTER INSERT ON loan_charge_event DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION lending_group_shared_projection_commit();
DO $$ DECLARE definition text;marker text; BEGIN
 SELECT pg_get_functiondef('lending_group_shared_movement_commit()'::regprocedure) INTO definition;
 marker=' IF EXISTS(SELECT 1 FROM lending_group_shared_allocation';
 IF strpos(definition,marker)=0 THEN RAISE EXCEPTION 'Shared movement conservation marker missing'; END IF;
 definition=replace(definition,marker,$guard$
 PERFORM lending_group_shared_projection(p.loan_id);
 IF p.kind='PAYMENT' AND EXISTS(
   WITH balances AS (
     SELECT s.client_id,s.ordinal,s.principal-coalesce(a.principal,0) principal,
       s.interest-coalesce(a.interest,0)-coalesce(a.excluded,0) interest,s.charges-s.deductions-coalesce(a.charges,0) charges
     FROM lending_group_shared_allocation s LEFT JOIN LATERAL (
       SELECT sum(CASE WHEN t.kind='REVERSAL' THEN -x.principal ELSE x.principal END) principal,
         sum(CASE WHEN t.kind='REVERSAL' THEN -x.interest ELSE x.interest END) interest,
         sum(CASE WHEN t.kind='REVERSAL' THEN -x.charges ELSE x.charges END) charges,
         sum(CASE WHEN t.kind='REVERSAL' THEN -x.excluded_interest ELSE x.excluded_interest END) excluded
       FROM lending_group_shared_movement x JOIN accountant_loan_post t ON t.id=x.posting_id
       WHERE x.cycle_id=s.cycle_id AND x.client_id=s.client_id AND x.posting_id<>p.id) a ON true WHERE s.cycle_id=f.cycle_id
   ), weights AS (
     SELECT *,sum(principal) OVER (ORDER BY ordinal) pc,sum(principal) OVER () pt,
       sum(interest) OVER (ORDER BY ordinal) ic,sum(interest) OVER () it,sum(charges) OVER (ORDER BY ordinal) cc,sum(charges) OVER () ct FROM balances
   ), expected AS (
     SELECT *,coalesce(trunc(p.principal_amount*pc/nullif(pt,0),2),0)-coalesce(trunc(p.principal_amount*(pc-principal)/nullif(pt,0),2),0) ep,
       coalesce(trunc(p.interest_amount*ic/nullif(it,0),2),0)-coalesce(trunc(p.interest_amount*(ic-interest)/nullif(it,0),2),0) ei,
       coalesce(trunc(p.charge_amount*cc/nullif(ct,0),2),0)-coalesce(trunc(p.charge_amount*(cc-charges)/nullif(ct,0),2),0) ec FROM weights
   ), exclusions AS (
     SELECT *,sum(interest-ei) OVER (ORDER BY ordinal) xc,sum(interest-ei) OVER () xt FROM expected
   ) SELECT 1 FROM exclusions e JOIN lending_group_shared_movement x ON x.posting_id=p.id AND x.client_id=e.client_id
     WHERE ROW(x.principal,x.interest,x.charges,x.excluded_interest) IS DISTINCT FROM ROW(e.ep,e.ei,e.ec,
       coalesce(trunc(total.excluded*e.xc/nullif(e.xt,0),2),0)-coalesce(trunc(total.excluded*(e.xc-e.interest+e.ei)/nullif(e.xt,0),2),0))
 ) THEN RAISE EXCEPTION 'Shared member components must follow their signed proportional remaining-share rule'; END IF;
$guard$||marker);
 EXECUTE definition;
END $$;
