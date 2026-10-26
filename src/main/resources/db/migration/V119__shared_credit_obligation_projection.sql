-- Credit-risk projection only. Member liability is not another receivable or portfolio asset.
CREATE VIEW lending_shared_credit_obligation AS
SELECT f.loan_id,f.cycle_id,f.sacco_id,f.station_id,s.client_id,l.status,f.liability_rule,
  l.status IN ('DISBURSED','PAR','DEFAULTED','WRITTEN_OFF') funded,
  l.status NOT IN ('DISBURSED','PAR','DEFAULTED','WRITTEN_OFF') pending,
  CASE WHEN l.status IN ('DISBURSED','PAR','DEFAULTED','WRITTEN_OFF') THEN
    greatest(0,s.principal-paid.principal)+greatest(0,s.interest-paid.interest-paid.excluded)+greatest(0,s.charges-s.deductions-paid.charges)
    ELSE s.principal+s.interest+s.charges END allocated_obligation,
  CASE WHEN f.liability_rule='JOINT_AND_SEVERAL' THEN least(s.liability_cap,
    CASE WHEN l.status IN ('DISBURSED','PAR','DEFAULTED','WRITTEN_OFF') THEN
      greatest(0,l.amount-b.principal_paid)+coalesce(schedule.interest,0)+coalesce(charges.outstanding,0)
      ELSE (f.snapshot->>'totalPayable')::numeric END)
    ELSE CASE WHEN l.status IN ('DISBURSED','PAR','DEFAULTED','WRITTEN_OFF') THEN
      greatest(0,s.principal-paid.principal)+greatest(0,s.interest-paid.interest-paid.excluded)+greatest(0,s.charges-s.deductions-paid.charges)
      ELSE s.principal+s.interest+s.charges END END maximum_liability,
  CASE WHEN l.status IN ('DISBURSED','PAR','DEFAULTED','WRITTEN_OFF') AND
    s.principal=paid.principal AND s.interest=paid.interest+paid.excluded AND s.charges-s.deductions=paid.charges
    THEN 0 ELSE s.monthly_payment END monthly_payment,
  l.status IN ('DISBURSED','PAR','DEFAULTED','WRITTEN_OFF') AND
    (b.loan_application_id IS NULL OR execution.loan_id IS NULL OR coalesce(schedule.installments,0)=0
      OR coalesce(schedule.waived,0)<>0 OR s.principal<paid.principal OR s.interest<paid.interest+paid.excluded
      OR s.charges-s.deductions<paid.charges) missing_history
FROM lending_group_shared_facility f JOIN lending_group_shared_allocation s ON s.cycle_id=f.cycle_id
JOIN loan_applications l ON l.id=f.loan_id
LEFT JOIN loan_ledgers b ON b.loan_application_id=f.loan_id
LEFT JOIN lending_group_shared_funding execution ON execution.loan_id=f.loan_id
LEFT JOIN loan_charge_balance charges ON charges.loan_id=f.loan_id
LEFT JOIN LATERAL (
 SELECT coalesce(sum(CASE WHEN p.kind='REVERSAL' THEN -x.principal ELSE x.principal END),0) principal,
   coalesce(sum(CASE WHEN p.kind='REVERSAL' THEN -x.interest ELSE x.interest END),0) interest,
   coalesce(sum(CASE WHEN p.kind='REVERSAL' THEN -x.charges ELSE x.charges END),0) charges,
   coalesce(sum(CASE WHEN p.kind='REVERSAL' THEN -x.excluded_interest ELSE x.excluded_interest END),0) excluded
 FROM lending_group_shared_movement x JOIN accountant_loan_post p ON p.id=x.posting_id
 WHERE x.cycle_id=f.cycle_id AND x.client_id=s.client_id
) paid ON true
LEFT JOIN LATERAL (
 SELECT count(*) installments,sum(interest-interest_paid-interest_excluded-interest_waived) interest,sum(interest_waived) waived
 FROM loan_ledger_installments WHERE loan_application_id=f.loan_id
) schedule ON true
WHERE l.status NOT IN ('DRAFT','WITHDRAWN','PAID','DISBURSEMENT_REVERSED','MANAGER_REJECTED','LOAN_OFFICER_REJECTED',
  'CHAIRPERSON_REJECTED','BOARD_REJECTED','CREDIT_COMMITTEE_REJECTED','ACCOUNTANT_REJECTED','REJECTED');
