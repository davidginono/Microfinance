-- The same client identity has several unique keys. During concurrent first applications,
-- selecting only the primary-key arbiter can still fail on another equivalent unique index.
-- Ignore any uniqueness conflict, then retain the guard's exact borrower/institution validation.
DO $$ DECLARE definition text; marker text='ON CONFLICT(id) DO NOTHING'; BEGIN
 SELECT pg_get_functiondef('loan_borrower_identity_guard()'::regprocedure) INTO definition;
 IF strpos(definition,marker)=0 THEN RAISE EXCEPTION 'Borrower identity insert marker unavailable'; END IF;
 EXECUTE replace(definition,marker,'ON CONFLICT DO NOTHING');
END $$;
