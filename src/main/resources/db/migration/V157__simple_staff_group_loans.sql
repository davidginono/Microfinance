-- New staff-recorded joint loans own one receivable and have no member share accounts.
ALTER TABLE lending_group ADD COLUMN leader_id uuid REFERENCES members(id);
ALTER TABLE lending_group ADD COLUMN payout_type text CHECK(payout_type IN('BANK_ACCOUNT','MOBILE_MONEY'));
ALTER TABLE lending_group ADD COLUMN payout_provider varchar(120);
ALTER TABLE lending_group ADD COLUMN payout_holder varchar(160);
ALTER TABLE lending_group ADD COLUMN payout_identifier varchar(160);
ALTER TABLE lending_group ADD COLUMN payout_evidence varchar(500);

-- Preserve immutable group identity while allowing the new versioned setup fields.
DO $$ DECLARE definition text; BEGIN
 SELECT pg_get_functiondef('lending_group_protect()'::regprocedure) INTO definition;
 IF strpos(definition,'ARRAY[''status'',''version'']')=0 THEN RAISE EXCEPTION 'Group identity guard missing';END IF;
 EXECUTE replace(definition,'ARRAY[''status'',''version'']','ARRAY[''status'',''version'',''leader_id'',''payout_type'',''payout_provider'',''payout_holder'',''payout_identifier'',''payout_evidence'']');
END $$;

CREATE TABLE staff_group_loan (
 loan_id uuid PRIMARY KEY REFERENCES loan_applications(id),group_id uuid NOT NULL,sacco_id varchar(255) NOT NULL,station_id varchar(255) NOT NULL,
 contract_version text NOT NULL DEFAULT 'STAFF_GROUP_V1' CHECK(contract_version='STAFF_GROUP_V1'),
 leader_id uuid NOT NULL REFERENCES members(id),group_version integer NOT NULL,snapshot jsonb NOT NULL,terms_hash varchar(32) NOT NULL,request_hash varchar(64) NOT NULL,
 reference varchar(500) NOT NULL CHECK(length(trim(reference))>0),language text NOT NULL CHECK(language IN('EN','SW')),
 filename varchar(200) NOT NULL,body bytea NOT NULL CHECK(octet_length(body) BETWEEN 5 AND 10485760),
 recorded_by uuid NOT NULL REFERENCES members(id),recorded_at timestamptz NOT NULL,
 FOREIGN KEY(group_id,sacco_id,station_id) REFERENCES lending_group(id,sacco_id,station_id),
 FOREIGN KEY(loan_id,sacco_id,station_id) REFERENCES loan_applications(id,sacco_id,station_id)
);
CREATE INDEX ix_staff_group_scope ON staff_group_loan(sacco_id,station_id,group_id,loan_id);
CREATE TABLE staff_group_borrower (
 loan_id uuid NOT NULL REFERENCES staff_group_loan(loan_id),client_id uuid NOT NULL REFERENCES members(id),
 client_number varchar(255) NOT NULL,client_name varchar(255) NOT NULL,signed_on date NOT NULL,profile_revision uuid NOT NULL REFERENCES client_onboarding_revision(id),
 income numeric(18,2) NOT NULL CHECK(income>0),expenses numeric(18,2) NOT NULL CHECK(expenses>=0),debt numeric(18,2) NOT NULL CHECK(debt>=0),
 income_source varchar(200) NOT NULL CHECK(length(trim(income_source))>0),evidence varchar(500) NOT NULL CHECK(length(trim(evidence))>0),
 PRIMARY KEY(loan_id,client_id),UNIQUE(loan_id,income_source)
);
CREATE INDEX ix_staff_group_client ON staff_group_borrower(client_id,loan_id);
CREATE UNIQUE INDEX uq_staff_group_income_source ON staff_group_borrower(loan_id,lower(btrim(income_source)));
CREATE TABLE staff_group_guarantor (
 loan_id uuid NOT NULL REFERENCES staff_group_loan(loan_id),client_id uuid NOT NULL REFERENCES members(id),
 amount_cap numeric(18,2) NOT NULL CHECK(amount_cap>0),budget numeric(18,2) NOT NULL CHECK(budget>=amount_cap),
 evidence varchar(500) NOT NULL CHECK(length(trim(evidence))>0),signed_on date NOT NULL,
 PRIMARY KEY(loan_id,client_id)
);
CREATE INDEX ix_staff_group_guarantor_client ON staff_group_guarantor(client_id,loan_id);
CREATE TABLE staff_group_funding (
 loan_id uuid PRIMARY KEY REFERENCES staff_group_loan(loan_id),posting_id uuid NOT NULL UNIQUE REFERENCES accountant_loan_post(id) DEFERRABLE INITIALLY DEFERRED,
 journal_id uuid NOT NULL REFERENCES gl_journal(id),actor_id uuid NOT NULL REFERENCES members(id),recorded_at timestamptz NOT NULL
);
CREATE TABLE staff_group_draft (
 id uuid PRIMARY KEY,sacco_id varchar(255) NOT NULL,station_id varchar(255) NOT NULL,actor_id uuid NOT NULL REFERENCES members(id),
 group_id uuid NOT NULL,version integer NOT NULL DEFAULT 0,form_data jsonb NOT NULL,uploads jsonb NOT NULL DEFAULT '[]'::jsonb,updated_at timestamptz NOT NULL,
 FOREIGN KEY(group_id,sacco_id,station_id) REFERENCES lending_group(id,sacco_id,station_id)
);
CREATE INDEX ix_staff_group_draft_scope ON staff_group_draft(sacco_id,station_id,updated_at,id);
CREATE TABLE staff_group_withdrawal (
 loan_id uuid PRIMARY KEY,request_key uuid NOT NULL,sacco_id varchar(255) NOT NULL,station_id varchar(255) NOT NULL,
 actor_id uuid NOT NULL REFERENCES members(id),previous_status varchar(64) NOT NULL,terms_hash varchar(32) NOT NULL,
 reason varchar(500) NOT NULL CHECK(length(trim(reason))>0),recorded_at timestamptz NOT NULL,
 UNIQUE(sacco_id,station_id,request_key),FOREIGN KEY(loan_id,sacco_id,station_id) REFERENCES loan_applications(id,sacco_id,station_id)
);

CREATE FUNCTION staff_group_setup_guard() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF NEW.leader_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM lending_group_member gm JOIN members m ON m.id=gm.client_id
   WHERE gm.group_id=NEW.id AND gm.client_id=NEW.leader_id AND gm.ends_on IS NULL AND gm.starts_on<=current_date
    AND m.sacco_id=NEW.sacco_id AND m.station_id=NEW.station_id AND m.is_member AND m.status='ACTIVE')
 THEN RAISE EXCEPTION 'simpleGroup.error.leader'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER staff_group_setup_scope BEFORE UPDATE OF leader_id,payout_type,payout_provider,payout_holder,payout_identifier,payout_evidence ON lending_group FOR EACH ROW EXECUTE FUNCTION staff_group_setup_guard();
CREATE FUNCTION staff_group_membership_revision() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF TG_OP='INSERT' AND (SELECT count(*) FROM lending_group_member WHERE group_id=NEW.group_id AND ends_on IS NULL)>50 THEN RAISE EXCEPTION 'simpleGroup.error.members';END IF;
 UPDATE lending_group SET version=version+1,leader_id=CASE WHEN TG_OP='UPDATE' AND NEW.ends_on IS NOT NULL AND leader_id=NEW.client_id THEN NULL ELSE leader_id END WHERE id=NEW.group_id;
 RETURN NEW;
END $$;
CREATE TRIGGER staff_group_member_revision AFTER INSERT OR UPDATE ON lending_group_member FOR EACH ROW EXECUTE FUNCTION staff_group_membership_revision();

CREATE FUNCTION staff_group_contract_validate(application uuid,initial boolean DEFAULT false) RETURNS void LANGUAGE plpgsql AS $$
DECLARE l loan_applications; c staff_group_loan; g lending_group; n integer;
BEGIN
 SELECT * INTO l FROM loan_applications WHERE id=application;
 SELECT * INTO c FROM staff_group_loan WHERE loan_id=application;
 IF c.loan_id IS NULL THEN RETURN; END IF;
 SELECT * INTO g FROM lending_group WHERE id=c.group_id FOR UPDATE;
 SELECT count(*) INTO n FROM staff_group_borrower WHERE loan_id=l.id;
 IF n NOT BETWEEN 2 AND 50 OR l.applicant_member_id IS NOT NULL OR l.top_up_source_loan_id IS NOT NULL
  OR l.first_repayment_date IS NULL OR l.first_repayment_date IS DISTINCT FROM (l.form_data::jsonb->>'firstPaymentDate')::date
  OR l.required_guarantors IS DISTINCT FROM (l.policy_snapshot::jsonb->>'guarantorsRequired')::integer
  OR c.terms_hash IS DISTINCT FROM loan_group_terms(l)
  OR c.request_hash IS DISTINCT FROM l.form_data::jsonb->>'groupRequestHash'
  OR encode(sha256(c.body),'hex') IS DISTINCT FROM l.form_data::jsonb->>'groupAgreementChecksum'
  OR coalesce((l.financial_snapshot::jsonb->>'recordedNetCashEstimate')::numeric,0)<=0
  OR l.payment_details_snapshot::jsonb IS DISTINCT FROM (c.snapshot->>'destination')::jsonb
  OR NOT EXISTS(SELECT 1 FROM loan_borrower_party WHERE id=l.borrower_party_id AND kind='JOINT_GROUP' AND group_id=c.group_id AND sacco_id=c.sacco_id AND group_station_id=c.station_id)
  OR NOT EXISTS(SELECT 1 FROM staff_group_borrower WHERE loan_id=l.id AND client_id=c.leader_id)
  OR NOT EXISTS(SELECT 1 FROM accountant_loan_record WHERE loan_id=l.id AND sacco_id=c.sacco_id AND station_id=c.station_id AND actor_id=c.recorded_by)
  OR EXISTS(SELECT 1 FROM staff_group_borrower b JOIN members m ON m.id=b.client_id WHERE b.loan_id=l.id
    AND (m.sacco_id<>c.sacco_id OR m.station_id<>c.station_id OR b.signed_on>(c.recorded_at AT TIME ZONE 'Africa/Dar_es_Salaam')::date
     OR b.signed_on<(SELECT application_date FROM accountant_loan_record WHERE loan_id=l.id)
     OR NOT EXISTS(SELECT 1 FROM client_onboarding_revision r WHERE r.id=b.profile_revision AND r.client_id=b.client_id AND r.sacco_id=c.sacco_id AND r.station_id=c.station_id AND r.mode<>'DRAFT'
       AND (r.profile->>'monthlyIncome')::numeric=b.income AND (r.profile->>'monthlyExpenses')::numeric=b.expenses AND (r.profile->>'monthlyDebtRepayments')::numeric=b.debt)))
  OR substring(c.body FROM 1 FOR 5)<>convert_to('%PDF-','UTF8')
 THEN RAISE EXCEPTION 'simpleGroup.error.signatures'; END IF;
 -- Membership is checked only when the contract is first recorded, never against a later roster.
 IF initial AND (g.status<>'ACTIVE' OR g.version<>c.group_version OR g.leader_id IS DISTINCT FROM c.leader_id
 OR n<>(SELECT count(*) FROM lending_group_member WHERE group_id=g.id AND ends_on IS NULL AND starts_on<=current_date)
 OR EXISTS(SELECT 1 FROM staff_group_borrower b WHERE b.loan_id=l.id AND NOT EXISTS(SELECT 1 FROM lending_group_member gm JOIN members m ON m.id=gm.client_id WHERE gm.group_id=g.id AND gm.client_id=b.client_id AND gm.ends_on IS NULL AND gm.starts_on<=current_date AND m.status='ACTIVE' AND m.is_member))) THEN RAISE EXCEPTION 'simpleGroup.error.changed';END IF;
END $$;
CREATE FUNCTION staff_group_contract_commit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 PERFORM staff_group_contract_validate(NEW.loan_id,true);RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER staff_group_contract_complete AFTER INSERT ON staff_group_loan DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION staff_group_contract_commit();
CREATE CONSTRAINT TRIGGER staff_group_borrowers_complete AFTER INSERT ON staff_group_borrower DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION staff_group_contract_commit();

-- Retain immutable evidence and the posting/servicing guards of funded historical contracts.
DO $$ DECLARE t text; BEGIN
 FOREACH t IN ARRAY ARRAY['staff_group_loan','staff_group_borrower','staff_group_guarantor','staff_group_funding','staff_group_withdrawal'] LOOP
  EXECUTE format('CREATE TRIGGER staff_group_history BEFORE UPDATE OR DELETE ON %I FOR EACH ROW EXECUTE FUNCTION gl_protect_evidence()',t);
  EXECUTE format('CREATE TRIGGER staff_group_no_truncate BEFORE TRUNCATE ON %I FOR EACH STATEMENT EXECUTE FUNCTION gl_protect_evidence()',t);
 END LOOP;
END $$;
CREATE FUNCTION staff_group_withdrawal_guard() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF NOT EXISTS(SELECT 1 FROM loan_applications l JOIN members m ON m.id=NEW.actor_id
   WHERE l.id=NEW.loan_id AND l.sacco_id=NEW.sacco_id AND l.station_id=NEW.station_id AND l.status=NEW.previous_status
    AND loan_group_terms(l)=NEW.terms_hash AND m.sacco_id=NEW.sacco_id AND m.station_id=NEW.station_id AND m.status='ACTIVE'
    AND (m.staff_access_status='ACTIVE' OR (m.staff_access_status='NONE' AND m.position<>'MEMBER'))
    AND l.status NOT IN('DISBURSED','PAR','DEFAULTED','PAID','WRITTEN_OFF','DISBURSEMENT_REVERSED','WITHDRAWN')
    AND NOT EXISTS(SELECT 1 FROM loan_ledgers WHERE loan_application_id=l.id)
    AND (l.applicant_member_id IS NULL OR EXISTS(SELECT 1 FROM lending_group_participant WHERE loan_id=l.id)))
 THEN RAISE EXCEPTION 'simpleGroup.error.withdrawal';END IF;RETURN NEW;
END $$;
CREATE TRIGGER staff_group_withdrawal_scope BEFORE INSERT ON staff_group_withdrawal FOR EACH ROW EXECUTE FUNCTION staff_group_withdrawal_guard();
DO $$ DECLARE d text; marker text='IF NEW.status IS DISTINCT FROM OLD.status AND NOT ('; name text; BEGIN
 SELECT pg_get_functiondef('accountant_loan_terms_protect()'::regprocedure) INTO d;
 IF strpos(d,marker)=0 THEN RAISE EXCEPTION 'Recorded transition guard marker missing';END IF;
 EXECUTE replace(d,marker,marker||' (NEW.status=''WITHDRAWN'' AND EXISTS(SELECT 1 FROM staff_group_withdrawal WHERE loan_id=NEW.id)) OR ');
 FOREACH name IN ARRAY ARRAY['lending_group_withdrawn_loan_guard','lending_group_shared_commit','lending_group_facility_commit'] LOOP
  SELECT pg_get_functiondef((name||'()')::regprocedure) INTO d;
  d=regexp_replace(d,'BEGIN','BEGIN IF TG_TABLE_NAME=''loan_applications'' THEN IF NEW.status=''WITHDRAWN'' AND EXISTS(SELECT 1 FROM staff_group_withdrawal WHERE loan_id=NEW.id) THEN RETURN NEW;END IF;END IF;', 'i');EXECUTE d;
 END LOOP;
END $$;
CREATE FUNCTION retired_group_application(application uuid) RETURNS boolean LANGUAGE sql STABLE AS $$
 SELECT EXISTS(SELECT 1 FROM loan_applications l WHERE l.id=application
  AND (l.applicant_member_id IS NULL OR EXISTS(SELECT 1 FROM lending_group_participant WHERE loan_id=l.id))
  AND NOT EXISTS(SELECT 1 FROM staff_group_loan WHERE loan_id=l.id)
  AND NOT EXISTS(SELECT 1 FROM loan_ledgers WHERE loan_application_id=l.id))
$$;
CREATE FUNCTION retired_group_review_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE application uuid;BEGIN
 IF TG_TABLE_NAME='loan_group_request' THEN application=NEW.loan_id;ELSE SELECT loan_id INTO application FROM loan_group_request WHERE id=NEW.request_id;END IF;
 IF retired_group_application(application) THEN RAISE EXCEPTION 'simpleGroup.error.retired';END IF;RETURN NEW;
END $$;
CREATE TRIGGER retired_group_request BEFORE INSERT ON loan_group_request FOR EACH ROW EXECUTE FUNCTION retired_group_review_guard();
CREATE TRIGGER retired_group_decision BEFORE INSERT ON loan_group_decision FOR EACH ROW EXECUTE FUNCTION retired_group_review_guard();
CREATE FUNCTION staff_group_retired() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'simpleGroup.error.retired';END $$;
DO $$ DECLARE t text; BEGIN
 FOREACH t IN ARRAY ARRAY['lending_group_policy','lending_group_cycle','lending_group_authority','lending_group_authority_signer',
 'lending_group_authority_revocation','lending_group_participant','lending_group_guarantee','lending_group_facility',
 'lending_group_consent','lending_group_consent_challenge','lending_group_consent_evidence','lending_group_termination','lending_group_signed_paper',
 'lending_group_org_agreement','lending_group_org_challenge','lending_group_org_document_access','lending_group_org_payout_challenge','lending_group_shared_opened','lending_group_shared_payout_challenge',
 'lending_group_personal_challenge','lending_group_personal_opened','lending_group_org_terms','lending_group_org_signature','lending_group_org_payout','lending_group_org_funding',
 'lending_group_shared_facility','lending_group_shared_allocation','lending_group_shared_document','lending_group_shared_payout','lending_group_shared_funding',
 'lending_group_personal_guarantee','lending_group_personal_decision','lending_group_coverage_policy','lending_group_coverage_case','lending_group_coverage_event'] LOOP
  EXECUTE format('CREATE TRIGGER retired_group_command BEFORE INSERT ON %I FOR EACH ROW EXECUTE FUNCTION staff_group_retired()',t);
 END LOOP;
END $$;

-- Admit the new source without fabricating an old policy/cycle/allocation or borrowing mandate.
DO $$ DECLARE d text; BEGIN
 SELECT pg_get_functiondef('lending_group_shared_commit()'::regprocedure) INTO d;
 d=replace(d,'IF cycle IS NULL AND EXISTS','IF cycle IS NULL AND NOT EXISTS(SELECT 1 FROM staff_group_loan WHERE loan_id=NEW.id) AND EXISTS'); EXECUTE d;
 SELECT pg_get_functiondef('lending_group_organization_funding_guard()'::regprocedure) INTO d;
 d=replace(d,'THEN RAISE EXCEPTION ''Typed group borrower','AND NOT EXISTS(SELECT 1 FROM staff_group_funding WHERE loan_id=NEW.id) THEN RAISE EXCEPTION ''Typed group borrower');EXECUTE d;
 SELECT pg_get_functiondef('loan_ledger_borrower_guard()'::regprocedure) INTO d;
 d=replace(d,'FROM (SELECT loan_id,sacco_id,station_id FROM lending_group_org_funding UNION ALL SELECT loan_id,sacco_id,station_id FROM lending_group_shared_funding) f',
 'FROM (SELECT loan_id,sacco_id,station_id FROM lending_group_org_funding UNION ALL SELECT loan_id,sacco_id,station_id FROM lending_group_shared_funding UNION ALL SELECT loan_id,sacco_id,station_id FROM staff_group_loan WHERE EXISTS(SELECT 1 FROM staff_group_funding WHERE loan_id=staff_group_loan.loan_id)) f');EXECUTE d;
END $$;
DO $$ DECLARE definition text; BEGIN
 SELECT pg_get_functiondef('loan_group_related(uuid,uuid)'::regprocedure) INTO definition;
 IF strpos(definition,'FUNCTION public.loan_group_related(')=0 THEN RAISE EXCEPTION 'Loan independence definition missing';END IF;
 EXECUTE replace(definition,'FUNCTION public.loan_group_related(','FUNCTION public.historical_loan_group_related(');
END $$;
CREATE OR REPLACE FUNCTION loan_group_related(application uuid,person uuid) RETURNS boolean LANGUAGE sql STABLE AS $$
 SELECT historical_loan_group_related(application,person) OR EXISTS(SELECT 1 FROM staff_group_borrower WHERE loan_id=application AND client_id=person) OR EXISTS(SELECT 1 FROM staff_group_guarantor WHERE loan_id=application AND client_id=person)
$$;

CREATE FUNCTION staff_group_application_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE failure text; b record;
BEGIN
 IF TG_OP='INSERT' THEN
  IF NEW.applicant_member_id IS NULL AND (NEW.form_data::jsonb->>'loanFor' IS DISTINCT FROM 'GROUP' OR NEW.status<>'RECORDED') THEN RAISE EXCEPTION 'simpleGroup.error.retired';END IF;
 ELSE
  IF EXISTS(SELECT 1 FROM staff_group_loan WHERE loan_id=NEW.id) AND (NEW.first_repayment_date IS DISTINCT FROM OLD.first_repayment_date OR NEW.required_guarantors IS DISTINCT FROM OLD.required_guarantors) THEN RAISE EXCEPTION 'simpleGroup.error.changed';END IF;
  IF NOT EXISTS(SELECT 1 FROM staff_group_loan WHERE loan_id=NEW.id) AND (
    EXISTS(SELECT 1 FROM lending_group_participant WHERE loan_id=NEW.id) OR EXISTS(SELECT 1 FROM lending_group_facility WHERE loan_id=NEW.id)
    OR EXISTS(SELECT 1 FROM lending_group_shared_facility WHERE loan_id=NEW.id))
    AND NEW.status IN('SUBMITTED','READY_FOR_DISBURSEMENT','DISBURSED') AND NEW.status IS DISTINCT FROM OLD.status
    AND NOT EXISTS(SELECT 1 FROM loan_ledgers WHERE loan_application_id=NEW.id)
   THEN RAISE EXCEPTION 'simpleGroup.error.retired';END IF;
  IF EXISTS(SELECT 1 FROM staff_group_loan WHERE loan_id=NEW.id) AND NOT EXISTS(SELECT 1 FROM loan_ledgers WHERE loan_application_id=NEW.id) AND NEW.status IN('SUBMITTED','READY_FOR_DISBURSEMENT','DISBURSED') AND OLD.status IS DISTINCT FROM NEW.status THEN
   PERFORM staff_group_contract_validate(NEW.id);
   PERFORM pg_advisory_xact_lock(hashtextextended('CREDIT_POLICY/'||NEW.sacco_id||'/'||NEW.station_id,0));
   FOR b IN SELECT client_id FROM staff_group_borrower WHERE loan_id=NEW.id ORDER BY client_id LOOP
    PERFORM pg_advisory_xact_lock(hashtextextended('CLIENT_KYC/'||b.client_id,0));
    failure=CASE WHEN coalesce((SELECT kyc_required FROM lending_credit_policy WHERE sacco_id=NEW.sacco_id AND station_id=NEW.station_id ORDER BY revision DESC LIMIT 1),false) THEN client_kyc_error(b.client_id,NEW.sacco_id,NEW.station_id,(current_timestamp AT TIME ZONE 'Africa/Dar_es_Salaam')::date) END;
    IF failure IS NOT NULL THEN RAISE EXCEPTION '%',failure;END IF;
   END LOOP;
  END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER simple_group_application BEFORE INSERT OR UPDATE ON loan_applications FOR EACH ROW EXECUTE FUNCTION staff_group_application_guard();
CREATE FUNCTION staff_group_funding_commit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF NOT EXISTS(SELECT 1 FROM staff_group_loan g JOIN accountant_loan_post p ON p.loan_id=g.loan_id
 JOIN gl_journal j ON j.id=p.journal_id JOIN loan_ledgers b ON b.loan_application_id=g.loan_id JOIN loan_applications l ON l.id=g.loan_id
 WHERE g.loan_id=NEW.loan_id AND p.id=NEW.posting_id AND p.journal_id=NEW.journal_id AND p.actor_id=NEW.actor_id
 AND p.kind='DISBURSEMENT' AND p.state='POSTED' AND j.state='POSTED' AND b.principal=l.amount AND b.applicant_member_id IS NULL
 AND NOT EXISTS(SELECT 1 FROM staff_group_borrower WHERE loan_id=g.loan_id AND client_id=NEW.actor_id)
 AND EXISTS(SELECT 1 FROM lending_group WHERE id=g.group_id AND status='ACTIVE')
 AND NOT EXISTS(SELECT 1 FROM staff_group_borrower person JOIN members m ON m.id=person.client_id WHERE person.loan_id=g.loan_id
   AND (person.signed_on>p.effective_date OR m.status<>'ACTIVE' OR m.sacco_id<>g.sacco_id OR m.station_id<>g.station_id))
 AND NOT EXISTS(SELECT 1 FROM staff_group_guarantor WHERE loan_id=g.loan_id AND signed_on>p.effective_date)
 AND p.sacco_id=g.sacco_id AND p.station_id=g.station_id
 AND (SELECT sum(credit-debit) FROM gl_journal_line WHERE journal_id=j.id AND account_id=p.money_account_id)=(l.financial_snapshot::jsonb->>'recordedNetCashEstimate')::numeric)
 THEN RAISE EXCEPTION 'simpleGroup.error.funding';END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER staff_group_funded AFTER INSERT ON staff_group_funding DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION staff_group_funding_commit();

CREATE VIEW historical_shared_credit_obligation AS SELECT * FROM lending_shared_credit_obligation;
-- Keep the original view OID so existing credit readers see the new obligations too.
DO $$ DECLARE definition text; BEGIN
 SELECT pg_get_viewdef('lending_shared_credit_obligation'::regclass,true) INTO definition;
 EXECUTE 'CREATE OR REPLACE VIEW historical_shared_credit_obligation AS '||definition;
END $$;
CREATE OR REPLACE VIEW lending_shared_credit_obligation AS SELECT * FROM historical_shared_credit_obligation UNION ALL
 SELECT g.loan_id,NULL::uuid,g.sacco_id,g.station_id,b.client_id,(CASE WHEN l.status='DISBURSED' AND coalesce(d.past_due,false) THEN 'PAR' ELSE l.status END)::varchar(255),'JOINT_AND_SEVERAL'::varchar(24),
 l.status IN('DISBURSED','PAR','DEFAULTED','WRITTEN_OFF'),l.status NOT IN('DISBURSED','PAR','DEFAULTED','WRITTEN_OFF'),
 0::numeric,
 CASE WHEN l.status IN('DISBURSED','PAR','DEFAULTED','WRITTEN_OFF') THEN greatest(0,ledger.principal-ledger.principal_paid)+coalesce(d.interest,0)+coalesce(c.outstanding,0)
 ELSE (l.financial_snapshot::jsonb->>'recordedTotalPayable')::numeric END,
 CASE WHEN l.repayment_frequency='WEEKLY' THEN ceil((l.financial_snapshot::jsonb->>'maximumInstallmentAmount')::numeric*5200/12)/100
 ELSE (l.financial_snapshot::jsonb->>'maximumInstallmentAmount')::numeric END,
 l.status IN('DISBURSED','PAR','DEFAULTED','WRITTEN_OFF') AND (ledger.loan_application_id IS NULL OR f.loan_id IS NULL OR coalesce(d.n,0)=0)
 FROM staff_group_loan g JOIN staff_group_borrower b ON b.loan_id=g.loan_id JOIN loan_applications l ON l.id=g.loan_id
 LEFT JOIN loan_ledgers ledger ON ledger.loan_application_id=g.loan_id LEFT JOIN staff_group_funding f ON f.loan_id=g.loan_id
 LEFT JOIN loan_charge_balance c ON c.loan_id=g.loan_id
 LEFT JOIN LATERAL(SELECT count(*) n,sum(interest-interest_paid-interest_excluded-interest_waived) interest,
 bool_or(due_date<current_date AND (principal>principal_paid OR loan_interest_due(id,current_date)>0)) past_due FROM loan_ledger_installments WHERE loan_application_id=g.loan_id)d ON true
 WHERE l.status NOT IN('DRAFT','WITHDRAWN','PAID','DISBURSEMENT_REVERSED','MANAGER_REJECTED','LOAN_OFFICER_REJECTED','CHAIRPERSON_REJECTED','BOARD_REJECTED','CREDIT_COMMITTEE_REJECTED','ACCOUNTANT_REJECTED','REJECTED');

CREATE VIEW historical_guarantor_commitment AS SELECT * FROM lending_guarantor_commitment;
DO $$ DECLARE definition text; BEGIN
 SELECT pg_get_viewdef('lending_guarantor_commitment'::regclass,true) INTO definition;
 EXECUTE 'CREATE OR REPLACE VIEW historical_guarantor_commitment AS '||definition;
END $$;
CREATE OR REPLACE VIEW lending_guarantor_commitment AS SELECT * FROM historical_guarantor_commitment UNION ALL
 SELECT g.client_id,g.loan_id,c.sacco_id,c.station_id,l.amount,g.amount_cap
 FROM staff_group_guarantor g JOIN staff_group_loan c ON c.loan_id=g.loan_id JOIN loan_applications l ON l.id=g.loan_id
 WHERE l.status NOT IN('WITHDRAWN','PAID','DISBURSEMENT_REVERSED','MANAGER_REJECTED','LOAN_OFFICER_REJECTED','CHAIRPERSON_REJECTED','BOARD_REJECTED','CREDIT_COMMITTEE_REJECTED','ACCOUNTANT_REJECTED','REJECTED');
CREATE FUNCTION staff_group_guarantees_ready(application uuid) RETURNS boolean LANGUAGE sql STABLE AS $$
 SELECT count(g.client_id)>=coalesce(l.required_guarantors,0) FROM loan_applications l LEFT JOIN staff_group_guarantor g ON g.loan_id=l.id
 WHERE l.id=application AND EXISTS(SELECT 1 FROM staff_group_loan WHERE loan_id=application) GROUP BY l.required_guarantors
$$;
DO $$ DECLARE d text; BEGIN
 SELECT pg_get_functiondef('lending_personal_review_guard()'::regprocedure) INTO d;
 d=replace(d,'AND NOT coalesce(lending_personal_guarantees_ready(NEW.id),false)','AND NOT coalesce(lending_personal_guarantees_ready(NEW.id),false) AND NOT coalesce(staff_group_guarantees_ready(NEW.id),false)');EXECUTE d;
END $$;
CREATE FUNCTION staff_group_guarantee_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE c staff_group_loan; l loan_applications; committed numeric;
BEGIN
 SELECT * INTO c FROM staff_group_loan WHERE loan_id=NEW.loan_id;
 SELECT * INTO l FROM loan_applications WHERE id=NEW.loan_id;
 PERFORM lending_guarantor_capacity_lock(NEW.client_id,c.sacco_id,c.station_id);
 PERFORM lending_personal_capacity(NEW.client_id,c.sacco_id,c.station_id,NEW.loan_id,NEW.amount_cap);
 SELECT sum(amount_cap) INTO committed FROM lending_guarantor_commitment WHERE guarantor_id=NEW.client_id AND sacco_id=c.sacco_id AND loan_id<>NEW.loan_id;
 IF EXISTS(SELECT 1 FROM staff_group_borrower WHERE loan_id=NEW.loan_id AND client_id=NEW.client_id)
 OR NOT EXISTS(SELECT 1 FROM members WHERE id=NEW.client_id AND sacco_id=c.sacco_id AND station_id=c.station_id AND status='ACTIVE' AND is_member)
 OR NEW.amount_cap IS DISTINCT FROM (l.financial_snapshot::jsonb->>'recordedTotalPayable')::numeric
 OR NEW.budget<NEW.amount_cap+coalesce(committed,0)
 OR EXISTS(SELECT 1 FROM staff_group_guarantor previous JOIN loan_applications a ON a.id=previous.loan_id WHERE previous.client_id=NEW.client_id AND a.sacco_id=c.sacco_id
   AND a.status NOT IN('PAID','WITHDRAWN','DISBURSEMENT_REVERSED','REJECTED') AND previous.budget<NEW.amount_cap+coalesce(committed,0))
 OR NEW.signed_on>(c.recorded_at AT TIME ZONE 'Africa/Dar_es_Salaam')::date
 OR NOT EXISTS(SELECT 1 FROM jsonb_array_elements(l.attachments_json::jsonb) d WHERE d->>'documentPurpose'='GUARANTOR_CONSENT')
 THEN RAISE EXCEPTION 'simpleGroup.error.guarantee';END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER staff_group_guarantee_scope BEFORE INSERT ON staff_group_guarantor FOR EACH ROW EXECUTE FUNCTION staff_group_guarantee_guard();

-- A single row per loan supports personal and joint borrowers without multiplying money.
CREATE VIEW loan_borrower_contact AS
 SELECT l.id loan_id,l.sacco_id,l.station_id,b.party_id id,b.client_id,b.member_no,
  coalesce(c.snapshot->'setup'->>'name',b.full_name) full_name,m.phone
 FROM loan_applications l JOIN loan_borrower_directory b ON b.party_id=l.borrower_party_id AND b.sacco_id=l.sacco_id
 LEFT JOIN staff_group_loan c ON c.loan_id=l.id AND c.sacco_id=l.sacco_id AND c.station_id=l.station_id
 LEFT JOIN members m ON m.id=coalesce(b.client_id,c.leader_id) AND m.sacco_id=l.sacco_id AND m.station_id=l.station_id
 WHERE b.kind='CLIENT' OR b.group_station_id=l.station_id;

-- New typed applications cannot commit an unfrozen or unsupported group contract.
CREATE FUNCTION staff_group_origination_commit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF NEW.applicant_member_id IS NULL AND NOT EXISTS(SELECT 1 FROM staff_group_loan WHERE loan_id=NEW.id) THEN RAISE EXCEPTION 'simpleGroup.error.retired';END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER staff_group_origination_complete AFTER INSERT ON loan_applications DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION staff_group_origination_commit();
