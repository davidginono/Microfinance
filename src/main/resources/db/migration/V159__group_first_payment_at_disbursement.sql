-- New group contracts leave the first payment date to the authorized funding action.
-- Existing contracts with a recorded date keep that immutable date.
DO $$
DECLARE definition text; previous text; replacement text;
BEGIN
 SELECT pg_get_functiondef('staff_group_contract_validate(uuid,boolean)'::regprocedure) INTO definition;
 previous := 'OR l.first_repayment_date IS NULL OR l.first_repayment_date IS DISTINCT FROM (l.form_data::jsonb->>''firstPaymentDate'')::date';
 replacement := 'OR ((l.form_data::jsonb->>''firstPaymentDate'') IS NOT NULL
    AND l.first_repayment_date IS DISTINCT FROM (l.form_data::jsonb->>''firstPaymentDate'')::date)
  OR ((l.form_data::jsonb->>''firstPaymentDate'') IS NULL AND l.first_repayment_date IS NOT NULL
    AND NOT (l.status IN(''DISBURSED'',''PAR'',''DEFAULTED'',''PAID'',''WRITTEN_OFF'')
      AND l.first_repayment_date>l.disbursement_date
      AND EXISTS(SELECT 1 FROM staff_group_funding WHERE loan_id=l.id)))';
 IF strpos(definition,previous)=0 THEN RAISE EXCEPTION 'Group contract date guard missing'; END IF;
 EXECUTE replace(definition,previous,replacement);

 SELECT pg_get_functiondef('staff_group_application_guard()'::regprocedure) INTO definition;
 previous := 'NEW.first_repayment_date IS DISTINCT FROM OLD.first_repayment_date OR NEW.required_guarantors IS DISTINCT FROM OLD.required_guarantors';
 replacement := '(NEW.first_repayment_date IS DISTINCT FROM OLD.first_repayment_date
    AND NOT (OLD.first_repayment_date IS NULL AND NEW.first_repayment_date IS NOT NULL
      AND OLD.status=''READY_FOR_DISBURSEMENT'' AND NEW.status=''DISBURSED''
      AND (OLD.form_data::jsonb->>''firstPaymentDate'') IS NULL
      AND NEW.first_repayment_date>NEW.disbursement_date
      AND EXISTS(SELECT 1 FROM staff_group_funding WHERE loan_id=NEW.id)))
    OR NEW.required_guarantors IS DISTINCT FROM OLD.required_guarantors';
 IF strpos(definition,previous)=0 THEN RAISE EXCEPTION 'Group application date guard missing'; END IF;
 EXECUTE replace(definition,previous,replacement);
END $$;
