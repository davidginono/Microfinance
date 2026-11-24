ALTER TABLE staff_group_loan
    ALTER COLUMN filename DROP NOT NULL,
    ALTER COLUMN body DROP NOT NULL,
    ADD CONSTRAINT staff_group_loan_pdf_pair CHECK (
        (filename IS NULL AND body IS NULL)
        OR (filename IS NOT NULL AND length(btrim(filename)) > 0 AND body IS NOT NULL)
    );

CREATE OR REPLACE FUNCTION staff_group_contract_validate(application uuid, initial boolean DEFAULT false)
RETURNS void LANGUAGE plpgsql AS $$
DECLARE
    l loan_applications;
    c staff_group_loan;
    g lending_group;
    n integer;
    application_debt boolean;
BEGIN
    SELECT * INTO l FROM loan_applications WHERE id=application;
    SELECT * INTO c FROM staff_group_loan WHERE loan_id=application;
    IF c.loan_id IS NULL THEN RETURN; END IF;
    SELECT * INTO g FROM lending_group WHERE id=c.group_id FOR UPDATE;
    SELECT count(*) INTO n FROM staff_group_borrower WHERE loan_id=l.id;
    application_debt := coalesce(c.snapshot->>'debtBasis'='LOAN_APPLICATION',false);

    IF n NOT BETWEEN 2 AND 50 OR l.applicant_member_id IS NOT NULL OR l.top_up_source_loan_id IS NOT NULL
        OR ((l.form_data::jsonb->>'firstPaymentDate') IS NOT NULL
            AND l.first_repayment_date IS DISTINCT FROM (l.form_data::jsonb->>'firstPaymentDate')::date)
        OR ((l.form_data::jsonb->>'firstPaymentDate') IS NULL AND l.first_repayment_date IS NOT NULL
            AND NOT (l.status IN ('DISBURSED','PAR','DEFAULTED','PAID','WRITTEN_OFF')
                AND l.first_repayment_date>l.disbursement_date
                AND EXISTS (SELECT 1 FROM staff_group_funding WHERE loan_id=l.id)))
        OR l.required_guarantors IS DISTINCT FROM (l.policy_snapshot::jsonb->>'guarantorsRequired')::integer
        OR c.terms_hash IS DISTINCT FROM loan_group_terms(l)
        OR c.request_hash IS DISTINCT FROM l.form_data::jsonb->>'groupRequestHash'
        OR encode(sha256(c.body),'hex') IS DISTINCT FROM l.form_data::jsonb->>'groupAgreementChecksum'
        OR (c.body IS NOT NULL AND substring(c.body FROM 1 FOR 5)<>convert_to('%PDF-','UTF8'))
        OR (NOT application_debt AND (initial OR c.body IS NULL))
        OR coalesce((l.financial_snapshot::jsonb->>'recordedNetCashEstimate')::numeric,0)<=0
        OR l.payment_details_snapshot::jsonb IS DISTINCT FROM (c.snapshot->>'destination')::jsonb
        OR NOT EXISTS (SELECT 1 FROM loan_borrower_party WHERE id=l.borrower_party_id AND kind='JOINT_GROUP'
            AND group_id=c.group_id AND sacco_id=c.sacco_id AND group_station_id=c.station_id)
        OR NOT EXISTS (SELECT 1 FROM staff_group_borrower WHERE loan_id=l.id AND client_id=c.leader_id)
        OR NOT EXISTS (SELECT 1 FROM accountant_loan_record WHERE loan_id=l.id AND sacco_id=c.sacco_id
            AND station_id=c.station_id AND actor_id=c.recorded_by)
        OR EXISTS (SELECT 1 FROM staff_group_borrower b JOIN members m ON m.id=b.client_id WHERE b.loan_id=l.id
            AND (m.sacco_id<>c.sacco_id OR m.station_id<>c.station_id
                OR b.signed_on>(c.recorded_at AT TIME ZONE 'Africa/Dar_es_Salaam')::date
                OR b.signed_on<(SELECT application_date FROM accountant_loan_record WHERE loan_id=l.id)
                OR NOT EXISTS (SELECT 1 FROM client_onboarding_revision r WHERE r.id=b.profile_revision
                    AND r.client_id=b.client_id AND r.sacco_id=c.sacco_id AND r.station_id=c.station_id AND r.mode<>'DRAFT'
                    AND (r.profile->>'monthlyIncome')::numeric=b.income AND (r.profile->>'monthlyExpenses')::numeric=b.expenses
                    AND (application_debt OR (r.profile->>'monthlyDebtRepayments')::numeric=b.debt))))
    THEN RAISE EXCEPTION 'simpleGroup.error.signatures'; END IF;

    IF application_debt AND (
        l.form_data::jsonb #> '{groupApplication,checked}' IS DISTINCT FROM 'true'::jsonb
        OR jsonb_typeof(c.snapshot->'outsideDebts') IS DISTINCT FROM 'array'
        OR c.snapshot->'outsideDebts' IS DISTINCT FROM l.form_data::jsonb #> '{groupApplication,debts}'
        OR NOT coalesce((c.snapshot->>'internalDebt')::numeric>=0,false)
        OR (l.form_data::jsonb->>'otherDebt')::numeric IS DISTINCT FROM (c.snapshot->>'internalDebt')::numeric + (
            SELECT coalesce(sum((debt->>'monthlyPayment')::numeric),0)
            FROM jsonb_array_elements(c.snapshot->'outsideDebts') AS debts(debt))
        OR EXISTS (SELECT 1 FROM staff_group_borrower b WHERE b.loan_id=l.id AND (
            b.signed_on IS DISTINCT FROM (l.form_data::jsonb #>> '{groupApplication,signedOn}')::date
            OR NOT EXISTS (SELECT 1 FROM jsonb_array_elements(c.snapshot->'assessments') AS assessments(person)
                WHERE person->>'clientId'=b.client_id::text AND person->>'profileRevision'=b.profile_revision::text
                    AND (person->>'internalDebt')::numeric>=0 AND (person->>'debt')::numeric=b.debt
                    AND b.debt=(person->>'internalDebt')::numeric + (
                        SELECT coalesce(sum((debt->>'monthlyPayment')::numeric),0)
                        FROM jsonb_array_elements(c.snapshot->'outsideDebts') AS debts(debt)
                        WHERE debt->'clientIds' ? b.client_id::text))))
    ) THEN RAISE EXCEPTION 'simpleGroup.error.outsideDebts'; END IF;

    -- Compare the roster only when the contract is first recorded.
    IF initial AND (g.status<>'ACTIVE' OR g.version<>c.group_version OR g.leader_id IS DISTINCT FROM c.leader_id
        OR n<>(SELECT count(*) FROM lending_group_member WHERE group_id=g.id AND ends_on IS NULL AND starts_on<=current_date)
        OR EXISTS (SELECT 1 FROM staff_group_borrower b WHERE b.loan_id=l.id AND NOT EXISTS (
            SELECT 1 FROM lending_group_member gm JOIN members m ON m.id=gm.client_id
            WHERE gm.group_id=g.id AND gm.client_id=b.client_id AND gm.ends_on IS NULL AND gm.starts_on<=current_date
                AND m.status='ACTIVE' AND m.is_member)))
    THEN RAISE EXCEPTION 'simpleGroup.error.changed'; END IF;
END $$;
