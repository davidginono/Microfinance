-- Accounting attribution only: write-off never pays or releases a member's debt.
CREATE TABLE lending_group_shared_adjustment (
 request_id uuid NOT NULL REFERENCES loan_adjustment_decision(request_id),cycle_id uuid NOT NULL,client_id uuid NOT NULL,
 sacco_id varchar(255) NOT NULL,station_id varchar(255) NOT NULL,amount numeric(18,2) NOT NULL CHECK(amount>=0),
 reverses_request_id uuid REFERENCES loan_adjustment_request(id),PRIMARY KEY(request_id,client_id),
 FOREIGN KEY(cycle_id,client_id) REFERENCES lending_group_shared_allocation(cycle_id,client_id),
 FOREIGN KEY(cycle_id,sacco_id,station_id) REFERENCES lending_group_shared_document(cycle_id,sacco_id,station_id)
);
CREATE INDEX ix_shared_adjustment_balance ON lending_group_shared_adjustment(cycle_id,client_id,request_id);
CREATE TRIGGER shared_adjustment_immutable BEFORE UPDATE OR DELETE ON lending_group_shared_adjustment FOR EACH ROW EXECUTE FUNCTION gl_protect_evidence();
CREATE FUNCTION lending_group_shared_adjustments_complete(loan uuid,skip uuid DEFAULT NULL) RETURNS boolean LANGUAGE sql STABLE AS $$
 SELECT NOT EXISTS(SELECT 1 FROM loan_adjustment_request q JOIN loan_adjustment_decision d ON d.request_id=q.id AND d.decision='APPROVED'
 LEFT JOIN gl_journal j ON j.id=d.journal_id JOIN lending_group_shared_facility f ON f.loan_id=q.loan_id
 LEFT JOIN LATERAL(SELECT count(*) n,sum(amount) amount FROM lending_group_shared_adjustment x WHERE x.request_id=q.id) a ON true
 WHERE q.loan_id=loan AND q.kind IN ('WRITE_OFF','WRITE_BACK') AND q.id IS DISTINCT FROM skip
 AND (j.state IS DISTINCT FROM 'POSTED' OR a.n<>(SELECT count(*) FROM lending_group_shared_allocation WHERE cycle_id=f.cycle_id) OR a.amount IS DISTINCT FROM q.amount))
$$;
CREATE FUNCTION lending_group_shared_adjustment_insert() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE source record;
BEGIN
 SELECT q.*,d.xmin owner_xid,d.decision,j.state,f.cycle_id,original.request_id original_id INTO source
 FROM loan_adjustment_request q JOIN loan_adjustment_decision d ON d.request_id=q.id JOIN gl_journal j ON j.id=d.journal_id
 JOIN lending_group_shared_facility f ON f.loan_id=q.loan_id AND f.sacco_id=q.sacco_id AND f.station_id=q.station_id
 LEFT JOIN loan_adjustment_decision original ON original.journal_id=q.reverses_journal_id WHERE q.id=NEW.request_id;
 IF NOT FOUND OR source.owner_xid::text::numeric<>mod(pg_current_xact_id()::text::numeric,4294967296)
 OR source.decision<>'APPROVED' OR source.state<>'POSTED' OR source.kind NOT IN ('WRITE_OFF','WRITE_BACK')
 OR ROW(NEW.cycle_id,NEW.sacco_id,NEW.station_id,NEW.reverses_request_id) IS DISTINCT FROM ROW(source.cycle_id,source.sacco_id,source.station_id,source.original_id)
 THEN RAISE EXCEPTION 'Shared accounting attribution requires its newly posted scoped decision';END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER shared_adjustment_source BEFORE INSERT ON lending_group_shared_adjustment FOR EACH ROW EXECUTE FUNCTION lending_group_shared_adjustment_insert();
CREATE FUNCTION lending_group_shared_adjustment_projection(loan uuid) RETURNS void LANGUAGE plpgsql AS $$
DECLARE f lending_group_shared_facility;expected numeric;attributed numeric;
BEGIN
 SELECT * INTO f FROM lending_group_shared_facility WHERE loan_id=loan;IF f.cycle_id IS NULL THEN RETURN;END IF;
 SELECT written_off INTO expected FROM loan_adjustment_balance WHERE loan_id=loan;
 SELECT coalesce(sum(CASE WHEN q.kind='WRITE_BACK' THEN -x.amount ELSE x.amount END),0) INTO attributed
 FROM lending_group_shared_adjustment x JOIN loan_adjustment_request q ON q.id=x.request_id WHERE x.cycle_id=f.cycle_id;
 IF NOT lending_group_shared_adjustments_complete(loan) OR coalesce(expected,0)<>attributed
 THEN RAISE EXCEPTION 'Shared accounting write-offs must agree with their one owning book balance';END IF;
 IF EXISTS(SELECT 1 FROM lending_group_shared_allocation s
 LEFT JOIN LATERAL(SELECT coalesce(sum(CASE WHEN q.kind='WRITE_BACK' THEN -x.amount ELSE x.amount END),0) amount
 FROM lending_group_shared_adjustment x JOIN loan_adjustment_request q ON q.id=x.request_id WHERE x.cycle_id=s.cycle_id AND x.client_id=s.client_id) a ON true
 LEFT JOIN LATERAL(SELECT coalesce(sum(CASE WHEN p.kind='REVERSAL' THEN -x.principal ELSE x.principal END),0) amount
 FROM lending_group_shared_movement x JOIN accountant_loan_post p ON p.id=x.posting_id WHERE x.cycle_id=s.cycle_id AND x.client_id=s.client_id) paid ON true
 WHERE s.cycle_id=f.cycle_id AND (a.amount<0 OR a.amount>s.principal-paid.amount))
 THEN RAISE EXCEPTION 'Shared accounting write-off cannot exceed a member principal allocation';END IF;
END $$;
CREATE FUNCTION lending_group_shared_adjustment_commit() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE q loan_adjustment_request;f lending_group_shared_facility;
BEGIN
 SELECT * INTO q FROM loan_adjustment_request WHERE id=NEW.request_id;SELECT * INTO f FROM lending_group_shared_facility WHERE loan_id=q.loan_id;
 IF f.cycle_id IS NULL OR NEW.decision<>'APPROVED' OR q.kind NOT IN ('WRITE_OFF','WRITE_BACK') THEN RETURN NULL;END IF;
 IF NOT EXISTS(SELECT 1 FROM gl_journal WHERE id=NEW.journal_id AND state='POSTED') THEN RAISE EXCEPTION 'Shared accounting decision requires its posted source journal';END IF;
 IF NOT lending_group_shared_adjustments_complete(q.loan_id) THEN RAISE EXCEPTION 'Shared accounting adjustment requires complete member attribution';END IF;
 IF q.kind='WRITE_BACK' THEN
  IF EXISTS(SELECT 1 FROM lending_group_shared_adjustment x
   LEFT JOIN loan_adjustment_decision original ON original.journal_id=q.reverses_journal_id
   LEFT JOIN lending_group_shared_adjustment o ON o.request_id=original.request_id AND o.client_id=x.client_id
   WHERE x.request_id=q.id AND ROW(x.amount,x.reverses_request_id) IS DISTINCT FROM ROW(o.amount,o.request_id))
  THEN RAISE EXCEPTION 'Shared write-back must restore the original member accounting amounts';END IF;
 ELSE
  IF EXISTS(SELECT 1 FROM lending_group_shared_allocation s JOIN lending_group_shared_adjustment x ON x.client_id=s.client_id AND x.cycle_id=s.cycle_id AND x.request_id=q.id
   LEFT JOIN LATERAL(SELECT coalesce(sum(CASE WHEN p.kind='REVERSAL' THEN -m.principal ELSE m.principal END),0) amount
   FROM lending_group_shared_movement m JOIN accountant_loan_post p ON p.id=m.posting_id JOIN loan_repayment_transactions t ON t.id=p.transaction_id
   WHERE m.cycle_id=s.cycle_id AND m.client_id=s.client_id AND t.sequence<q.repayment_sequence) paid ON true
   WHERE s.cycle_id=f.cycle_id AND x.amount IS DISTINCT FROM s.principal-paid.amount)
  THEN RAISE EXCEPTION 'Shared write-off must partition the remaining principal exactly';END IF;
 END IF;
 PERFORM lending_group_shared_adjustment_projection(q.loan_id);RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER shared_accounting_adjustment_commit AFTER INSERT ON loan_adjustment_decision DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION lending_group_shared_adjustment_commit();
DO $$ DECLARE definition text;marker text;BEGIN
 SELECT pg_get_functiondef('lending_group_shared_projection(uuid)'::regprocedure) INTO definition;
 marker:='PERFORM lending_group_shared_waiver_projection(loan);';
 IF strpos(definition,marker)=0 THEN RAISE EXCEPTION 'Shared waiver projection marker missing';END IF;
 EXECUTE replace(definition,marker,marker||E'\n PERFORM lending_group_shared_adjustment_projection(loan);');
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
      OR NOT lending_group_shared_waivers_complete(f.loan_id) OR NOT lending_group_shared_adjustments_complete(f.loan_id) OR s.principal<paid.principal OR s.interest<paid.interest+paid.excluded+coalesce(waived.amount,0)
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
