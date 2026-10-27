-- Scoped credit evidence and servicing casework. No balances, fees or permissions are seeded.
CREATE TABLE lending_credit_policy (
 id uuid PRIMARY KEY,sacco_id varchar(255) NOT NULL,station_id varchar(255) NOT NULL,
 revision integer NOT NULL CHECK(revision>0),capacity_required boolean NOT NULL,collateral_required boolean NOT NULL,
 reference varchar(500) NOT NULL CHECK(length(trim(reference))>0),
 request_key uuid NOT NULL,payload_hash varchar(64) NOT NULL,actor_id uuid NOT NULL REFERENCES members(id),recorded_at timestamptz NOT NULL,
 reminders_enabled boolean NOT NULL,reminder_days_before integer,reminder_every_days integer,
 CHECK((reminders_enabled AND reminder_days_before IS NOT NULL AND reminder_every_days IS NOT NULL AND reminder_days_before BETWEEN 0 AND 30 AND reminder_every_days BETWEEN 1 AND 30)
   OR (NOT reminders_enabled AND reminder_days_before IS NULL AND reminder_every_days IS NULL)),
 UNIQUE(sacco_id,station_id,revision),UNIQUE(sacco_id,station_id,request_key)
);
CREATE TABLE lending_guarantor_assessment (
 id uuid PRIMARY KEY,request_id uuid NOT NULL REFERENCES guarantor_requests(id),sacco_id varchar(255) NOT NULL,station_id varchar(255) NOT NULL,
 subject_key text NOT NULL,income numeric(18,2) NOT NULL CHECK(income>0),expenses numeric(18,2) NOT NULL CHECK(expenses>=0),
 other_debt numeric(18,2) NOT NULL CHECK(other_debt>=0),capacity numeric(18,2) NOT NULL CHECK(capacity>=0),
 assessed_on date NOT NULL,valid_through date NOT NULL CHECK(valid_through>=assessed_on),
 evidence varchar(1000) NOT NULL CHECK(length(trim(evidence))>0),policy_reference varchar(500) NOT NULL CHECK(length(trim(policy_reference))>0),
 request_key uuid NOT NULL,payload_hash varchar(64) NOT NULL,actor_id uuid NOT NULL REFERENCES members(id),recorded_at timestamptz NOT NULL,
 revision integer NOT NULL CHECK(revision>0),
 CHECK(income>expenses+other_debt),UNIQUE(sacco_id,station_id,request_key),UNIQUE(sacco_id,station_id,subject_key,revision)
);
CREATE INDEX ix_credit_assessment_subject ON lending_guarantor_assessment(sacco_id,station_id,subject_key,revision DESC);
CREATE INDEX ix_credit_assessment_request ON lending_guarantor_assessment(request_id,recorded_at DESC,id DESC);
CREATE TABLE lending_guarantor_capacity_decision (
 id uuid PRIMARY KEY,request_id uuid NOT NULL REFERENCES guarantor_requests(id),assessment_id uuid NOT NULL REFERENCES lending_guarantor_assessment(id),
 liability numeric(18,2) NOT NULL CHECK(liability>0),recorded_at timestamptz NOT NULL
);
CREATE TRIGGER capacity_decision_immutable BEFORE UPDATE OR DELETE ON lending_guarantor_capacity_decision FOR EACH ROW EXECUTE FUNCTION gl_protect_evidence();
CREATE TRIGGER capacity_decision_no_truncate BEFORE TRUNCATE ON lending_guarantor_capacity_decision FOR EACH STATEMENT EXECUTE FUNCTION gl_protect_evidence();
CREATE TABLE lending_collateral_asset (
 id uuid PRIMARY KEY,sacco_id varchar(255) NOT NULL,station_id varchar(255) NOT NULL,
 code varchar(100) NOT NULL,description varchar(1000) NOT NULL,owner_name varchar(255) NOT NULL,
 owner_evidence varchar(1000) NOT NULL CHECK(length(trim(owner_evidence))>0),
 request_key uuid NOT NULL,payload_hash varchar(64) NOT NULL,actor_id uuid NOT NULL REFERENCES members(id),recorded_at timestamptz NOT NULL,
 UNIQUE(id,sacco_id,station_id),UNIQUE(sacco_id,code),UNIQUE(sacco_id,station_id,request_key)
);
CREATE TABLE lending_collateral_event (
 id uuid PRIMARY KEY,asset_id uuid NOT NULL,sacco_id varchar(255) NOT NULL,station_id varchar(255) NOT NULL,
 sequence integer NOT NULL CHECK(sequence>0),kind varchar(20) NOT NULL CHECK(kind IN ('VALUATION','PLEDGE','RELEASE','CUSTODY','RECOVERY')),
 loan_id uuid,amount numeric(18,2),encumbrance numeric(18,2),valid_through date,effective_date date NOT NULL,
 evidence varchar(1000) NOT NULL CHECK(length(trim(evidence))>0),reason varchar(1000) NOT NULL CHECK(length(trim(reason))>0),
 request_key uuid NOT NULL,payload_hash varchar(64) NOT NULL,actor_id uuid NOT NULL REFERENCES members(id),recorded_at timestamptz NOT NULL,
 FOREIGN KEY(asset_id,sacco_id,station_id) REFERENCES lending_collateral_asset(id,sacco_id,station_id),
 FOREIGN KEY(loan_id,sacco_id,station_id) REFERENCES loan_applications(id,sacco_id,station_id),
 CHECK((kind='VALUATION' AND amount IS NOT NULL AND amount>0 AND encumbrance IS NOT NULL AND encumbrance>=0 AND encumbrance<=amount AND valid_through IS NOT NULL AND valid_through>=effective_date AND loan_id IS NULL)
 OR (kind='PLEDGE' AND loan_id IS NOT NULL AND amount IS NOT NULL AND amount>0 AND encumbrance IS NULL AND valid_through IS NULL)
 OR (kind='RELEASE' AND loan_id IS NOT NULL AND amount IS NULL AND encumbrance IS NULL AND valid_through IS NULL)
 OR (kind IN ('CUSTODY','RECOVERY') AND amount IS NULL AND encumbrance IS NULL AND valid_through IS NULL)),
 UNIQUE(asset_id,sequence),UNIQUE(sacco_id,station_id,request_key)
);
CREATE INDEX ix_collateral_loan ON lending_collateral_event(loan_id,asset_id,sequence DESC) WHERE loan_id IS NOT NULL;
CREATE VIEW lending_collateral_pledge AS
 SELECT DISTINCT ON(asset_id,loan_id) asset_id,loan_id,sacco_id,station_id,kind,amount,sequence
 FROM lending_collateral_event WHERE kind IN ('PLEDGE','RELEASE') ORDER BY asset_id,loan_id,sequence DESC;
CREATE VIEW lending_collateral_valuation AS
 SELECT DISTINCT ON(asset_id) asset_id,sacco_id,station_id,amount,encumbrance,valid_through,effective_date
 FROM lending_collateral_event WHERE kind='VALUATION' ORDER BY asset_id,sequence DESC;
CREATE TABLE lending_collection_case (
 id uuid PRIMARY KEY,loan_id uuid NOT NULL,sacco_id varchar(255) NOT NULL,station_id varchar(255) NOT NULL,
 assigned_to uuid NOT NULL REFERENCES members(id),next_action date NOT NULL,reason varchar(1000) NOT NULL CHECK(length(trim(reason))>0),
 request_key uuid NOT NULL,payload_hash varchar(64) NOT NULL,actor_id uuid NOT NULL REFERENCES members(id),recorded_at timestamptz NOT NULL,
 UNIQUE(id,sacco_id,station_id),UNIQUE(sacco_id,station_id,request_key),
 FOREIGN KEY(loan_id,sacco_id,station_id) REFERENCES loan_applications(id,sacco_id,station_id)
);
CREATE INDEX ix_collection_case_scope ON lending_collection_case(sacco_id,station_id,recorded_at DESC,id);
CREATE INDEX ix_collection_case_loan ON lending_collection_case(loan_id);
CREATE TABLE lending_collection_event (
 id uuid PRIMARY KEY,case_id uuid NOT NULL,sacco_id varchar(255) NOT NULL,station_id varchar(255) NOT NULL,
 sequence integer NOT NULL CHECK(sequence>0),kind varchar(20) NOT NULL CHECK(kind IN ('CONTACT','PROMISE','DISPUTE','RESOLVE','NOTICE','ASSIGN','CLOSE','REOPEN')),
 assigned_to uuid REFERENCES members(id),next_action date,amount numeric(18,2),promise_on date,
 evidence varchar(1000) NOT NULL CHECK(length(trim(evidence))>0),effective_date date NOT NULL,
 request_key uuid NOT NULL,payload_hash varchar(64) NOT NULL,actor_id uuid NOT NULL REFERENCES members(id),recorded_at timestamptz NOT NULL,
 FOREIGN KEY(case_id,sacco_id,station_id) REFERENCES lending_collection_case(id,sacco_id,station_id),
 CHECK((kind='PROMISE' AND amount IS NOT NULL AND amount>0 AND promise_on IS NOT NULL) OR (kind<>'PROMISE' AND amount IS NULL AND promise_on IS NULL)),
 CHECK((kind='ASSIGN')=(assigned_to IS NOT NULL)),CHECK((kind='CLOSE' AND next_action IS NULL) OR (kind<>'CLOSE' AND next_action IS NOT NULL)),
 UNIQUE(case_id,sequence),UNIQUE(sacco_id,station_id,request_key)
);
CREATE TABLE lending_instalment_reminder (
 id uuid PRIMARY KEY,installment_id uuid NOT NULL REFERENCES loan_ledger_installments(id),reminder_on date NOT NULL,
 outbox_id uuid NOT NULL UNIQUE,UNIQUE(installment_id,reminder_on)
);
CREATE INDEX ix_credit_unpaid_due ON loan_ledger_installments(due_date,loan_application_id,id)
 WHERE principal>principal_paid OR interest>interest_paid+interest_excluded+interest_waived;

DO $$ DECLARE t text;BEGIN
 FOREACH t IN ARRAY ARRAY['lending_credit_policy','lending_guarantor_assessment','lending_collateral_asset','lending_collateral_event','lending_collection_case','lending_collection_event','lending_instalment_reminder'] LOOP
 EXECUTE format('CREATE TRIGGER %I BEFORE UPDATE OR DELETE ON %I FOR EACH ROW EXECUTE FUNCTION gl_protect_evidence()',t||'_immutable',t);
 EXECUTE format('CREATE TRIGGER %I BEFORE TRUNCATE ON %I FOR EACH STATEMENT EXECUTE FUNCTION gl_protect_evidence()',t||'_no_truncate',t);
 END LOOP;
END $$;

CREATE FUNCTION lending_capacity_subject(request uuid) RETURNS text LANGUAGE sql STABLE AS $$
 SELECT CASE WHEN guarantor_member_id IS NOT NULL THEN 'CLIENT/'||guarantor_member_id::text
 ELSE 'EXTERNAL/'||upper(trim(external_station_id))||'/'||upper(trim(external_member_no)) END FROM guarantor_requests WHERE id=request;
$$;
CREATE FUNCTION lending_capacity_lock(request uuid,institution text,branch text) RETURNS boolean LANGUAGE plpgsql AS $$
DECLARE g guarantor_requests;l loan_applications;subject text;
BEGIN
 SELECT * INTO g FROM guarantor_requests WHERE id=request;
 SELECT * INTO l FROM loan_applications WHERE id=g.loan_application_id AND sacco_id=institution AND station_id=branch FOR UPDATE;
 IF l.id IS NULL THEN RETURN false; END IF;
 PERFORM pg_advisory_xact_lock(hashtextextended('CREDIT_POLICY/'||institution||'/'||branch,0));
 subject=lending_capacity_subject(request);IF subject IS NULL THEN RETURN false;END IF;
 IF g.guarantor_member_id IS NOT NULL THEN RETURN lending_guarantor_capacity_lock(g.guarantor_member_id,institution,branch);END IF;
 PERFORM pg_advisory_xact_lock(hashtextextended('CREDIT_CAPACITY/'||institution||'/'||subject,0));RETURN true;
END $$;
CREATE FUNCTION lending_capacity_error(request uuid,institution text,branch text,day date) RETURNS text LANGUAGE plpgsql STABLE AS $$
DECLARE g guarantor_requests;l loan_applications;a lending_guarantor_assessment;required boolean;reserved numeric;cap numeric;unknown bigint;own_debt numeric;
BEGIN
 SELECT * INTO g FROM guarantor_requests WHERE id=request;
 SELECT * INTO l FROM loan_applications WHERE id=g.loan_application_id AND sacco_id=institution AND station_id=branch;
 IF l.id IS NULL THEN RETURN 'credit.error.scope';END IF;
 SELECT * INTO a FROM lending_guarantor_assessment WHERE sacco_id=institution AND station_id=branch AND subject_key=lending_capacity_subject(request) ORDER BY revision DESC LIMIT 1;
 SELECT capacity_required INTO required FROM lending_credit_policy WHERE sacco_id=institution AND station_id=branch ORDER BY revision DESC LIMIT 1;
 -- Group guarantees retain their existing frozen, serialized capacity assessment.
 IF a.id IS NULL AND EXISTS(SELECT 1 FROM lending_group_guarantee WHERE loan_id=l.id AND guarantor_id=g.guarantor_member_id) THEN RETURN NULL;END IF;
 IF a.id IS NULL THEN RETURN CASE WHEN coalesce(required,false) THEN 'credit.error.assessmentRequired' ELSE NULL END;END IF;
 IF a.assessed_on>day OR a.valid_through<day THEN RETURN 'credit.error.expired';END IF;
 IF jsonb_typeof(l.financial_snapshot->'interestAmount') IS DISTINCT FROM 'number' OR (l.financial_snapshot->>'interestAmount')::numeric<0 THEN RETURN 'credit.error.history';END IF;
 cap=l.amount+(l.financial_snapshot->>'interestAmount')::numeric+coalesce((l.financial_snapshot->>'recordedChargeTotal')::numeric,0);
 IF g.guarantor_member_id IS NOT NULL THEN
  IF EXISTS(SELECT 1 FROM lending_guarantor_commitment WHERE guarantor_id=g.guarantor_member_id AND sacco_id=institution AND station_id<>branch)
   OR EXISTS(SELECT 1 FROM lending_shared_credit_obligation WHERE client_id=g.guarantor_member_id AND sacco_id=institution AND (station_id<>branch OR missing_history))
   OR EXISTS(SELECT 1 FROM loan_applications x LEFT JOIN loan_ledgers b ON b.loan_application_id=x.id WHERE x.applicant_member_id=g.guarantor_member_id AND x.sacco_id=institution AND x.status IN('DISBURSED','PAR','DEFAULTED','WRITTEN_OFF') AND (x.station_id<>branch OR b.loan_application_id IS NULL OR x.installment_amount IS NULL OR x.repayment_frequency IS NULL OR NOT EXISTS(SELECT 1 FROM loan_ledger_installments i WHERE i.loan_application_id=x.id)))
  THEN RETURN 'credit.error.history';END IF;
  SELECT coalesce(sum(amount_cap),0),count(*) FILTER(WHERE amount_cap IS NULL) INTO reserved,unknown FROM lending_guarantor_commitment WHERE guarantor_id=g.guarantor_member_id AND sacco_id=institution AND loan_id<>l.id;
  SELECT reserved+coalesce(sum(greatest(0,maximum_liability-allocated_obligation)),0) INTO reserved FROM lending_shared_credit_obligation WHERE client_id=g.guarantor_member_id AND sacco_id=institution;
  SELECT coalesce(sum(CASE WHEN x.repayment_frequency='WEEKLY' THEN scheduled.maximum*52/12 ELSE scheduled.maximum END),0) INTO own_debt FROM loan_applications x
   JOIN LATERAL(SELECT max(i.principal-i.principal_paid+i.interest-i.interest_paid-i.interest_excluded-i.interest_waived) maximum FROM loan_ledger_installments i WHERE i.loan_application_id=x.id) scheduled ON true
   WHERE x.sacco_id=institution AND x.applicant_member_id=g.guarantor_member_id AND x.status IN('DISBURSED','PAR','DEFAULTED','WRITTEN_OFF');
  SELECT own_debt+coalesce(sum(monthly_payment) FILTER(WHERE funded),0) INTO own_debt FROM lending_shared_credit_obligation WHERE client_id=g.guarantor_member_id AND sacco_id=institution;
 ELSE
  IF EXISTS(SELECT 1 FROM guarantor_requests x JOIN loan_applications b ON b.id=x.loan_application_id WHERE b.sacco_id=institution AND b.station_id<>branch AND lending_capacity_subject(x.id)=a.subject_key AND x.status='APPROVED' AND b.status NOT IN('PAID','WITHDRAWN','DISBURSEMENT_REVERSED','REJECTED','MANAGER_REJECTED','BOARD_REJECTED','LOAN_OFFICER_REJECTED','CHAIRPERSON_REJECTED','CREDIT_COMMITTEE_REJECTED','ACCOUNTANT_REJECTED')) THEN RETURN 'credit.error.history';END IF;
  SELECT coalesce(sum(b.amount+(b.financial_snapshot->>'interestAmount')::numeric+coalesce((b.financial_snapshot->>'recordedChargeTotal')::numeric,0)),0),count(*) FILTER(WHERE jsonb_typeof(b.financial_snapshot->'interestAmount') IS DISTINCT FROM 'number') INTO reserved,unknown
   FROM guarantor_requests x JOIN loan_applications b ON b.id=x.loan_application_id WHERE b.sacco_id=institution AND b.id<>l.id AND lending_capacity_subject(x.id)=a.subject_key AND x.status='APPROVED' AND b.status NOT IN('PAID','WITHDRAWN','DISBURSEMENT_REVERSED','REJECTED','MANAGER_REJECTED','BOARD_REJECTED','LOAN_OFFICER_REJECTED','CHAIRPERSON_REJECTED','CREDIT_COMMITTEE_REJECTED','ACCOUNTANT_REJECTED');
  own_debt=0;
 END IF;
 IF unknown>0 THEN RETURN 'credit.error.history';END IF;
 IF a.income-a.expenses-greatest(a.other_debt,own_debt)<=0 OR reserved+cap>least(a.capacity,
  (SELECT min(capacity) FROM lending_group_personal_commitment WHERE client_id=g.guarantor_member_id AND sacco_id=institution AND station_id=branch)) THEN RETURN 'credit.error.capacity';END IF;
 RETURN NULL;
END $$;
CREATE FUNCTION lending_capacity_approval_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE l loan_applications;failure text;assessment uuid;
BEGIN
 IF NEW.status='APPROVED' AND (TG_OP='INSERT' OR OLD.status IS DISTINCT FROM NEW.status) THEN
  SELECT * INTO l FROM loan_applications WHERE id=NEW.loan_application_id;
  IF NOT lending_capacity_lock(NEW.id,l.sacco_id,l.station_id) THEN RAISE EXCEPTION 'credit.error.scope';END IF;
  failure=lending_capacity_error(NEW.id,l.sacco_id,l.station_id,(current_timestamp AT TIME ZONE 'Africa/Dar_es_Salaam')::date);
  IF failure IS NOT NULL THEN RAISE EXCEPTION '%',failure;END IF;
  SELECT id INTO assessment FROM lending_guarantor_assessment WHERE sacco_id=l.sacco_id AND station_id=l.station_id AND subject_key=lending_capacity_subject(NEW.id) ORDER BY revision DESC LIMIT 1;
  IF assessment IS NOT NULL THEN INSERT INTO lending_guarantor_capacity_decision VALUES(gen_random_uuid(),NEW.id,assessment,l.amount+(l.financial_snapshot->>'interestAmount')::numeric+coalesce((l.financial_snapshot->>'recordedChargeTotal')::numeric,0),current_timestamp);END IF;
 END IF;RETURN NEW;
END $$;
CREATE CONSTRAINT TRIGGER credit_capacity_approval AFTER INSERT OR UPDATE ON guarantor_requests DEFERRABLE INITIALLY IMMEDIATE FOR EACH ROW EXECUTE FUNCTION lending_capacity_approval_guard();

CREATE FUNCTION lending_collateral_error(application uuid,institution text,branch text,day date) RETURNS text LANGUAGE plpgsql STABLE AS $$
DECLARE l loan_applications;p loan_product_settings;required boolean;available numeric;coverage_ratio numeric;
BEGIN
 SELECT * INTO l FROM loan_applications WHERE id=application AND sacco_id=institution AND station_id=branch;
 IF l.id IS NULL THEN RETURN 'credit.error.scope';END IF;
 SELECT collateral_required INTO required FROM lending_credit_policy WHERE sacco_id=institution AND station_id=branch ORDER BY revision DESC LIMIT 1;
 SELECT * INTO p FROM loan_product_settings WHERE id=l.loan_product_setting_id AND sacco_id=institution;
 IF NOT coalesce(required,false) OR NOT coalesce((l.policy_snapshot->>'collateralRequired')::boolean,p.collateral_required,false) THEN RETURN NULL;END IF;
 coverage_ratio=coalesce((l.policy_snapshot->>'collateralCoverageRatio')::numeric,p.min_collateral_coverage_ratio);
 IF coverage_ratio IS NULL OR coverage_ratio<0 THEN RETURN 'credit.error.history';END IF;
 SELECT coalesce(sum(x.amount),0) INTO available FROM lending_collateral_pledge x JOIN lending_collateral_valuation v ON v.asset_id=x.asset_id
 WHERE x.loan_id=l.id AND x.kind='PLEDGE' AND v.effective_date<=day AND v.valid_through>=day
  AND v.amount-v.encumbrance >= (SELECT coalesce(sum(y.amount),0) FROM lending_collateral_pledge y WHERE y.asset_id=x.asset_id AND y.kind='PLEDGE');
 IF available<l.amount*coverage_ratio OR available<=0 THEN RETURN 'credit.error.collateralRequired';END IF;RETURN NULL;
END $$;
CREATE FUNCTION lending_collateral_funding_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE failure text;
BEGIN
 IF NEW.status='DISBURSED' AND OLD.status IS DISTINCT FROM NEW.status THEN
  PERFORM pg_advisory_xact_lock(hashtextextended('CREDIT_POLICY/'||NEW.sacco_id||'/'||NEW.station_id,0));
  PERFORM a.id FROM lending_collateral_asset a JOIN lending_collateral_pledge p ON p.asset_id=a.id WHERE p.loan_id=NEW.id ORDER BY a.id FOR UPDATE OF a;
  failure=lending_collateral_error(NEW.id,NEW.sacco_id,NEW.station_id,(current_timestamp AT TIME ZONE 'Africa/Dar_es_Salaam')::date);
  IF failure IS NOT NULL THEN RAISE EXCEPTION '%',failure;END IF;
 END IF;RETURN NEW;
END $$;
CREATE TRIGGER credit_collateral_funding BEFORE UPDATE ON loan_applications FOR EACH ROW EXECUTE FUNCTION lending_collateral_funding_guard();

CREATE FUNCTION lending_collateral_check(application uuid,institution text,branch text,day date) RETURNS text LANGUAGE plpgsql AS $$
BEGIN
 PERFORM id FROM loan_applications WHERE id=application AND sacco_id=institution AND station_id=branch FOR UPDATE;
 IF NOT FOUND THEN RETURN 'credit.error.scope';END IF;
 PERFORM pg_advisory_xact_lock(hashtextextended('CREDIT_POLICY/'||institution||'/'||branch,0));
 PERFORM a.id FROM lending_collateral_asset a JOIN lending_collateral_pledge p ON p.asset_id=a.id WHERE p.loan_id=application ORDER BY a.id FOR UPDATE OF a;
 RETURN lending_collateral_error(application,institution,branch,day);
END $$;

CREATE FUNCTION lending_credit_evidence_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE asset lending_collateral_asset;last_sequence integer;pledged numeric;v lending_collateral_valuation;
 c lending_collection_case;last_kind text;actor uuid;loan loan_applications;
BEGIN
 actor=coalesce((to_jsonb(NEW)->>'actor_id')::uuid,NULL);
 IF NOT EXISTS(SELECT 1 FROM members m JOIN registered_saccos i ON i.sacco_id=m.sacco_id
  JOIN sacco_stations s ON s.sacco_id=m.sacco_id AND s.station_id=m.station_id
  WHERE m.id=actor AND m.sacco_id=NEW.sacco_id AND m.station_id=NEW.station_id AND m.status='ACTIVE'
   AND m.staff_access_status='ACTIVE' AND m.position<>'ADMIN' AND i.active AND s.active AND s.access_status='ACTIVE')
 THEN RAISE EXCEPTION 'credit.error.scope';END IF;
 IF TG_TABLE_NAME='lending_guarantor_assessment' THEN
  IF NOT lending_capacity_lock(NEW.request_id,NEW.sacco_id,NEW.station_id) OR NEW.subject_key IS DISTINCT FROM lending_capacity_subject(NEW.request_id)
   OR NEW.subject_key='CLIENT/'||actor::text THEN RAISE EXCEPTION 'credit.error.scope';END IF;
 ELSIF TG_TABLE_NAME='lending_collateral_event' THEN
  IF NEW.loan_id IS NOT NULL THEN SELECT * INTO loan FROM loan_applications WHERE id=NEW.loan_id AND sacco_id=NEW.sacco_id AND station_id=NEW.station_id FOR UPDATE;END IF;
  SELECT * INTO asset FROM lending_collateral_asset WHERE id=NEW.asset_id AND sacco_id=NEW.sacco_id AND station_id=NEW.station_id FOR UPDATE;
  SELECT coalesce(max(sequence),0) INTO last_sequence FROM lending_collateral_event WHERE asset_id=asset.id;
  IF asset.id IS NULL OR NEW.sequence<>last_sequence+1 THEN RAISE EXCEPTION 'credit.error.changed';END IF;
  SELECT coalesce(sum(amount),0) INTO pledged FROM lending_collateral_pledge WHERE asset_id=asset.id AND kind='PLEDGE';
  SELECT * INTO v FROM lending_collateral_valuation WHERE asset_id=asset.id;
  IF NEW.kind='PLEDGE' AND (v.asset_id IS NULL OR v.valid_through<NEW.effective_date OR pledged+NEW.amount>v.amount-v.encumbrance
   OR EXISTS(SELECT 1 FROM lending_collateral_pledge WHERE asset_id=asset.id AND loan_id=NEW.loan_id AND kind='PLEDGE')) THEN RAISE EXCEPTION 'credit.error.pledgeCapacity';END IF;
  IF NEW.kind='RELEASE' THEN
   IF NOT EXISTS(SELECT 1 FROM lending_collateral_pledge WHERE asset_id=asset.id AND loan_id=NEW.loan_id AND kind='PLEDGE')
    OR NOT(loan.status IN('PAID','WITHDRAWN','DISBURSEMENT_REVERSED','REJECTED') OR loan.status LIKE '%REJECTED')
    OR actor=(SELECT actor_id FROM lending_collateral_event WHERE asset_id=asset.id AND loan_id=NEW.loan_id AND kind='PLEDGE' ORDER BY sequence DESC LIMIT 1)
   THEN RAISE EXCEPTION 'credit.error.release';END IF;
   IF loan.status='PAID' AND NOT EXISTS(SELECT 1 FROM loan_ledgers b WHERE b.loan_application_id=loan.id AND b.principal=b.principal_paid
    AND EXISTS(SELECT 1 FROM loan_ledger_installments i WHERE i.loan_application_id=loan.id)
    AND NOT EXISTS(SELECT 1 FROM loan_ledger_installments i WHERE i.loan_application_id=loan.id AND i.principal-i.principal_paid+i.interest-i.interest_paid-i.interest_excluded-i.interest_waived>0)
    AND NOT EXISTS(SELECT 1 FROM loan_charge_balance charges_remaining WHERE charges_remaining.loan_id=loan.id AND charges_remaining.outstanding>0)) THEN RAISE EXCEPTION 'credit.error.history';END IF;
  END IF;
 ELSIF TG_TABLE_NAME='lending_collection_case' THEN
  PERFORM id FROM loan_applications WHERE id=NEW.loan_id AND sacco_id=NEW.sacco_id AND station_id=NEW.station_id FOR UPDATE;
  IF coalesce(lending_coverage_due(NEW.loan_id,NEW.recorded_at::date),0)<=0 OR EXISTS(SELECT 1 FROM lending_collection_case prior_case
    WHERE prior_case.loan_id=NEW.loan_id AND coalesce((SELECT kind FROM lending_collection_event e WHERE e.case_id=prior_case.id AND kind IN('CLOSE','REOPEN') ORDER BY sequence DESC LIMIT 1),'OPEN')<>'CLOSE')
  THEN RAISE EXCEPTION 'credit.error.openCase';END IF;
 ELSIF TG_TABLE_NAME='lending_collection_event' THEN
  SELECT * INTO c FROM lending_collection_case WHERE id=NEW.case_id AND sacco_id=NEW.sacco_id AND station_id=NEW.station_id;
  PERFORM id FROM loan_applications WHERE id=c.loan_id FOR UPDATE;
  PERFORM id FROM lending_collection_case WHERE id=c.id FOR UPDATE;
  SELECT coalesce(max(sequence),0) INTO last_sequence FROM lending_collection_event WHERE case_id=c.id;
  SELECT kind INTO last_kind FROM lending_collection_event WHERE case_id=c.id AND kind IN('CLOSE','REOPEN') ORDER BY sequence DESC LIMIT 1;
  IF c.id IS NULL OR NEW.sequence<>last_sequence+1 OR (coalesce(last_kind,'OPEN')='CLOSE')<>(NEW.kind='REOPEN') THEN RAISE EXCEPTION 'credit.error.changed';END IF;
 END IF;
 IF TG_TABLE_NAME IN('lending_collection_case','lending_collection_event') THEN
 IF NEW.assigned_to IS NOT NULL AND NOT EXISTS(
  SELECT 1 FROM members WHERE id=NEW.assigned_to AND sacco_id=NEW.sacco_id AND station_id=NEW.station_id AND status='ACTIVE' AND staff_access_status='ACTIVE' AND position<>'ADMIN') THEN RAISE EXCEPTION 'credit.error.assignedTo';END IF;
 END IF;
 RETURN NEW;
END $$;
DO $$ DECLARE t text;BEGIN
 FOREACH t IN ARRAY ARRAY['lending_credit_policy','lending_guarantor_assessment','lending_collateral_asset','lending_collateral_event','lending_collection_case','lending_collection_event'] LOOP
 EXECUTE format('ALTER TABLE %I ADD FOREIGN KEY(sacco_id) REFERENCES registered_saccos(sacco_id)',t);
 EXECUTE format('ALTER TABLE %I ADD FOREIGN KEY(sacco_id,station_id) REFERENCES sacco_stations(sacco_id,station_id)',t);
 EXECUTE format('CREATE TRIGGER %I BEFORE INSERT ON %I FOR EACH ROW EXECUTE FUNCTION lending_credit_evidence_guard()',t||'_scope',t);
 END LOOP;
END $$;

-- A later group offer cannot spend a budget already assessed for an ordinary guarantee.
CREATE FUNCTION lending_reviewed_group_capacity_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE client uuid;institution text;branch text;target uuid;cap numeric;a lending_guarantor_assessment;
 g lending_group_personal_guarantee;reserved numeric;unknown bigint;
BEGIN
 IF TG_TABLE_NAME='lending_group_personal_decision' THEN
  IF NEW.decision<>'ACCEPTED' THEN RETURN NEW;END IF;
  SELECT * INTO g FROM lending_group_personal_guarantee WHERE id=NEW.offer_id;
  client=g.client_id;institution=g.sacco_id;branch=g.station_id;target=g.loan_id;cap=g.amount_cap;
 ELSE
  client=CASE WHEN TG_TABLE_NAME='lending_group_guarantee' THEN (to_jsonb(NEW)->>'guarantor_id')::uuid ELSE (to_jsonb(NEW)->>'client_id')::uuid END;
  institution=NEW.sacco_id;branch=NEW.station_id;target=NEW.loan_id;cap=NEW.amount_cap;
 END IF;
 IF NOT lending_guarantor_capacity_lock(client,institution,branch) THEN RAISE EXCEPTION 'credit.error.scope';END IF;
 SELECT * INTO a FROM lending_guarantor_assessment WHERE sacco_id=institution AND station_id=branch AND subject_key='CLIENT/'||client::text ORDER BY revision DESC LIMIT 1;
 IF a.id IS NULL THEN RETURN NEW;END IF;
 IF a.valid_through<(current_timestamp AT TIME ZONE 'Africa/Dar_es_Salaam')::date THEN RAISE EXCEPTION 'credit.error.expired';END IF;
 IF EXISTS(SELECT 1 FROM lending_guarantor_commitment WHERE guarantor_id=client AND sacco_id=institution AND station_id<>branch)
  OR EXISTS(SELECT 1 FROM lending_shared_credit_obligation WHERE client_id=client AND sacco_id=institution AND (station_id<>branch OR missing_history)) THEN RAISE EXCEPTION 'credit.error.history';END IF;
 SELECT coalesce(sum(amount_cap),0),count(*) FILTER(WHERE amount_cap IS NULL) INTO reserved,unknown FROM lending_guarantor_commitment WHERE guarantor_id=client AND sacco_id=institution AND loan_id<>target;
 SELECT reserved+coalesce(sum(greatest(0,maximum_liability-allocated_obligation)),0) INTO reserved FROM lending_shared_credit_obligation WHERE client_id=client AND sacco_id=institution;
 IF unknown>0 OR cap IS NULL OR reserved+cap>a.capacity THEN RAISE EXCEPTION 'credit.error.capacity';END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER credit_reviewed_group_guarantee BEFORE INSERT ON lending_group_guarantee FOR EACH ROW EXECUTE FUNCTION lending_reviewed_group_capacity_guard();
CREATE TRIGGER credit_reviewed_personal_guarantee BEFORE INSERT ON lending_group_personal_guarantee FOR EACH ROW EXECUTE FUNCTION lending_reviewed_group_capacity_guard();
CREATE TRIGGER credit_reviewed_personal_acceptance BEFORE INSERT ON lending_group_personal_decision FOR EACH ROW EXECUTE FUNCTION lending_reviewed_group_capacity_guard();
