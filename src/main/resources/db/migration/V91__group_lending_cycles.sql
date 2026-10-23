-- Group product rules and frozen cycles. No policy is enabled or approved by this migration.
CREATE TABLE lending_group_policy (
 id uuid PRIMARY KEY,sacco_id varchar(255) NOT NULL,station_id varchar(255) NOT NULL,
 product_id uuid NOT NULL REFERENCES loan_product_settings(id),
 model varchar(16) NOT NULL CHECK(model IN ('INDIVIDUAL','SHARED','ORGANIZATION')),
 min_members integer NOT NULL CHECK(min_members BETWEEN 2 AND 50),
 max_members integer NOT NULL CHECK(max_members BETWEEN min_members AND 50),
 max_principal numeric(18,2) NOT NULL CHECK(max_principal>0),
 max_guarantee numeric(18,2) NOT NULL CHECK(max_guarantee>0),
 max_active_cycles integer NOT NULL CHECK(max_active_cycles BETWEEN 1 AND 10),
 own_loan_guarantors boolean NOT NULL,
 policy_reference varchar(500) NOT NULL CHECK(length(trim(policy_reference))>0),
 agreement_reference varchar(500) NOT NULL CHECK(length(trim(agreement_reference))>0),
 enabled boolean NOT NULL,request_key uuid NOT NULL,payload_hash varchar(64) NOT NULL,
 created_by uuid NOT NULL REFERENCES members(id),created_at timestamptz NOT NULL,
 UNIQUE(id,sacco_id,station_id),UNIQUE(sacco_id,station_id,request_key),
 FOREIGN KEY(sacco_id,station_id) REFERENCES sacco_stations(sacco_id,station_id)
);
CREATE INDEX ix_lending_group_policy ON lending_group_policy(sacco_id,station_id,product_id,model,created_at DESC,id DESC);
CREATE TRIGGER lending_group_policy_immutable BEFORE UPDATE OR DELETE ON lending_group_policy FOR EACH ROW EXECUTE FUNCTION gl_protect_evidence();

CREATE TABLE lending_group_cycle (
 id uuid PRIMARY KEY,group_id uuid NOT NULL,sacco_id varchar(255) NOT NULL,station_id varchar(255) NOT NULL,
 policy_id uuid NOT NULL,product_id uuid NOT NULL REFERENCES loan_product_settings(id),
 model varchar(16) NOT NULL CHECK(model IN ('INDIVIDUAL','SHARED','ORGANIZATION')),
 state varchar(16) NOT NULL DEFAULT 'DRAFT' CHECK(state IN ('DRAFT','SUBMITTED','CANCELLED')),
 version integer NOT NULL DEFAULT 0,form_data jsonb NOT NULL,submitted_at timestamptz,
 request_key uuid NOT NULL,payload_hash varchar(64) NOT NULL,created_by uuid NOT NULL REFERENCES members(id),created_at timestamptz NOT NULL,
 UNIQUE(id,sacco_id,station_id),UNIQUE(sacco_id,station_id,request_key),
 FOREIGN KEY(group_id,sacco_id,station_id) REFERENCES lending_group(id,sacco_id,station_id),
 FOREIGN KEY(policy_id,sacco_id,station_id) REFERENCES lending_group_policy(id,sacco_id,station_id)
);
CREATE INDEX ix_lending_group_cycles ON lending_group_cycle(sacco_id,station_id,group_id,created_at DESC,id DESC);
CREATE TABLE lending_group_participant (
 id uuid PRIMARY KEY,cycle_id uuid NOT NULL,sacco_id varchar(255) NOT NULL,station_id varchar(255) NOT NULL,
 client_id uuid NOT NULL REFERENCES members(id),membership_id uuid NOT NULL,
 principal numeric(18,2) NOT NULL CHECK(principal>0),
 income numeric(18,2) NOT NULL CHECK(income>0),expenses numeric(18,2) NOT NULL CHECK(expenses>=0),other_debt numeric(18,2) NOT NULL CHECK(other_debt>=0),
 monthly_payment numeric(18,2) NOT NULL CHECK(monthly_payment>0),
 guarantee_capacity numeric(18,2) NOT NULL CHECK(guarantee_capacity>=0),
 assessment_evidence varchar(500) NOT NULL CHECK(length(trim(assessment_evidence))>0),
 capacity_evidence varchar(500) NOT NULL CHECK(length(trim(capacity_evidence))>0),
 loan_id uuid UNIQUE REFERENCES loan_applications(id),
 UNIQUE(cycle_id,client_id),UNIQUE(id,sacco_id,station_id),
 FOREIGN KEY(cycle_id,sacco_id,station_id) REFERENCES lending_group_cycle(id,sacco_id,station_id),
 FOREIGN KEY(membership_id,sacco_id,station_id) REFERENCES lending_group_member(id,sacco_id,station_id)
);
CREATE INDEX ix_lending_group_participant_client ON lending_group_participant(sacco_id,station_id,client_id,cycle_id);
CREATE TRIGGER lending_group_participant_immutable BEFORE UPDATE OR DELETE ON lending_group_participant FOR EACH ROW EXECUTE FUNCTION gl_protect_evidence();

CREATE TABLE lending_group_guarantee (
 id uuid PRIMARY KEY,cycle_id uuid NOT NULL,sacco_id varchar(255) NOT NULL,station_id varchar(255) NOT NULL,
 guarantor_id uuid NOT NULL REFERENCES members(id),borrower_id uuid NOT NULL REFERENCES members(id),loan_id uuid NOT NULL REFERENCES loan_applications(id),
 amount_cap numeric(18,2) NOT NULL CHECK(amount_cap>0),terms_reference varchar(500) NOT NULL,
 created_at timestamptz NOT NULL,
 CHECK(guarantor_id<>borrower_id),UNIQUE(cycle_id,guarantor_id,loan_id),UNIQUE(id,sacco_id,station_id),
 FOREIGN KEY(cycle_id,sacco_id,station_id) REFERENCES lending_group_cycle(id,sacco_id,station_id)
);
CREATE INDEX ix_lending_group_guarantor ON lending_group_guarantee(sacco_id,station_id,guarantor_id,cycle_id);
CREATE INDEX ix_lending_group_guarantee_loan ON lending_group_guarantee(loan_id,cycle_id);
CREATE TRIGGER lending_group_guarantee_immutable BEFORE UPDATE OR DELETE ON lending_group_guarantee FOR EACH ROW EXECUTE FUNCTION gl_protect_evidence();
CREATE TABLE lending_group_consent (
 cycle_id uuid NOT NULL,sacco_id varchar(255) NOT NULL,station_id varchar(255) NOT NULL,
 client_id uuid NOT NULL REFERENCES members(id),accepted_at timestamptz NOT NULL,
 PRIMARY KEY(cycle_id,client_id),
 FOREIGN KEY(cycle_id,sacco_id,station_id) REFERENCES lending_group_cycle(id,sacco_id,station_id)
);
CREATE TRIGGER lending_group_consent_immutable BEFORE UPDATE OR DELETE ON lending_group_consent FOR EACH ROW EXECUTE FUNCTION gl_protect_evidence();

CREATE FUNCTION lending_group_cycle_protect() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF TG_OP='DELETE' OR OLD.state<>'DRAFT' OR NEW.version<>OLD.version+1
 OR (to_jsonb(NEW)-ARRAY['state','version','form_data','submitted_at']) IS DISTINCT FROM
    (to_jsonb(OLD)-ARRAY['state','version','form_data','submitted_at'])
 OR (NEW.state='DRAFT' AND NEW.submitted_at IS NOT NULL)
 OR (NEW.state='SUBMITTED' AND NEW.submitted_at IS NULL)
 THEN RAISE EXCEPTION 'Submitted group cycles are immutable'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER lending_group_cycle_protect BEFORE UPDATE OR DELETE ON lending_group_cycle FOR EACH ROW EXECUTE FUNCTION lending_group_cycle_protect();

CREATE FUNCTION lending_group_cycle_scope() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE c lending_group_cycle; p lending_group_policy;
BEGIN
 IF TG_TABLE_NAME='lending_group_policy' THEN
  IF NOT EXISTS(SELECT 1 FROM loan_product_settings l WHERE l.id=NEW.product_id AND l.sacco_id=NEW.sacco_id)
   OR NOT EXISTS(SELECT 1 FROM members m WHERE m.id=NEW.created_by AND m.sacco_id=NEW.sacco_id AND m.station_id=NEW.station_id)
  THEN RAISE EXCEPTION 'Group product scope must agree'; END IF;
 ELSIF TG_TABLE_NAME='lending_group_cycle' THEN
  SELECT * INTO p FROM lending_group_policy WHERE id=NEW.policy_id;
  IF p.sacco_id<>NEW.sacco_id OR p.station_id<>NEW.station_id OR p.product_id<>NEW.product_id OR p.model<>NEW.model OR NOT p.enabled
   OR NOT EXISTS(SELECT 1 FROM members m WHERE m.id=NEW.created_by AND m.sacco_id=NEW.sacco_id AND m.station_id=NEW.station_id)
  THEN RAISE EXCEPTION 'Group cycle scope must agree'; END IF;
 ELSE
  SELECT * INTO c FROM lending_group_cycle WHERE id=NEW.cycle_id;
  IF c.sacco_id<>NEW.sacco_id OR c.station_id<>NEW.station_id THEN RAISE EXCEPTION 'Group cycle scope must agree'; END IF;
  IF TG_TABLE_NAME='lending_group_participant' THEN
   IF c.state<>'DRAFT' OR NOT EXISTS(SELECT 1 FROM lending_group_member m WHERE m.id=NEW.membership_id
     AND m.group_id=c.group_id AND m.client_id=NEW.client_id AND m.sacco_id=c.sacco_id AND m.station_id=c.station_id
     AND m.ends_on IS NULL) OR (NEW.loan_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM loan_applications a
     WHERE a.id=NEW.loan_id AND a.sacco_id=c.sacco_id AND a.station_id=c.station_id AND a.applicant_member_id=NEW.client_id AND a.amount=NEW.principal))
   THEN RAISE EXCEPTION 'Group participant scope must agree'; END IF;
  ELSIF TG_TABLE_NAME='lending_group_guarantee' THEN
   IF c.state<>'DRAFT' OR NOT EXISTS(SELECT 1 FROM lending_group_participant b JOIN lending_group_participant g ON g.cycle_id=b.cycle_id
     WHERE b.cycle_id=c.id AND b.loan_id=NEW.loan_id AND b.client_id=NEW.borrower_id AND g.client_id=NEW.guarantor_id)
   THEN RAISE EXCEPTION 'Group guarantee scope must agree'; END IF;
  ELSIF TG_TABLE_NAME='lending_group_consent' THEN
   IF c.state<>'SUBMITTED' OR NOT EXISTS(SELECT 1 FROM lending_group_participant x WHERE x.cycle_id=c.id AND x.client_id=NEW.client_id)
   THEN RAISE EXCEPTION 'Only a frozen participant may accept group terms'; END IF;
  END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER lending_group_policy_scope BEFORE INSERT ON lending_group_policy FOR EACH ROW EXECUTE FUNCTION lending_group_cycle_scope();
CREATE TRIGGER lending_group_cycle_scope BEFORE INSERT ON lending_group_cycle FOR EACH ROW EXECUTE FUNCTION lending_group_cycle_scope();
CREATE TRIGGER lending_group_participant_scope BEFORE INSERT ON lending_group_participant FOR EACH ROW EXECUTE FUNCTION lending_group_cycle_scope();
CREATE TRIGGER lending_group_guarantee_scope BEFORE INSERT ON lending_group_guarantee FOR EACH ROW EXECUTE FUNCTION lending_group_cycle_scope();
CREATE TRIGGER lending_group_consent_scope BEFORE INSERT ON lending_group_consent FOR EACH ROW EXECUTE FUNCTION lending_group_cycle_scope();

CREATE FUNCTION lending_group_cycle_commit() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE c lending_group_cycle;p lending_group_policy;cnt integer;
BEGIN
 IF TG_TABLE_NAME='lending_group_cycle' THEN SELECT * INTO c FROM lending_group_cycle WHERE id=NEW.id;
 ELSE SELECT * INTO c FROM lending_group_cycle WHERE id=NEW.cycle_id; END IF;
 SELECT * INTO p FROM lending_group_policy WHERE id=c.policy_id;
 SELECT count(*) INTO cnt FROM lending_group_participant WHERE cycle_id=c.id;
 IF cnt>0 AND c.state<>'SUBMITTED' THEN RAISE EXCEPTION 'Frozen participants require submitted cycle'; END IF;
 IF c.state='SUBMITTED' THEN
  IF cnt<p.min_members OR cnt>p.max_members OR
    (SELECT sum(principal) FROM lending_group_participant WHERE cycle_id=c.id)>p.max_principal
  THEN RAISE EXCEPTION 'Cycle participants must fit policy'; END IF;
  IF c.model='INDIVIDUAL' AND (EXISTS(SELECT 1 FROM lending_group_participant WHERE cycle_id=c.id AND loan_id IS NULL)
     OR (SELECT count(*) FROM lending_group_guarantee WHERE cycle_id=c.id)<>cnt*(cnt-1))
  THEN RAISE EXCEPTION 'Individual group loans require complete reciprocal guarantees'; END IF;
  IF EXISTS(SELECT 1 FROM lending_group_participant x WHERE x.cycle_id=c.id AND
    (SELECT coalesce(sum(g.amount_cap),0) FROM lending_group_guarantee g JOIN loan_applications l ON l.id=g.loan_id
     WHERE g.sacco_id=c.sacco_id AND g.station_id=c.station_id AND g.guarantor_id=x.client_id AND l.status NOT IN
     ('PAID','DISBURSEMENT_REVERSED','MANAGER_REJECTED','LOAN_OFFICER_REJECTED','CHAIRPERSON_REJECTED','BOARD_REJECTED','CREDIT_COMMITTEE_REJECTED','ACCOUNTANT_REJECTED','REJECTED'))>least(x.guarantee_capacity,p.max_guarantee))
  THEN RAISE EXCEPTION 'Group guarantee capacity exceeded'; END IF;
 END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER lending_group_cycle_commit AFTER INSERT OR UPDATE ON lending_group_cycle DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION lending_group_cycle_commit();
CREATE CONSTRAINT TRIGGER lending_group_participant_commit AFTER INSERT ON lending_group_participant DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION lending_group_cycle_commit();
CREATE CONSTRAINT TRIGGER lending_group_guarantee_commit AFTER INSERT ON lending_group_guarantee DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION lending_group_cycle_commit();

-- Every funding route is covered, even an older staff form or direct SQL mutation.
CREATE FUNCTION lending_group_funding_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE c lending_group_cycle;
BEGIN
 IF NEW.status='DISBURSED' AND OLD.status IS DISTINCT FROM NEW.status THEN
  SELECT g.* INTO c FROM lending_group_cycle g JOIN lending_group_participant x ON x.cycle_id=g.id WHERE x.loan_id=NEW.id;
  IF FOUND AND (NEW.first_repayment_date IS DISTINCT FROM OLD.first_repayment_date OR c.state<>'SUBMITTED' OR EXISTS(SELECT 1 FROM lending_group_participant x WHERE x.cycle_id=c.id AND NOT EXISTS(
     SELECT 1 FROM lending_group_consent consent WHERE consent.cycle_id=c.id AND consent.client_id=x.client_id)))
  THEN RAISE EXCEPTION 'All group participants must accept frozen liabilities before funding'; END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER lending_group_funding_guard BEFORE UPDATE ON loan_applications FOR EACH ROW EXECUTE FUNCTION lending_group_funding_guard();
