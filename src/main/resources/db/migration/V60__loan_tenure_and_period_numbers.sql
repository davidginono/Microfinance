-- New tenure values and disbursement numbering; historical contracts remain intact.
ALTER TABLE loan_product_settings ADD COLUMN tenure_unit varchar(8), ADD COLUMN min_tenure integer, ADD COLUMN max_tenure integer;
ALTER TABLE loan_product_settings ADD CONSTRAINT loan_product_tenure CHECK (tenure_unit IS NULL OR (tenure_unit IN ('DAYS','MONTHS','YEARS') AND min_tenure>0 AND max_tenure>=min_tenure));
ALTER TABLE accountant_loan_record ALTER COLUMN first_payment_date DROP NOT NULL;
ALTER TABLE loan_applications ADD COLUMN loan_accounting_period_id uuid REFERENCES accounting_period(id);
DROP INDEX ux_loan_applications_sacco_loanid;
CREATE UNIQUE INDEX ux_loan_legacy_number ON loan_applications(sacco_id,loan_id) WHERE loan_id IS NOT NULL AND loan_accounting_period_id IS NULL;
CREATE UNIQUE INDEX ux_loan_period_number ON loan_applications(sacco_id,loan_accounting_period_id,loan_id) WHERE loan_accounting_period_id IS NOT NULL;
CREATE TABLE loan_period_counter(sacco_id varchar(255) NOT NULL REFERENCES registered_saccos(sacco_id),period_id uuid NOT NULL REFERENCES accounting_period(id),last_number bigint NOT NULL CHECK(last_number>0),PRIMARY KEY(sacco_id,period_id));
CREATE OR REPLACE FUNCTION accountant_loan_terms_protect() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF EXISTS(SELECT 1 FROM accountant_loan_record WHERE loan_id=OLD.id) THEN
  IF TG_OP='DELETE' OR ROW(NEW.sacco_id,NEW.station_id,NEW.applicant_member_id,NEW.loan_type,NEW.loan_product_setting_id,NEW.amount,NEW.tenor_months,NEW.repayment_frequency,NEW.form_data,NEW.policy_snapshot)
   IS DISTINCT FROM ROW(OLD.sacco_id,OLD.station_id,OLD.applicant_member_id,OLD.loan_type,OLD.loan_product_setting_id,OLD.amount,OLD.tenor_months,OLD.repayment_frequency,OLD.form_data,OLD.policy_snapshot)
   OR jsonb_build_array(NEW.financial_snapshot->'calculationVersion',NEW.financial_snapshot->'principalAmount',NEW.financial_snapshot->'interestRate',NEW.financial_snapshot->'interestMethod',NEW.financial_snapshot->'repaymentFrequency',NEW.financial_snapshot->'tenorMonths',NEW.financial_snapshot->'numberOfPayments',NEW.financial_snapshot->'periodicRepaymentAmount',NEW.financial_snapshot->'maximumInstallmentAmount',NEW.financial_snapshot->'interestAmount',NEW.financial_snapshot->'recordingSource',NEW.financial_snapshot->'tenureUnit',NEW.financial_snapshot->'tenureValue',NEW.financial_snapshot->'loanMode',NEW.financial_snapshot->'interestEntry',NEW.financial_snapshot->'interestBasis',NEW.financial_snapshot->'interestPercent')
   IS DISTINCT FROM jsonb_build_array(OLD.financial_snapshot->'calculationVersion',OLD.financial_snapshot->'principalAmount',OLD.financial_snapshot->'interestRate',OLD.financial_snapshot->'interestMethod',OLD.financial_snapshot->'repaymentFrequency',OLD.financial_snapshot->'tenorMonths',OLD.financial_snapshot->'numberOfPayments',OLD.financial_snapshot->'periodicRepaymentAmount',OLD.financial_snapshot->'maximumInstallmentAmount',OLD.financial_snapshot->'interestAmount',OLD.financial_snapshot->'recordingSource',OLD.financial_snapshot->'tenureUnit',OLD.financial_snapshot->'tenureValue',OLD.financial_snapshot->'loanMode',OLD.financial_snapshot->'interestEntry',OLD.financial_snapshot->'interestBasis',OLD.financial_snapshot->'interestPercent')
  OR (NEW.first_repayment_date IS DISTINCT FROM OLD.first_repayment_date AND NOT (OLD.status='RECORDED' AND NEW.status='DISBURSED' AND OLD.first_repayment_date IS NULL AND NEW.first_repayment_date>NEW.disbursement_date))
  THEN RAISE EXCEPTION 'Recorded loan contract is immutable'; END IF;
  IF NEW.status IS DISTINCT FROM OLD.status AND NOT ((OLD.status='RECORDED' AND NEW.status='DISBURSED') OR (OLD.status IN ('DISBURSED','PAR','DEFAULTED','PAID') AND NEW.status IN ('DISBURSED','PAR','DEFAULTED','PAID')))
  THEN RAISE EXCEPTION 'Invalid recorded loan transition'; END IF;
 END IF; RETURN NEW;
END $$;

CREATE FUNCTION loan_period_number_protect() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF OLD.loan_accounting_period_id IS NOT NULL AND ROW(NEW.loan_id,NEW.loan_accounting_period_id) IS DISTINCT FROM ROW(OLD.loan_id,OLD.loan_accounting_period_id)
 THEN RAISE EXCEPTION 'Disbursement number is immutable'; END IF;
 IF NEW.loan_accounting_period_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM accounting_period p WHERE p.id=NEW.loan_accounting_period_id AND p.sacco_id=NEW.sacco_id AND NEW.disbursement_date BETWEEN p.starts_on AND p.ends_on)
 THEN RAISE EXCEPTION 'Loan numbering period must agree'; END IF; RETURN NEW;
END $$;
CREATE TRIGGER loan_period_number BEFORE UPDATE ON loan_applications FOR EACH ROW EXECUTE FUNCTION loan_period_number_protect();
