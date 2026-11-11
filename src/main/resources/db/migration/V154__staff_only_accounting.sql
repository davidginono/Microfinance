-- Retire borrower authentication without deleting identities, contracts or financial evidence.
CREATE TABLE staff_only_client_cutover AS
 SELECT id AS member_id,client_portal_enabled AS had_borrower_login,status AS previous_status,now() AS recorded_at
 FROM members WHERE is_member IS TRUE OR position='MEMBER';
ALTER TABLE staff_only_client_cutover ADD PRIMARY KEY(member_id);
ALTER TABLE members DROP CONSTRAINT ck_provisional_client_credentials;
ALTER TABLE members ALTER COLUMN client_portal_enabled SET DEFAULT false;
UPDATE members SET client_portal_enabled=false;
UPDATE members m SET username=NULL,password_hash='!NO_PORTAL_ACCESS'
 WHERE (m.position IS NULL OR m.position='MEMBER') AND NOT EXISTS(SELECT 1 FROM member_staff_roles r WHERE r.member_id=m.id AND r.role_name<>'MEMBER');
-- An unfinished borrower sign-up becomes an unverified client record, with no login.
UPDATE members m SET status='ACTIVE' WHERE m.position='MEMBER' AND m.status='INVITED'
 AND NOT EXISTS(SELECT 1 FROM member_staff_roles r WHERE r.member_id=m.id AND r.role_name<>'MEMBER');
ALTER TABLE members ADD CONSTRAINT staff_only_no_borrower_login CHECK(NOT client_portal_enabled);
-- Existing assignments were already authorized; remove their retired borrower acknowledgement gate.
UPDATE members SET staff_access_status='ACTIVE',staff_access_activated_at=now()
 WHERE staff_access_status='PENDING_ACKNOWLEDGEMENT';

ALTER TABLE members DROP CONSTRAINT IF EXISTS members_position_check;
ALTER TABLE member_staff_roles DROP CONSTRAINT IF EXISTS member_staff_roles_role_name_check;
ALTER TABLE members ADD CONSTRAINT members_position_check CHECK(position IN
 ('MEMBER','ADMIN','MINOR_ADMIN','MANAGER','ACCOUNTANT','LOAN_OFFICER','DISBURSEMENT_OFFICER','CHAIRPERSON','BOARD','CREDIT_COMMITTEE',
  'FINANCE_MANAGER','ICT_MANAGER','CREDIT_OFFICER','ACCOUNTS_CLERK','CUSTOMER_CARE','ACCOUNTS_CLERK_2'));
ALTER TABLE member_staff_roles ADD CONSTRAINT member_staff_roles_role_name_check CHECK(role_name IN
 ('MEMBER','ADMIN','MINOR_ADMIN','MANAGER','ACCOUNTANT','LOAN_OFFICER','DISBURSEMENT_OFFICER','CHAIRPERSON','BOARD','CREDIT_COMMITTEE',
  'FINANCE_MANAGER','ICT_MANAGER','CREDIT_OFFICER','ACCOUNTS_CLERK','CUSTOMER_CARE','ACCOUNTS_CLERK_2'));
-- Retain legacy metadata while frozen voucher plans still reference it. No permission is granted.
INSERT INTO member_staff_roles(member_id,role_name)
 SELECT DISTINCT m.id,x.new_role FROM members m
 JOIN (VALUES ('MINOR_ADMIN','ICT_MANAGER'),('CHAIRPERSON','FINANCE_MANAGER'),('BOARD','CREDIT_OFFICER'),
              ('CREDIT_COMMITTEE','CREDIT_OFFICER'),('DISBURSEMENT_OFFICER','ACCOUNTS_CLERK')) x(old_role,new_role)
 ON m.position=x.old_role OR EXISTS(SELECT 1 FROM member_staff_roles r WHERE r.member_id=m.id AND r.role_name=x.old_role)
 ON CONFLICT DO NOTHING;

-- Fingerprint assessed terms, independent of later posting-account links and payout proof.
-- JSON numeric scale is presentation: 10.00 and 10.0 are the same assessed amount.
CREATE FUNCTION loan_group_canonical(value jsonb) RETURNS jsonb LANGUAGE sql IMMUTABLE AS $$
 SELECT CASE jsonb_typeof(value)
  WHEN 'number' THEN to_jsonb(trim_scale(value::text::numeric))
  WHEN 'array' THEN coalesce((SELECT jsonb_agg(loan_group_canonical(item) ORDER BY ordinal)
    FROM jsonb_array_elements(value) WITH ORDINALITY x(item,ordinal)),'[]'::jsonb)
  WHEN 'object' THEN coalesce((SELECT jsonb_object_agg(key,loan_group_canonical(item))
    FROM jsonb_each(value) x(key,item)),'{}'::jsonb)
  ELSE value END
$$;
CREATE FUNCTION loan_group_terms(l loan_applications) RETURNS text LANGUAGE sql IMMUTABLE AS $$
 SELECT md5(loan_group_canonical(jsonb_build_array(l.amount,l.loan_product_setting_id,l.tenor_months,l.form_data,
  l.financial_snapshot::jsonb-'recordedCharges',
  (SELECT jsonb_agg(jsonb_build_array(t->'id',t->'kind',t->'basis',t->'basisValue',t->'amount',t->'timing',t->'evidence') ORDER BY ordinal)
   FROM jsonb_array_elements(coalesce(l.financial_snapshot::jsonb->'recordedCharges'->'terms','[]'::jsonb)) WITH ORDINALITY x(t,ordinal)),
  l.policy_snapshot::jsonb-'recordedChargePolicy',
  (SELECT jsonb_agg(file ORDER BY ordinal) FROM jsonb_array_elements(coalesce(l.attachments_json::jsonb,'[]'::jsonb)) WITH ORDINALITY x(file,ordinal)
   WHERE coalesce(file->>'category','')<>'DISBURSEMENT_PROOF'),
  l.repayment_frequency,l.borrower_party_id))::text)
$$;
CREATE FUNCTION loan_group_related(application uuid,person uuid) RETURNS boolean LANGUAGE sql STABLE AS $$
 SELECT EXISTS(SELECT 1 FROM loan_applications WHERE id=application AND applicant_member_id=person)
 OR EXISTS(SELECT 1 FROM lending_group_participant p WHERE p.client_id=person AND
   (p.loan_id=application OR EXISTS(SELECT 1 FROM lending_group_shared_facility f WHERE f.cycle_id=p.cycle_id AND f.loan_id=application)))
 OR EXISTS(SELECT 1 FROM lending_group_org_agreement a JOIN lending_group_authority_signer s ON s.authority_id=a.authority_id
   WHERE a.loan_id=application AND s.client_id=person AND (s.may_apply OR s.may_sign OR s.may_receive))
$$;
CREATE TABLE loan_group_rule (
 id uuid PRIMARY KEY,sacco_id varchar(255) NOT NULL,station_id varchar(255) NOT NULL,
 product_id uuid NOT NULL REFERENCES loan_product_settings(id),revision integer NOT NULL CHECK(revision>0),
 steps jsonb NOT NULL CHECK(jsonb_typeof(steps)='array' AND jsonb_array_length(steps) BETWEEN 1 AND 10),
 actor_id uuid NOT NULL REFERENCES members(id),recorded_at timestamptz NOT NULL,
 UNIQUE(sacco_id,station_id,product_id,revision),UNIQUE(id,sacco_id,station_id),
 FOREIGN KEY(sacco_id,station_id) REFERENCES sacco_stations(sacco_id,station_id)
);
CREATE TABLE loan_group_request (
 id uuid PRIMARY KEY,loan_id uuid NOT NULL REFERENCES loan_applications(id),sacco_id varchar(255) NOT NULL,station_id varchar(255) NOT NULL,
 round integer NOT NULL CHECK(round>0),rule_id uuid NOT NULL,terms_hash varchar(64) NOT NULL,
 previous_status varchar(64) NOT NULL,maker_id uuid NOT NULL REFERENCES members(id),recorded_at timestamptz NOT NULL,
 UNIQUE(loan_id,round),UNIQUE(id,sacco_id,station_id),
 FOREIGN KEY(rule_id,sacco_id,station_id) REFERENCES loan_group_rule(id,sacco_id,station_id)
);
CREATE INDEX ix_loan_group_queue ON loan_group_request(sacco_id,station_id,recorded_at DESC,id DESC);
CREATE TABLE loan_group_decision (
 id uuid PRIMARY KEY,request_id uuid NOT NULL REFERENCES loan_group_request(id),step integer NOT NULL CHECK(step BETWEEN 0 AND 9),
 actor_id uuid NOT NULL REFERENCES members(id),decision varchar(16) NOT NULL CHECK(decision IN('APPROVE','REJECT')),
 reason varchar(500) NOT NULL CHECK(length(trim(reason))>0),evidence varchar(500) NOT NULL CHECK(length(trim(evidence))>0),
 recorded_at timestamptz NOT NULL,UNIQUE(request_id,actor_id)
);
CREATE FUNCTION loan_group_scope() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_TABLE_NAME='loan_group_rule' THEN
  IF NOT EXISTS(SELECT 1 FROM members m WHERE m.id=NEW.actor_id AND m.sacco_id=NEW.sacco_id AND m.station_id=NEW.station_id
    AND m.status='ACTIVE' AND (m.staff_access_status='ACTIVE' OR (m.staff_access_status='NONE' AND m.position<>'MEMBER')))
  THEN RAISE EXCEPTION 'Loan rule actor scope mismatch'; END IF;
  IF EXISTS(SELECT 1 FROM jsonb_array_elements(NEW.steps) s WHERE jsonb_typeof(s)<>'object'
   OR coalesce(s->>'group','') NOT IN('LOAN_OFFICER','MANAGER','FINANCE_MANAGER','ACCOUNTANT','ICT_MANAGER','CREDIT_OFFICER','ACCOUNTS_CLERK','CUSTOMER_CARE','ACCOUNTS_CLERK_2')
   OR coalesce(s->>'approvals','') !~ '^(10|[1-9])$')
   OR (SELECT sum((s->>'approvals')::integer) FROM jsonb_array_elements(NEW.steps) s)>50
  THEN RAISE EXCEPTION 'Invalid ordered loan groups'; END IF;
  IF NOT EXISTS(SELECT 1 FROM loan_product_settings WHERE id=NEW.product_id AND sacco_id=NEW.sacco_id)
  THEN RAISE EXCEPTION 'Loan rule product scope mismatch'; END IF;
 ELSE
  IF NOT EXISTS(SELECT 1 FROM members m WHERE m.id=NEW.maker_id AND m.sacco_id=NEW.sacco_id AND m.station_id=NEW.station_id
    AND m.status='ACTIVE' AND (m.staff_access_status='ACTIVE' OR (m.staff_access_status='NONE' AND m.position<>'MEMBER')))
  THEN RAISE EXCEPTION 'Loan request actor scope mismatch'; END IF;
  IF NOT EXISTS(SELECT 1 FROM loan_applications a JOIN loan_group_rule r ON r.id=NEW.rule_id
   WHERE a.id=NEW.loan_id AND a.sacco_id=NEW.sacco_id AND a.station_id=NEW.station_id
   AND r.product_id=a.loan_product_setting_id AND r.sacco_id=a.sacco_id AND r.station_id=a.station_id)
  THEN RAISE EXCEPTION 'Loan request scope mismatch'; END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER loan_group_rule_scope BEFORE INSERT ON loan_group_rule FOR EACH ROW EXECUTE FUNCTION loan_group_scope();
CREATE TRIGGER loan_group_request_scope BEFORE INSERT ON loan_group_request FOR EACH ROW EXECUTE FUNCTION loan_group_scope();
CREATE TRIGGER loan_group_rule_history BEFORE UPDATE OR DELETE ON loan_group_rule FOR EACH ROW EXECUTE FUNCTION gl_protect_evidence();
CREATE TRIGGER loan_group_request_history BEFORE UPDATE OR DELETE ON loan_group_request FOR EACH ROW EXECUTE FUNCTION gl_protect_evidence();
CREATE TRIGGER loan_group_decision_history BEFORE UPDATE OR DELETE ON loan_group_decision FOR EACH ROW EXECUTE FUNCTION gl_protect_evidence();
CREATE TRIGGER staff_only_cutover_history BEFORE UPDATE OR DELETE ON staff_only_client_cutover FOR EACH ROW EXECUTE FUNCTION gl_protect_evidence();
CREATE FUNCTION loan_group_decision_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE request loan_group_request; loan loan_applications; plan jsonb; required integer; current_step integer;
BEGIN
 SELECT * INTO request FROM loan_group_request WHERE id=NEW.request_id;
 SELECT * INTO loan FROM loan_applications WHERE id=request.loan_id FOR UPDATE;
 SELECT steps INTO plan FROM loan_group_rule WHERE id=request.rule_id;
 SELECT min(s.ordinality-1) INTO current_step FROM jsonb_array_elements(plan) WITH ORDINALITY s(value,ordinality)
 WHERE (SELECT count(*) FROM loan_group_decision d WHERE d.request_id=request.id AND d.step=s.ordinality-1 AND d.decision='APPROVE')<(s.value->>'approvals')::integer;
 IF loan.status<>'SUBMITTED' OR current_step IS NULL OR NEW.step<>current_step OR request.terms_hash IS DISTINCT FROM loan_group_terms(loan)
 OR request.id IS DISTINCT FROM (SELECT id FROM loan_group_request WHERE loan_id=loan.id ORDER BY round DESC LIMIT 1)
 OR EXISTS(SELECT 1 FROM loan_group_decision WHERE request_id=request.id AND decision='REJECT')
  OR NEW.actor_id=request.maker_id OR loan_group_related(loan.id,NEW.actor_id)
 OR EXISTS(SELECT 1 FROM accountant_loan_record WHERE loan_id=loan.id AND actor_id=NEW.actor_id)
 OR NOT EXISTS(SELECT 1 FROM members m WHERE m.id=NEW.actor_id AND m.sacco_id=request.sacco_id AND m.station_id=request.station_id
   AND m.status='ACTIVE' AND m.position<>'ADMIN' AND NOT EXISTS(SELECT 1 FROM member_staff_roles r WHERE r.member_id=m.id AND r.role_name='ADMIN')
   AND (m.staff_access_status='ACTIVE' OR (m.staff_access_status='NONE' AND coalesce(m.staff_no,'')=''))
   AND EXISTS(SELECT 1 FROM member_access_claims c WHERE c.member_id=m.id AND c.claim_name='LOAN_RECORDING_VIEW')
   AND EXISTS(SELECT 1 FROM member_access_claims c WHERE c.member_id=m.id AND c.claim_name='LOAN_RECORDING_'||NEW.decision)
   AND (m.position=plan->NEW.step->>'group' OR EXISTS(SELECT 1 FROM member_staff_roles r WHERE r.member_id=m.id AND r.role_name=plan->NEW.step->>'group')))
 THEN RAISE EXCEPTION 'Loan approval step, group or independence mismatch'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER loan_group_decision_scope BEFORE INSERT ON loan_group_decision FOR EACH ROW EXECUTE FUNCTION loan_group_decision_guard();
CREATE FUNCTION loan_group_funding_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE request loan_group_request; plan jsonb;
BEGIN
 SELECT * INTO request FROM loan_group_request WHERE loan_id=NEW.id ORDER BY round DESC LIMIT 1;
 IF request.id IS NULL THEN
  IF NEW.policy_snapshot::jsonb->>'recordingWorkflowVersion'='GROUP_REVIEW_V1'
   AND NEW.status IN('READY_FOR_DISBURSEMENT','DISBURSED') AND NEW.status IS DISTINCT FROM OLD.status
  THEN RAISE EXCEPTION 'Complete unchanged loan group approval required'; END IF;
  RETURN NEW;
 END IF;
 -- Payment corrections may restore an already-funded loan's active status.
 IF NEW.status IN('READY_FOR_DISBURSEMENT','DISBURSED') AND OLD.status IS DISTINCT FROM NEW.status
 AND NOT (NEW.status='DISBURSED' AND OLD.status IN('PAID','PAR','DEFAULTED','WRITTEN_OFF')
   AND EXISTS(SELECT 1 FROM loan_ledgers WHERE loan_application_id=OLD.id)) THEN
  SELECT steps INTO plan FROM loan_group_rule WHERE id=request.rule_id;
  IF EXISTS(SELECT 1 FROM loan_group_decision WHERE request_id=request.id AND decision='REJECT')
   OR EXISTS(SELECT 1 FROM jsonb_array_elements(plan) WITH ORDINALITY s(value,ordinality)
    WHERE (SELECT count(*) FROM loan_group_decision d WHERE d.request_id=request.id AND d.step=s.ordinality-1 AND d.decision='APPROVE')<(s.value->>'approvals')::integer)
   OR request.terms_hash IS DISTINCT FROM loan_group_terms(OLD)
   OR request.terms_hash IS DISTINCT FROM loan_group_terms(NEW)
  THEN RAISE EXCEPTION 'Complete unchanged loan group approval required'; END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER loan_group_funding BEFORE UPDATE ON loan_applications FOR EACH ROW EXECUTE FUNCTION loan_group_funding_guard();
CREATE TRIGGER loan_group_rule_no_truncate BEFORE TRUNCATE ON loan_group_rule FOR EACH STATEMENT EXECUTE FUNCTION gl_protect_evidence();
CREATE TRIGGER loan_group_request_no_truncate BEFORE TRUNCATE ON loan_group_request FOR EACH STATEMENT EXECUTE FUNCTION gl_protect_evidence();
CREATE TRIGGER loan_group_decision_no_truncate BEFORE TRUNCATE ON loan_group_decision FOR EACH STATEMENT EXECUTE FUNCTION gl_protect_evidence();
-- Staff may continue an old submitted profile by appending a new revision. Old submissions remain evidence.
CREATE OR REPLACE FUNCTION client_onboarding_self_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 PERFORM pg_advisory_xact_lock(hashtextextended('CLIENT_KYC/'||NEW.client_id,0));
 IF TG_TABLE_NAME IN('client_onboarding_revision','client_onboarding_document') THEN
  IF NEW.actor_id=NEW.client_id OR NOT EXISTS(SELECT 1 FROM members m WHERE m.id=NEW.actor_id
   AND m.sacco_id=NEW.sacco_id AND m.station_id=NEW.station_id AND m.status='ACTIVE'
   AND (m.staff_access_status='ACTIVE' OR (m.staff_access_status='NONE' AND m.position<>'MEMBER')))
  THEN RAISE EXCEPTION 'kyc.error.scope';END IF;
 END IF;
 IF TG_TABLE_NAME='client_onboarding_revision' THEN
  IF NEW.mode NOT IN('DRAFT','RECORDED') OR coalesce((NEW.profile->>'selfService')::boolean,false)
  THEN RAISE EXCEPTION 'kyc.error.scope';END IF;
 ELSIF TG_TABLE_NAME='client_onboarding_document' THEN
  IF NOT EXISTS(SELECT 1 FROM client_onboarding_current WHERE client_id=NEW.client_id AND sacco_id=NEW.sacco_id
    AND station_id=NEW.station_id AND state IN('DRAFT','RECORDED')) THEN RAISE EXCEPTION 'kyc.error.state';END IF;
 ELSIF TG_TABLE_NAME='client_onboarding_decision' THEN
  IF NEW.kind IN('RETURN','REJECT','REVOKE') AND length(trim(NEW.client_message))=0 THEN RAISE EXCEPTION 'kyc.error.clientMessage';END IF;
 END IF;
 RETURN NEW;
END $$;

CREATE TRIGGER staff_only_cutover_no_truncate BEFORE TRUNCATE ON staff_only_client_cutover FOR EACH STATEMENT EXECUTE FUNCTION gl_protect_evidence();
-- Recording a profile is separate from an independent check of its retained evidence.
DO $$ DECLARE definition text; name text; BEGIN
 FOREACH name IN ARRAY ARRAY['client_onboarding_complete(uuid,date)','client_onboarding_scope_guard()','client_onboarding_reserve_identity()'] LOOP
  SELECT pg_get_functiondef(name::regprocedure) INTO definition;
  definition=replace(definition,'mode=''SUBMITTED''','mode IN (''SUBMITTED'',''RECORDED'')');
  definition=replace(definition,'mode = ''SUBMITTED''','mode IN (''SUBMITTED'',''RECORDED'')');
  definition=replace(definition,'state=''SUBMITTED''','state IN (''SUBMITTED'',''RECORDED'')');
  EXECUTE definition;
 END LOOP;
END $$;
-- Preserve frozen contract protections while allowing the new evidenced staff review rounds.
DO $$ DECLARE definition text; marker text='IF NEW.status IS DISTINCT FROM OLD.status AND NOT ('; BEGIN
 SELECT pg_get_functiondef('accountant_loan_terms_protect()'::regprocedure) INTO definition;
 IF strpos(definition,marker)=0 THEN RAISE EXCEPTION 'Recorded transition guard marker missing'; END IF;
 EXECUTE replace(definition,marker,marker||$rule$
  (EXISTS(SELECT 1 FROM loan_group_request r WHERE r.loan_id=OLD.id)
   AND ((NEW.status='SUBMITTED' AND OLD.status NOT IN('DISBURSED','PAR','DEFAULTED','PAID','WRITTEN_OFF','DISBURSEMENT_REVERSED','WITHDRAWN','READY_FOR_DISBURSEMENT'))
    OR (OLD.status='SUBMITTED' AND NEW.status IN('REJECTED','READY_FOR_DISBURSEMENT'))))
  OR (OLD.status='AWAITING_GUARANTORS' AND NEW.status='RECORDED' AND lending_personal_guarantees_ready(OLD.id)) OR $rule$);
END $$;
