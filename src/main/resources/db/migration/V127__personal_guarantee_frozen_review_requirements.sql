-- Positive guarantor counts are contract requirements, not mutable workflow shortcuts.
CREATE FUNCTION lending_personal_requirements_immutable() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF OLD.applicant_member_id IS NULL AND NEW.required_guarantors IS DISTINCT FROM OLD.required_guarantors
 THEN RAISE EXCEPTION 'Typed loan guarantor requirements are frozen'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER personal_requirements_immutable BEFORE UPDATE ON loan_applications FOR EACH ROW EXECUTE FUNCTION lending_personal_requirements_immutable();
DO $$ DECLARE definition text;marker text;BEGIN
 SELECT pg_get_functiondef('accountant_loan_terms_protect()'::regprocedure) INTO definition;
 marker=$old$OLD.status='AWAITING_GUARANTORS' AND NEW.status IN ('READY_FOR_MANAGER','AWAITING_LOAN_OFFICER','AWAITING_CHAIRPERSON','AWAITING_BOARD','AWAITING_CREDIT_COMMITTEE','AWAITING_ACCOUNTANT','READY_FOR_DISBURSEMENT') AND lending_personal_guarantees_ready(OLD.id)$old$;
 IF strpos(definition,marker)=0 THEN RAISE EXCEPTION 'Personal review transition marker missing'; END IF;
 definition=replace(definition,marker,$new$OLD.status='AWAITING_GUARANTORS' AND recorded_review_transition('RECORDED',NEW.status,NEW.policy_snapshot::jsonb) AND lending_personal_guarantees_ready(OLD.id)$new$);
 EXECUTE definition;
END $$;
