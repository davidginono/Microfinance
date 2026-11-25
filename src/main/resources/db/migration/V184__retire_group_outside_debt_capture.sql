-- Keep the previous validation for frozen applications. New assessments use linked ledger obligations.
DO $migration$
DECLARE
    definition text := pg_get_functiondef('staff_group_contract_validate(uuid,boolean)'::regprocedure);
    old_basis text := $$application_debt := coalesce(c.snapshot->>'debtBasis'='LOAN_APPLICATION',false);$$;
    old_gate text := $$IF application_debt AND ($$;
    roster_check text := $$-- Compare the roster only when the contract is first recorded.$$;
BEGIN
    IF strpos(definition,old_basis)=0 OR strpos(definition,old_gate)=0 OR strpos(definition,roster_check)=0 THEN
        RAISE EXCEPTION 'Group assessment validation is missing';
    END IF;
    definition := replace(definition, old_basis, $$application_debt := coalesce(c.snapshot->>'debtBasis' IN ('LOAN_APPLICATION','INTERNAL_LEDGER'),false);$$);
    definition := replace(definition, old_gate, $$IF c.snapshot->>'debtBasis'='LOAN_APPLICATION' AND ($$);
    definition := replace(definition, roster_check, $check$
    IF c.snapshot->>'debtBasis'='INTERNAL_LEDGER' AND (
        l.form_data::jsonb #> '{groupApplication,checked}' IS DISTINCT FROM 'true'::jsonb
        OR c.snapshot ? 'outsideDebts'
        OR l.form_data::jsonb #> '{groupApplication,debts}' IS NOT NULL
        OR NOT coalesce((c.snapshot->>'internalDebt')::numeric>=0,false)
        OR (l.form_data::jsonb->>'otherDebt')::numeric IS DISTINCT FROM (c.snapshot->>'internalDebt')::numeric
        OR EXISTS (SELECT 1 FROM staff_group_borrower b WHERE b.loan_id=l.id AND (
            b.signed_on IS DISTINCT FROM (l.form_data::jsonb #>> '{groupApplication,signedOn}')::date
            OR NOT EXISTS (SELECT 1 FROM jsonb_array_elements(c.snapshot->'assessments') AS assessments(person)
                WHERE person->>'clientId'=b.client_id::text AND person->>'profileRevision'=b.profile_revision::text
                    AND (person->>'internalDebt')::numeric=b.debt AND b.debt>=0)))
    ) THEN RAISE EXCEPTION 'simpleGroup.error.budget'; END IF;

    -- Compare the roster only when the contract is first recorded.
$check$);
    EXECUTE definition;
END
$migration$;
