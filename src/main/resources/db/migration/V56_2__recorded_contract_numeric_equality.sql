-- Preserve contractual values while allowing equivalent JSON decimal scales during balance refresh.
CREATE OR REPLACE FUNCTION accountant_loan_terms_protect() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF EXISTS(SELECT 1 FROM accountant_loan_record WHERE loan_id=OLD.id) THEN
  IF TG_OP='DELETE' OR ROW(NEW.sacco_id,NEW.station_id,NEW.applicant_member_id,NEW.loan_type,NEW.loan_product_setting_id,NEW.amount,NEW.tenor_months,NEW.repayment_frequency,NEW.first_repayment_date,NEW.form_data,NEW.policy_snapshot)
   IS DISTINCT FROM ROW(OLD.sacco_id,OLD.station_id,OLD.applicant_member_id,OLD.loan_type,OLD.loan_product_setting_id,OLD.amount,OLD.tenor_months,OLD.repayment_frequency,OLD.first_repayment_date,OLD.form_data,OLD.policy_snapshot)
   OR jsonb_build_array(NEW.financial_snapshot->'calculationVersion',NEW.financial_snapshot->'principalAmount',NEW.financial_snapshot->'interestRate',NEW.financial_snapshot->'interestMethod',NEW.financial_snapshot->'repaymentFrequency',NEW.financial_snapshot->'tenorMonths',NEW.financial_snapshot->'numberOfPayments',NEW.financial_snapshot->'periodicRepaymentAmount',NEW.financial_snapshot->'maximumInstallmentAmount',NEW.financial_snapshot->'interestAmount',NEW.financial_snapshot->'recordingSource')
   IS DISTINCT FROM jsonb_build_array(OLD.financial_snapshot->'calculationVersion',OLD.financial_snapshot->'principalAmount',OLD.financial_snapshot->'interestRate',OLD.financial_snapshot->'interestMethod',OLD.financial_snapshot->'repaymentFrequency',OLD.financial_snapshot->'tenorMonths',OLD.financial_snapshot->'numberOfPayments',OLD.financial_snapshot->'periodicRepaymentAmount',OLD.financial_snapshot->'maximumInstallmentAmount',OLD.financial_snapshot->'interestAmount',OLD.financial_snapshot->'recordingSource')
  THEN RAISE EXCEPTION 'Recorded loan contract is immutable'; END IF;
  IF NEW.status IS DISTINCT FROM OLD.status AND NOT ((OLD.status='RECORDED' AND NEW.status='DISBURSED') OR (OLD.status IN ('DISBURSED','PAR','DEFAULTED','PAID') AND NEW.status IN ('DISBURSED','PAR','DEFAULTED','PAID')))
  THEN RAISE EXCEPTION 'Invalid recorded loan transition'; END IF;
 END IF; RETURN NEW;
END $$;
