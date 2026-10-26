-- Extend the existing immutable receipt envelope to one shared parent payment.
-- Fifty input contributors and fifty beneficiaries produce at most ninety-nine attribution edges.
ALTER TABLE lending_group_contribution DROP CONSTRAINT lending_group_contribution_line_no_check;
ALTER TABLE lending_group_contribution ADD CONSTRAINT lending_group_contribution_line_no_check CHECK(line_no BETWEEN 1 AND 100);
ALTER TABLE lending_group_contribution ADD COLUMN source_line_no integer CHECK(source_line_no BETWEEN 1 AND 50);

CREATE OR REPLACE FUNCTION lending_group_receipt_commit() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE r lending_group_receipt;original lending_group_receipt;cnt integer;received numeric;contributions integer;
BEGIN
 IF TG_TABLE_NAME='lending_group_receipt' THEN SELECT * INTO r FROM lending_group_receipt WHERE id=NEW.id;
 ELSE SELECT * INTO r FROM lending_group_receipt WHERE id=NEW.receipt_id; END IF;
 SELECT count(*),sum(p.amount) INTO cnt,received FROM lending_group_receipt_loan x JOIN accountant_loan_post p ON p.id=x.posting_id WHERE x.receipt_id=r.id;
 SELECT count(*) INTO contributions FROM lending_group_contribution WHERE receipt_id=r.id;
 IF cnt<>r.transaction_count OR received IS DISTINCT FROM r.total OR contributions NOT BETWEEN 1 AND (CASE WHEN EXISTS(SELECT 1 FROM lending_group_cycle WHERE id=r.cycle_id AND model='SHARED') THEN 99 ELSE 50 END)
 OR (SELECT max(line_no) FROM lending_group_receipt_loan WHERE receipt_id=r.id)<>cnt
 OR (SELECT max(line_no) FROM lending_group_contribution WHERE receipt_id=r.id)<>contributions
 OR NOT EXISTS(SELECT 1 FROM lending_group_cycle c WHERE c.id=r.cycle_id AND c.state='SUBMITTED')
 OR NOT EXISTS(SELECT 1 FROM members m WHERE m.id=r.posted_by AND m.sacco_id=r.sacco_id AND m.station_id=r.station_id)
 OR NOT EXISTS(SELECT 1 FROM accounting_voucher_number_reservation n WHERE n.request_key=r.request_key AND n.sacco_id=r.sacco_id AND n.station_id=r.station_id
   AND n.voucher_type='RECEIPT' AND n.effective_date=r.effective_date AND n.voucher_number=r.number AND n.consumed_at IS NOT NULL)
 OR (SELECT count(DISTINCT p.loan_id) FROM lending_group_receipt_loan x JOIN accountant_loan_post p ON p.id=x.posting_id WHERE x.receipt_id=r.id)<>cnt
 OR EXISTS(SELECT 1 FROM lending_group_receipt_loan x JOIN accountant_loan_post p ON p.id=x.posting_id
   WHERE x.receipt_id=r.id AND (p.state<>'POSTED' OR p.actor_id<>r.posted_by OR p.effective_date<>r.effective_date OR p.money_account_id<>r.money_account_id
    OR (r.reverses_id IS NULL AND p.kind<>'PAYMENT') OR (r.reverses_id IS NOT NULL AND p.kind<>'REVERSAL')
    OR (NOT EXISTS(SELECT 1 FROM lending_group_participant a WHERE a.cycle_id=r.cycle_id AND a.loan_id=p.loan_id)
      AND NOT EXISTS(SELECT 1 FROM lending_group_shared_facility f WHERE f.cycle_id=r.cycle_id AND f.loan_id=p.loan_id AND f.sacco_id=r.sacco_id AND f.station_id=r.station_id))
    OR p.amount IS DISTINCT FROM (SELECT sum(c.amount) FROM lending_group_contribution c WHERE c.receipt_id=r.id AND c.loan_id=p.loan_id)
    OR p.principal_amount IS DISTINCT FROM (SELECT sum(c.principal) FROM lending_group_contribution c WHERE c.receipt_id=r.id AND c.loan_id=p.loan_id)
    OR p.interest_amount IS DISTINCT FROM (SELECT sum(c.interest) FROM lending_group_contribution c WHERE c.receipt_id=r.id AND c.loan_id=p.loan_id)
    OR p.amount-p.principal_amount-p.interest_amount IS DISTINCT FROM (SELECT sum(c.charges) FROM lending_group_contribution c WHERE c.receipt_id=r.id AND c.loan_id=p.loan_id)))
 OR EXISTS(SELECT 1 FROM lending_group_contribution c WHERE c.receipt_id=r.id AND
   (NOT EXISTS(SELECT 1 FROM lending_group_participant p WHERE p.cycle_id=r.cycle_id AND p.client_id=c.payer_id)
    OR (NOT EXISTS(SELECT 1 FROM lending_group_participant b JOIN loan_applications l ON l.id=b.loan_id WHERE b.cycle_id=r.cycle_id
       AND b.loan_id=c.loan_id AND b.client_id=c.beneficiary_id AND coalesce(l.loan_id,l.application_number::text)=c.loan_number)
     AND NOT EXISTS(SELECT 1 FROM lending_group_shared_facility f JOIN loan_applications l ON l.id=f.loan_id
       JOIN lending_group_shared_allocation b ON b.cycle_id=f.cycle_id WHERE f.cycle_id=r.cycle_id AND f.loan_id=c.loan_id
       AND b.client_id=c.beneficiary_id AND b.client_number=c.beneficiary_number AND b.client_name=c.beneficiary_name
       AND coalesce(l.loan_id,l.application_number::text)=c.loan_number))
    OR NOT EXISTS(SELECT 1 FROM lending_group_receipt_loan x JOIN accountant_loan_post p ON p.id=x.posting_id WHERE x.receipt_id=r.id AND p.loan_id=c.loan_id)))
 THEN RAISE EXCEPTION 'Group receipt, loan postings and contributions must agree'; END IF;
 IF EXISTS(SELECT 1 FROM lending_group_cycle WHERE id=r.cycle_id AND model='SHARED') THEN
  IF cnt<>1 OR EXISTS(
    SELECT 1 FROM lending_group_receipt_loan x JOIN lending_group_shared_movement m ON m.posting_id=x.posting_id
    WHERE x.receipt_id=r.id AND ROW(m.principal,m.interest,m.charges) IS DISTINCT FROM
      (SELECT ROW(coalesce(sum(c.principal),0),coalesce(sum(c.interest),0),coalesce(sum(c.charges),0)) FROM lending_group_contribution c
       WHERE c.receipt_id=r.id AND c.beneficiary_id=m.client_id))
   OR NOT EXISTS(SELECT 1 FROM lending_group_receipt_loan x JOIN lending_group_shared_movement m ON m.posting_id=x.posting_id WHERE x.receipt_id=r.id)
   OR EXISTS(SELECT 1 FROM lending_group_contribution c WHERE c.receipt_id=r.id AND (c.source_line_no IS NULL OR NOT EXISTS(
       SELECT 1 FROM lending_group_receipt_loan x JOIN lending_group_shared_movement m ON m.posting_id=x.posting_id
       WHERE x.receipt_id=r.id AND m.client_id=c.beneficiary_id)))
  THEN RAISE EXCEPTION 'Shared contribution components must match the signed member allocation rule'; END IF;
  IF r.reverses_id IS NULL THEN
   IF jsonb_typeof(r.input_data->'rows') IS DISTINCT FROM 'array' THEN RAISE EXCEPTION 'Shared receipt requires contributor source rows'; END IF;
   IF jsonb_array_length(r.input_data->'rows') NOT BETWEEN 1 AND 50
    OR EXISTS(SELECT 1 FROM jsonb_array_elements(r.input_data->'rows') WITH ORDINALITY AS source(value,line)
       WHERE (source.value->>'amount')::numeric IS DISTINCT FROM (SELECT sum(c.amount) FROM lending_group_contribution c WHERE c.receipt_id=r.id AND c.source_line_no=source.line)
        OR (source.value->>'amount')::numeric<=0)
    OR EXISTS(SELECT 1 FROM lending_group_contribution c WHERE c.receipt_id=r.id AND NOT EXISTS(
       SELECT 1 FROM jsonb_array_elements(r.input_data->'rows') WITH ORDINALITY AS source(value,line)
       WHERE source.line=c.source_line_no AND (source.value->>'payerId')::uuid=c.payer_id
        AND (source.value->>'loanId')::uuid=c.loan_id AND trim(source.value->>'description')=c.description))
   THEN RAISE EXCEPTION 'Shared contribution attribution must conserve each actual payer source row'; END IF;
  END IF;
 ELSE
  IF EXISTS(SELECT 1 FROM lending_group_contribution WHERE receipt_id=r.id AND source_line_no IS NOT NULL)
  THEN RAISE EXCEPTION 'Individual-loan contributions retain their original attribution'; END IF;
 END IF;
 IF r.reverses_id IS NULL THEN
  IF EXISTS(SELECT 1 FROM lending_group_contribution WHERE receipt_id=r.id AND reverses_id IS NOT NULL) THEN RAISE EXCEPTION 'New contributions cannot reverse history'; END IF;
 ELSE
  SELECT * INTO original FROM lending_group_receipt WHERE id=r.reverses_id;
  IF original.reverses_id IS NOT NULL OR original.posted_by=r.posted_by OR original.total<>r.total OR original.cycle_id<>r.cycle_id
   OR original.transaction_count<>cnt OR original.money_account_id<>r.money_account_id OR r.effective_date<original.effective_date
   OR ROW(r.remitter,r.evidence,r.group_code,r.group_name,r.reference) IS DISTINCT FROM ROW(original.remitter,original.evidence,original.group_code,original.group_name,original.reference)
   OR contributions<>(SELECT count(*) FROM lending_group_contribution WHERE receipt_id=original.id)
   OR EXISTS(SELECT 1 FROM lending_group_receipt_loan x JOIN accountant_loan_post p ON p.id=x.posting_id WHERE x.receipt_id=r.id
      AND NOT EXISTS(SELECT 1 FROM lending_group_receipt_loan y WHERE y.receipt_id=original.id AND y.posting_id=p.reverses_id))
   OR EXISTS(SELECT 1 FROM lending_group_contribution c LEFT JOIN lending_group_contribution o ON o.id=c.reverses_id
      WHERE c.receipt_id=r.id AND (o.receipt_id IS DISTINCT FROM original.id OR
       ROW(c.line_no,c.loan_id,c.loan_number,c.payer_id,c.payer_number,c.payer_name,c.beneficiary_id,c.beneficiary_number,c.beneficiary_name,c.description,c.amount,c.principal,c.interest,c.charges,c.source_line_no) IS DISTINCT FROM
       ROW(o.line_no,o.loan_id,o.loan_number,o.payer_id,o.payer_number,o.payer_name,o.beneficiary_id,o.beneficiary_number,o.beneficiary_name,o.description,o.amount,o.principal,o.interest,o.charges,o.source_line_no)))
  THEN RAISE EXCEPTION 'Group correction must independently reverse every original contribution and loan posting'; END IF;
 END IF;
 RETURN NULL;
END $$;
