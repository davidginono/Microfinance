-- Staff-recorded applications use the existing review stages and one teller posting transaction.
ALTER TABLE loan_product_settings ADD COLUMN custom_recording_configuration boolean NOT NULL DEFAULT false;
CREATE UNIQUE INDEX ux_custom_recording_configuration ON loan_product_settings(sacco_id) WHERE custom_recording_configuration;
CREATE FUNCTION recorded_pending_status(stage text) RETURNS text LANGUAGE sql IMMUTABLE AS $$
 SELECT CASE stage WHEN 'MANAGER' THEN 'READY_FOR_MANAGER' WHEN 'LOAN_OFFICER' THEN 'AWAITING_LOAN_OFFICER'
 WHEN 'CHAIRPERSON' THEN 'AWAITING_CHAIRPERSON' WHEN 'BOARD' THEN 'AWAITING_BOARD' WHEN 'CREDIT_COMMITTEE' THEN 'AWAITING_CREDIT_COMMITTEE'
 WHEN 'ACCOUNTANT' THEN 'AWAITING_ACCOUNTANT' WHEN 'DISBURSEMENT_OFFICER' THEN 'READY_FOR_DISBURSEMENT' END
$$;
CREATE FUNCTION recorded_review_transition(previous text, following text, policy jsonb) RETURNS boolean LANGUAGE plpgsql AS $$
DECLARE stage text; position integer; rejected text;
BEGIN
 IF previous='RECORDED' THEN RETURN coalesce(following=recorded_pending_status(policy->'approvalFlow'->>0),false); END IF;
 IF previous='READY_FOR_DISBURSEMENT' THEN RETURN following='DISBURSED'; END IF;
 stage := CASE previous WHEN 'READY_FOR_MANAGER' THEN 'MANAGER' WHEN 'AWAITING_LOAN_OFFICER' THEN 'LOAN_OFFICER'
  WHEN 'AWAITING_CHAIRPERSON' THEN 'CHAIRPERSON' WHEN 'AWAITING_BOARD' THEN 'BOARD'
  WHEN 'AWAITING_CREDIT_COMMITTEE' THEN 'CREDIT_COMMITTEE' WHEN 'AWAITING_ACCOUNTANT' THEN 'ACCOUNTANT' END;
 IF stage IS NULL THEN RETURN false; END IF;
 rejected := stage||'_REJECTED'; IF following=rejected THEN RETURN true; END IF;
 SELECT ordinal::integer INTO position FROM jsonb_array_elements_text(policy->'approvalFlow') WITH ORDINALITY f(value,ordinal) WHERE value=stage;
 IF position IS NULL THEN RETURN false; END IF;
 RETURN coalesce(following=recorded_pending_status(policy->'approvalFlow'->>position),false);
END $$;
CREATE OR REPLACE FUNCTION accountant_loan_terms_protect() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF EXISTS(SELECT 1 FROM accountant_loan_record WHERE loan_id=OLD.id) THEN
  IF TG_OP='DELETE' OR ROW(NEW.sacco_id,NEW.station_id,NEW.applicant_member_id,NEW.loan_type,NEW.amount,NEW.tenor_months,NEW.repayment_frequency,NEW.form_data)
   IS DISTINCT FROM ROW(OLD.sacco_id,OLD.station_id,OLD.applicant_member_id,OLD.loan_type,OLD.amount,OLD.tenor_months,OLD.repayment_frequency,OLD.form_data)
   OR jsonb_build_array(NEW.financial_snapshot->'calculationVersion',NEW.financial_snapshot->'principalAmount',NEW.financial_snapshot->'interestRate',NEW.financial_snapshot->'interestMethod',NEW.financial_snapshot->'repaymentFrequency',NEW.financial_snapshot->'tenorMonths',NEW.financial_snapshot->'numberOfPayments',NEW.financial_snapshot->'periodicRepaymentAmount',NEW.financial_snapshot->'maximumInstallmentAmount',NEW.financial_snapshot->'interestAmount',NEW.financial_snapshot->'recordingSource',NEW.financial_snapshot->'tenureUnit',NEW.financial_snapshot->'tenureValue',NEW.financial_snapshot->'loanMode',NEW.financial_snapshot->'interestEntry',NEW.financial_snapshot->'interestBasis',NEW.financial_snapshot->'interestPercent')
   IS DISTINCT FROM jsonb_build_array(OLD.financial_snapshot->'calculationVersion',OLD.financial_snapshot->'principalAmount',OLD.financial_snapshot->'interestRate',OLD.financial_snapshot->'interestMethod',OLD.financial_snapshot->'repaymentFrequency',OLD.financial_snapshot->'tenorMonths',OLD.financial_snapshot->'numberOfPayments',OLD.financial_snapshot->'periodicRepaymentAmount',OLD.financial_snapshot->'maximumInstallmentAmount',OLD.financial_snapshot->'interestAmount',OLD.financial_snapshot->'recordingSource',OLD.financial_snapshot->'tenureUnit',OLD.financial_snapshot->'tenureValue',OLD.financial_snapshot->'loanMode',OLD.financial_snapshot->'interestEntry',OLD.financial_snapshot->'interestBasis',OLD.financial_snapshot->'interestPercent')
  OR (NEW.first_repayment_date IS DISTINCT FROM OLD.first_repayment_date AND NOT (OLD.status='READY_FOR_DISBURSEMENT' AND NEW.status='DISBURSED' AND OLD.first_repayment_date IS NULL AND NEW.first_repayment_date>NEW.disbursement_date))
  THEN RAISE EXCEPTION 'Recorded loan contract is immutable'; END IF;
  IF (NEW.policy_snapshot IS DISTINCT FROM OLD.policy_snapshot OR NEW.loan_product_setting_id IS DISTINCT FROM OLD.loan_product_setting_id)
     AND NOT (OLD.status='RECORDED' AND recorded_review_transition(OLD.status,NEW.status,NEW.policy_snapshot::jsonb)
        AND (NEW.loan_product_setting_id IS NOT DISTINCT FROM OLD.loan_product_setting_id OR (OLD.loan_product_setting_id IS NULL AND EXISTS(SELECT 1 FROM loan_product_settings p WHERE p.id=NEW.loan_product_setting_id AND p.sacco_id=NEW.sacco_id AND p.custom_recording_configuration))))
  THEN RAISE EXCEPTION 'Recorded loan workflow is immutable after submission'; END IF;
  IF NEW.status IS DISTINCT FROM OLD.status AND NOT (recorded_review_transition(OLD.status,NEW.status,NEW.policy_snapshot::jsonb) OR (OLD.status IN ('DISBURSED','PAR','DEFAULTED','PAID') AND NEW.status IN ('DISBURSED','PAR','DEFAULTED','PAID')))
  THEN RAISE EXCEPTION 'Invalid recorded loan transition'; END IF;
 END IF; RETURN NEW;
END $$;


CREATE FUNCTION recorded_application_funding_commit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF NEW.status='DISBURSED' AND OLD.status IS DISTINCT FROM NEW.status AND EXISTS(SELECT 1 FROM accountant_loan_record WHERE loan_id=NEW.id)
 AND NOT EXISTS(SELECT 1 FROM accountant_loan_post p JOIN gl_journal j ON j.id=p.journal_id WHERE p.loan_id=NEW.id AND p.sacco_id=NEW.sacco_id AND p.station_id=NEW.station_id
  AND p.kind='DISBURSEMENT' AND p.state='POSTED' AND j.state='POSTED' AND p.amount=NEW.amount AND p.effective_date=NEW.disbursement_date)
 THEN RAISE EXCEPTION 'Recorded application disbursement requires its posted journal'; END IF; RETURN NEW;
END $$;
CREATE CONSTRAINT TRIGGER recorded_application_funding AFTER UPDATE ON loan_applications DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION recorded_application_funding_commit();
