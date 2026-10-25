-- New organization policy revisions opt in explicitly. Existing unsupported configurations stay closed.
ALTER TABLE lending_group_policy ADD COLUMN capability_version smallint NOT NULL DEFAULT 0 CHECK(capability_version IN (0,1));
ALTER TABLE lending_group_policy DROP CONSTRAINT lending_group_policy_min_members_check;
ALTER TABLE lending_group_policy ADD CONSTRAINT lending_group_policy_min_members_check CHECK(min_members BETWEEN CASE WHEN model='ORGANIZATION' THEN 1 ELSE 2 END AND 50);
ALTER TABLE loan_applications ALTER COLUMN applicant_member_id DROP NOT NULL;
ALTER TABLE loan_applications ADD CONSTRAINT uq_group_facility_loan_scope UNIQUE(id,sacco_id,station_id);
CREATE TABLE lending_group_facility (
 cycle_id uuid PRIMARY KEY,loan_id uuid NOT NULL UNIQUE,group_id uuid NOT NULL,sacco_id varchar(255) NOT NULL,station_id varchar(255) NOT NULL,
 model varchar(16) NOT NULL CHECK(model='ORGANIZATION'),authority_id uuid NOT NULL UNIQUE,representative_id uuid NOT NULL REFERENCES members(id),
 monthly_payment numeric(18,2) NOT NULL CHECK(monthly_payment>0),created_by uuid NOT NULL REFERENCES members(id),created_at timestamptz NOT NULL,
 FOREIGN KEY(cycle_id,sacco_id,station_id) REFERENCES lending_group_cycle(id,sacco_id,station_id),
 FOREIGN KEY(loan_id,sacco_id,station_id) REFERENCES loan_applications(id,sacco_id,station_id),
 FOREIGN KEY(group_id,sacco_id,station_id) REFERENCES lending_group(id,sacco_id,station_id),
 FOREIGN KEY(authority_id,group_id,sacco_id,station_id) REFERENCES lending_group_authority(id,group_id,sacco_id,station_id)
);
CREATE INDEX ix_group_facility_scope ON lending_group_facility(sacco_id,station_id,group_id,loan_id);
CREATE TRIGGER group_facility_immutable BEFORE UPDATE OR DELETE ON lending_group_facility FOR EACH ROW EXECUTE FUNCTION gl_protect_evidence();

CREATE OR REPLACE FUNCTION loan_borrower_identity_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE party loan_borrower_party;
BEGIN
 IF TG_OP='UPDATE' AND ROW(NEW.borrower_party_id,NEW.applicant_member_id,NEW.sacco_id)
   IS DISTINCT FROM ROW(OLD.borrower_party_id,OLD.applicant_member_id,OLD.sacco_id)
 THEN RAISE EXCEPTION 'Loan borrower identity is immutable'; END IF;
 IF TG_OP='UPDATE' THEN RETURN NEW; END IF;
 IF NEW.borrower_party_id IS NULL THEN NEW.borrower_party_id=NEW.applicant_member_id; END IF;
 IF NEW.applicant_member_id IS NOT NULL THEN
  INSERT INTO loan_borrower_party(id,sacco_id,kind,client_id) VALUES(NEW.applicant_member_id,NEW.sacco_id,'CLIENT',NEW.applicant_member_id) ON CONFLICT(id) DO NOTHING;
 END IF;
 SELECT * INTO party FROM loan_borrower_party WHERE id=NEW.borrower_party_id AND sacco_id=NEW.sacco_id;
 IF NOT FOUND THEN RAISE EXCEPTION 'Loan borrower identity unavailable'; END IF;
 IF party.kind='CLIENT' AND NEW.applicant_member_id IS NOT DISTINCT FROM party.client_id THEN RETURN NEW; END IF;
 IF party.kind='ORGANIZATION' AND NEW.applicant_member_id IS NULL AND party.group_station_id=NEW.station_id
   AND NEW.status='RECORDED' AND NEW.top_up_source_loan_id IS NULL
   AND EXISTS(SELECT 1 FROM lending_group g WHERE g.id=party.group_id AND g.sacco_id=party.sacco_id AND g.station_id=NEW.station_id AND g.legal_form='ORGANIZATION' AND g.status='ACTIVE')
 THEN RETURN NEW; END IF;
 RAISE EXCEPTION 'Unsupported loan borrower or origination path';
END $$;

CREATE FUNCTION lending_group_facility_commit() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE application uuid; f lending_group_facility; c lending_group_cycle; a loan_applications; p lending_group_policy; h lending_group_authority;
BEGIN
 IF TG_TABLE_NAME='loan_applications' THEN application=NEW.id;
 ELSIF TG_TABLE_NAME='lending_group_cycle' THEN SELECT loan_id INTO application FROM lending_group_facility WHERE cycle_id=NEW.id;
 ELSE application=NEW.loan_id; END IF;
 SELECT * INTO a FROM loan_applications WHERE id=application;
 IF NOT FOUND THEN RETURN NULL; END IF;
 IF NOT EXISTS(SELECT 1 FROM loan_borrower_party WHERE id=a.borrower_party_id AND kind='ORGANIZATION') THEN
 IF TG_TABLE_NAME='lending_group_facility' THEN RAISE EXCEPTION 'Organization facility requires an organization borrower'; END IF;
 RETURN NULL;
END IF;
 SELECT * INTO f FROM lending_group_facility WHERE loan_id=a.id;
 IF NOT FOUND THEN RAISE EXCEPTION 'Organization application requires its group facility'; END IF;
 SELECT * INTO c FROM lending_group_cycle WHERE id=f.cycle_id;
 SELECT * INTO p FROM lending_group_policy WHERE id=c.policy_id;
 SELECT * INTO h FROM lending_group_authority WHERE id=f.authority_id;
 IF c.state NOT IN ('SUBMITTED','CANCELLED') OR c.model<>'ORGANIZATION' OR p.capability_version<>1
  OR ROW(c.group_id,c.sacco_id,c.station_id,c.product_id) IS DISTINCT FROM ROW(f.group_id,a.sacco_id,a.station_id,a.loan_product_setting_id)
  OR NOT EXISTS(SELECT 1 FROM loan_borrower_party b WHERE b.id=a.borrower_party_id AND b.group_id=f.group_id AND b.group_station_id=f.station_id)
  OR a.amount>least(p.max_principal,h.max_principal) OR a.applicant_member_id IS NOT NULL OR a.required_guarantors IS DISTINCT FROM 0
  OR NOT EXISTS(SELECT 1 FROM accountant_loan_record r WHERE r.loan_id=a.id AND r.sacco_id=f.sacco_id AND r.station_id=f.station_id)
  OR a.financial_snapshot->>'loanMode' IS DISTINCT FROM 'CONFIGURED' OR a.financial_snapshot->>'recordingSource' IS DISTINCT FROM 'ACCOUNTANT_DIRECT'
  OR c.form_data->'organization'->>'authorityId' IS DISTINCT FROM f.authority_id::text
  OR c.form_data->'organization'->>'representativeId' IS DISTINCT FROM f.representative_id::text
  OR EXISTS(SELECT 1 FROM lending_group_participant x WHERE x.cycle_id=c.id)
 THEN RAISE EXCEPTION 'Organization facility and recorded contract must agree'; END IF;
 IF a.payment_details_snapshot IS NULL OR NOT coalesce((a.payment_details_snapshot->>'available')::boolean,false)
  OR a.payment_details_snapshot->>'destinationType' IS DISTINCT FROM h.payout_type
  OR a.payment_details_snapshot->>'provider' IS DISTINCT FROM h.payout_provider
  OR a.payment_details_snapshot->>'accountHolderName' IS DISTINCT FROM h.payout_holder
  OR a.payment_details_snapshot->>'accountIdentifier' IS DISTINCT FROM h.payout_identifier
 THEN RAISE EXCEPTION 'Organization payout must remain the verified authority destination'; END IF;
 IF c.state='CANCELLED' AND a.status NOT IN ('WITHDRAWN','MANAGER_REJECTED','LOAN_OFFICER_REJECTED','CHAIRPERSON_REJECTED','BOARD_REJECTED','CREDIT_COMMITTEE_REJECTED','ACCOUNTANT_REJECTED','REJECTED')
 THEN RAISE EXCEPTION 'Cancelled organization facility must retain a closed application'; END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER group_facility_commit AFTER INSERT ON lending_group_facility DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION lending_group_facility_commit();
CREATE CONSTRAINT TRIGGER group_facility_application_commit AFTER INSERT OR UPDATE ON loan_applications DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION lending_group_facility_commit();
CREATE CONSTRAINT TRIGGER group_facility_cycle_commit AFTER UPDATE ON lending_group_cycle DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION lending_group_facility_commit();

CREATE FUNCTION lending_group_facility_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE c lending_group_cycle; h lending_group_authority; p lending_group_policy; a loan_applications;
BEGIN
 SELECT * INTO c FROM lending_group_cycle WHERE id=NEW.cycle_id FOR UPDATE;
 PERFORM id FROM lending_group WHERE id=NEW.group_id FOR UPDATE;
 SELECT * INTO h FROM lending_group_authority WHERE id=NEW.authority_id;
 SELECT * INTO p FROM lending_group_policy WHERE id=c.policy_id;
 SELECT * INTO a FROM loan_applications WHERE id=NEW.loan_id;
 IF c.state<>'DRAFT' OR c.model<>'ORGANIZATION' OR p.capability_version<>1 OR NOT p.enabled
  OR EXISTS(SELECT 1 FROM lending_group_policy n WHERE n.sacco_id=p.sacco_id AND n.station_id=p.station_id AND n.product_id=p.product_id AND n.model=p.model AND n.revision>p.revision)
  OR h.id IS NULL OR h.legal_form<>'ORGANIZATION' OR h.valid_from>NEW.created_at::date OR h.valid_through<NEW.created_at::date
  OR h.recorded_at>NEW.created_at OR h.valid_from>(SELECT application_date FROM accountant_loan_record WHERE loan_id=NEW.loan_id)
  OR h.valid_through<(SELECT application_date FROM accountant_loan_record WHERE loan_id=NEW.loan_id) OR h.recorded_at::date>(SELECT application_date FROM accountant_loan_record WHERE loan_id=NEW.loan_id)
  OR EXISTS(SELECT 1 FROM lending_group_authority n WHERE n.group_id=h.group_id AND n.revision>h.revision)
  OR EXISTS(SELECT 1 FROM lending_group_authority_revocation r WHERE r.authority_id=h.id)
  OR NOT EXISTS(SELECT 1 FROM lending_group_authority_signer s JOIN members m ON m.id=s.client_id WHERE s.authority_id=h.id AND s.client_id=NEW.representative_id AND s.may_apply AND m.sacco_id=NEW.sacco_id AND m.station_id=NEW.station_id AND m.status='ACTIVE' AND m.is_member)
  OR h.signer_count<p.min_members OR h.signer_count>p.max_members
 THEN RAISE EXCEPTION 'Current verified organization authority and policy required'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER group_facility_guard BEFORE INSERT ON lending_group_facility FOR EACH ROW EXECUTE FUNCTION lending_group_facility_guard();

-- Signing and confirmed payout are a separate increment; no route or direct SQL may fund early.
CREATE FUNCTION lending_group_organization_funding_guard() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF NEW.applicant_member_id IS NULL AND NEW.status IN ('DISBURSED','PAR','DEFAULTED','WRITTEN_OFF','PAID','DISBURSEMENT_REVERSED')
 THEN RAISE EXCEPTION 'Organization contract signing and payout are not yet enabled'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER group_organization_funding_guard BEFORE INSERT OR UPDATE ON loan_applications FOR EACH ROW EXECUTE FUNCTION lending_group_organization_funding_guard();

-- Preserve individual-cycle rules verbatim and add the distinct organization aggregate path.
DO $$ DECLARE definition text; marker text=' SELECT count(*) INTO cnt FROM lending_group_participant WHERE cycle_id=c.id;'; BEGIN
 SELECT pg_get_functiondef('lending_group_cycle_commit()'::regprocedure) INTO definition;
 IF strpos(definition,marker)=0 THEN RAISE EXCEPTION 'Group cycle validation marker unavailable'; END IF;
 EXECUTE replace(definition,marker,$replacement$
 IF c.model='ORGANIZATION' THEN
  IF EXISTS(SELECT 1 FROM lending_group_participant WHERE cycle_id=c.id)
   OR (c.state='SUBMITTED' AND NOT EXISTS(SELECT 1 FROM lending_group_facility WHERE cycle_id=c.id))
  THEN RAISE EXCEPTION 'Organization cycle requires one facility and no personal loans'; END IF;
  RETURN NULL;
 END IF;
$replacement$||marker);
END $$;

-- Whole-cycle unfunded withdrawal also closes the real organization application.
DO $$ DECLARE definition text; original text='JOIN lending_group_participant p ON p.loan_id=l.id'; replacement text='JOIN (SELECT cycle_id,loan_id FROM lending_group_participant UNION ALL SELECT cycle_id,loan_id FROM lending_group_facility) p ON p.loan_id=l.id'; BEGIN
 SELECT pg_get_functiondef('lending_group_termination_guard()'::regprocedure) INTO definition;
 IF strpos(definition,original)=0 THEN RAISE EXCEPTION 'Termination lock marker unavailable'; END IF;
 definition=replace(definition,original,replacement);
 definition=replace(definition,'FROM lending_group_participant p JOIN loan_applications l ON l.id=p.loan_id','FROM (SELECT cycle_id,loan_id FROM lending_group_participant UNION ALL SELECT cycle_id,loan_id FROM lending_group_facility) p JOIN loan_applications l ON l.id=p.loan_id');
 EXECUTE definition;
 SELECT pg_get_functiondef('lending_group_termination_commit()'::regprocedure) INTO definition;
 EXECUTE replace(definition,'FROM lending_group_participant p JOIN loan_applications l ON l.id=p.loan_id','FROM (SELECT cycle_id,loan_id FROM lending_group_participant UNION ALL SELECT cycle_id,loan_id FROM lending_group_facility) p JOIN loan_applications l ON l.id=p.loan_id');
 SELECT pg_get_functiondef('lending_group_withdrawn_loan_guard()'::regprocedure) INTO definition;
 EXECUTE replace(definition,'FROM lending_group_participant p JOIN lending_group_termination t ON t.cycle_id=p.cycle_id','FROM (SELECT cycle_id,loan_id FROM lending_group_participant UNION ALL SELECT cycle_id,loan_id FROM lending_group_facility) p JOIN lending_group_termination t ON t.cycle_id=p.cycle_id');
 SELECT pg_get_functiondef('accountant_loan_terms_protect()'::regprocedure) INTO definition;
 EXECUTE replace(definition,'FROM lending_group_participant p JOIN lending_group_termination t ON t.cycle_id=p.cycle_id','FROM (SELECT cycle_id,loan_id FROM lending_group_participant UNION ALL SELECT cycle_id,loan_id FROM lending_group_facility) p JOIN lending_group_termination t ON t.cycle_id=p.cycle_id');
END $$;
