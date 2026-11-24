-- Preserve amounts recorded before this migration. New lending assessments leave these columns unset.
ALTER TABLE staff_group_borrower ALTER COLUMN expenses DROP NOT NULL;
ALTER TABLE lending_guarantor_assessment ALTER COLUMN expenses DROP NOT NULL;

DO $$
DECLARE
    definition text;
    original text;
    legacy_check name;
BEGIN
    definition := pg_get_functiondef('staff_group_contract_validate(uuid,boolean)'::regprocedure);
    original := ' AND (r.profile->>''monthlyExpenses'')::numeric=b.expenses';
    IF strpos(definition, original) = 0 THEN RAISE EXCEPTION 'Group profile expense check is missing'; END IF;
    EXECUTE replace(definition, original, '');

    definition := pg_get_functiondef('lending_capacity_error(uuid,text,text,date)'::regprocedure);
    original := 'a.income-a.expenses-greatest(a.other_debt,own_debt)';
    IF strpos(definition, original) = 0 THEN RAISE EXCEPTION 'Guarantor expense check is missing'; END IF;
    EXECUTE replace(definition, original, 'a.income-greatest(a.other_debt,own_debt)');

    SELECT conname INTO STRICT legacy_check
    FROM pg_constraint
    WHERE conrelid = 'lending_guarantor_assessment'::regclass AND contype = 'c'
        AND pg_get_constraintdef(oid) LIKE '%income > (expenses + other_debt)%';
    EXECUTE format('ALTER TABLE lending_guarantor_assessment DROP CONSTRAINT %I', legacy_check);
END $$;

ALTER TABLE lending_guarantor_assessment
    ADD CONSTRAINT lending_guarantor_assessment_income_debt_check CHECK (income > other_debt);
