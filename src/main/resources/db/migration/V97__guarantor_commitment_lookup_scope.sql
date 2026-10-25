-- Keep scope predicates visible to PostgreSQL before commitment aggregation.
CREATE OR REPLACE VIEW lending_guarantor_commitment AS
 WITH ordinary AS (
  SELECT g.guarantor_member_id guarantor_id,l.id loan_id,l.sacco_id,l.station_id,l.amount principal_amount,
   CASE WHEN l.amount>0 AND terms.interest>=0
    AND ((r.loan_id IS NULL AND NOT coalesce(l.financial_snapshot ? 'recordedChargeTotal',false)) OR
      terms.charges>=0)
    THEN l.amount+terms.interest+coalesce(terms.charges,0) END amount_cap
  FROM guarantor_requests g JOIN loan_applications l ON l.id=g.loan_application_id
  LEFT JOIN accountant_loan_record r ON r.loan_id=l.id
  CROSS JOIN LATERAL(SELECT
   CASE WHEN jsonb_typeof(l.financial_snapshot->'interestAmount')='number' THEN (l.financial_snapshot->>'interestAmount')::numeric END interest,
   CASE WHEN jsonb_typeof(l.financial_snapshot->'recordedChargeTotal')='number' THEN (l.financial_snapshot->>'recordedChargeTotal')::numeric END charges) terms
  WHERE g.status='APPROVED' AND g.guarantor_member_id IS NOT NULL
   AND l.status NOT IN ('DRAFT','PAID','DISBURSEMENT_REVERSED','MANAGER_REJECTED','LOAN_OFFICER_REJECTED','CHAIRPERSON_REJECTED','BOARD_REJECTED','CREDIT_COMMITTEE_REJECTED','ACCOUNTANT_REJECTED','REJECTED')
 ), combined AS (
  SELECT * FROM ordinary
  UNION ALL
  SELECT g.guarantor_id,l.id,g.sacco_id,g.station_id,l.amount,g.amount_cap
  FROM lending_group_guarantee g JOIN loan_applications l ON l.id=g.loan_id AND l.sacco_id=g.sacco_id AND l.station_id=g.station_id
  JOIN lending_group_cycle c ON c.id=g.cycle_id
  WHERE c.state='SUBMITTED'
   AND l.status NOT IN ('PAID','DISBURSEMENT_REVERSED','MANAGER_REJECTED','LOAN_OFFICER_REJECTED','CHAIRPERSON_REJECTED','BOARD_REJECTED','CREDIT_COMMITTEE_REJECTED','ACCOUNTANT_REJECTED','REJECTED')
 )
 SELECT guarantor_id,loan_id,sacco_id,station_id,max(principal_amount) principal_amount,
  CASE WHEN bool_or(amount_cap IS NULL) THEN NULL ELSE max(amount_cap) END amount_cap
 FROM combined GROUP BY guarantor_id,loan_id,sacco_id,station_id;
CREATE INDEX ix_group_credit_client ON lending_group_guarantee(guarantor_id,loan_id);
