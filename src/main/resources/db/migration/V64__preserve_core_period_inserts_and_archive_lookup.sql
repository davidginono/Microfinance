-- Retain the existing period insert validation used by core posting and historical imports.
-- Closing/reopening actions remain frozen; the retired close-review workflow is not reinstated.
CREATE OR REPLACE FUNCTION gl_validate_period() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Periods cannot be deleted'; END IF;
 IF TG_OP='UPDATE' THEN
  IF NEW IS DISTINCT FROM OLD THEN RAISE EXCEPTION 'Period management is retired; existing dates and closed states are protected'; END IF;
 ELSE
  PERFORM pg_advisory_xact_lock(hashtextextended('GL_SETUP/' || NEW.sacco_id,0));
  IF EXISTS(SELECT 1 FROM accounting_period WHERE sacco_id=NEW.sacco_id AND starts_on<=NEW.ends_on AND ends_on>=NEW.starts_on)
   THEN RAISE EXCEPTION 'Accounting periods cannot overlap'; END IF;
 END IF;
 RETURN NEW;
END $$;

-- Exact loan-ownership checks must not scan the frozen business register.
CREATE INDEX ix_archived_business_loan_owner ON accounting_archive.accounting_business_document(loan_id)
 WHERE state<>'REJECTED';
