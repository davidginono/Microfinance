-- Shared allocations describe one debt. They are neither individual loans nor guarantees.
ALTER TABLE lending_group_policy DROP CONSTRAINT lending_group_policy_capability_version_check;
ALTER TABLE lending_group_policy ADD CONSTRAINT lending_group_policy_capability_version_check CHECK(capability_version IN (0,1,2));
ALTER TABLE lending_group_policy ADD COLUMN shared_liability varchar(24) CHECK(shared_liability IN ('PROPORTIONAL','JOINT_AND_SEVERAL'));
ALTER TABLE lending_group_policy ADD CONSTRAINT shared_policy_capability CHECK((capability_version=2)=(shared_liability IS NOT NULL) AND (capability_version<>2 OR model='SHARED'));
CREATE TABLE lending_group_shared_facility (
 cycle_id uuid PRIMARY KEY,loan_id uuid NOT NULL UNIQUE,sacco_id varchar(255) NOT NULL,station_id varchar(255) NOT NULL,
 liability_rule varchar(24) NOT NULL CHECK(liability_rule IN ('PROPORTIONAL','JOINT_AND_SEVERAL')),
 allocation_version varchar(32) NOT NULL DEFAULT 'REMAINING_COMPONENT_V1' CHECK(allocation_version='REMAINING_COMPONENT_V1'),
 snapshot jsonb NOT NULL,created_by uuid NOT NULL REFERENCES members(id),created_at timestamptz NOT NULL,
 FOREIGN KEY(cycle_id,sacco_id,station_id) REFERENCES lending_group_cycle(id,sacco_id,station_id),
 FOREIGN KEY(loan_id,sacco_id,station_id) REFERENCES loan_applications(id,sacco_id,station_id)
);
CREATE TABLE lending_group_shared_allocation (
 cycle_id uuid NOT NULL REFERENCES lending_group_shared_facility(cycle_id),participant_id uuid PRIMARY KEY REFERENCES lending_group_participant(id),
 client_id uuid NOT NULL REFERENCES members(id),ordinal integer NOT NULL CHECK(ordinal BETWEEN 1 AND 50),
 client_name varchar(255) NOT NULL,client_number varchar(255) NOT NULL,
 principal numeric(18,2) NOT NULL CHECK(principal>0),interest numeric(18,2) NOT NULL CHECK(interest>=0),
 charges numeric(18,2) NOT NULL CHECK(charges>=0),deductions numeric(18,2) NOT NULL CHECK(deductions>=0 AND deductions<=charges),
 net numeric(18,2) NOT NULL CHECK(net>0 AND net=principal-deductions),monthly_payment numeric(18,2) NOT NULL CHECK(monthly_payment>0),
 liability_cap numeric(18,2) NOT NULL CHECK(liability_cap>0),UNIQUE(cycle_id,client_id),UNIQUE(cycle_id,ordinal)
);
CREATE INDEX ix_shared_borrower_scope ON lending_group_shared_facility(sacco_id,station_id,cycle_id,loan_id);
CREATE INDEX ix_shared_obligor ON lending_group_shared_allocation(client_id,cycle_id);
CREATE TRIGGER shared_facility_immutable BEFORE UPDATE OR DELETE ON lending_group_shared_facility FOR EACH ROW EXECUTE FUNCTION gl_protect_evidence();
CREATE TRIGGER shared_allocation_immutable BEFORE UPDATE OR DELETE ON lending_group_shared_allocation FOR EACH ROW EXECUTE FUNCTION gl_protect_evidence();

CREATE FUNCTION lending_group_shared_insert() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE c lending_group_cycle;p lending_group_policy;f lending_group_shared_facility;
BEGIN
 SELECT * INTO c FROM lending_group_cycle WHERE id=NEW.cycle_id FOR UPDATE;
 SELECT * INTO p FROM lending_group_policy WHERE id=c.policy_id;
 IF c.state<>'DRAFT' OR c.model<>'SHARED' THEN RAISE EXCEPTION 'Only a draft shared cycle can freeze obligations'; END IF;
 IF TG_TABLE_NAME='lending_group_shared_facility' THEN
  IF ROW(NEW.sacco_id,NEW.station_id,NEW.liability_rule) IS DISTINCT FROM ROW(c.sacco_id,c.station_id,p.shared_liability)
   OR p.capability_version<>2 OR NOT p.enabled
   OR EXISTS(SELECT 1 FROM lending_group_policy n WHERE n.sacco_id=p.sacco_id AND n.station_id=p.station_id AND n.product_id=p.product_id AND n.model=p.model AND n.revision>p.revision)
   OR NOT EXISTS(SELECT 1 FROM lending_group g WHERE g.id=c.group_id AND g.status='ACTIVE')
   OR NOT EXISTS(SELECT 1 FROM members m WHERE m.id=NEW.created_by AND m.sacco_id=c.sacco_id AND m.station_id=c.station_id AND m.status='ACTIVE')
  THEN RAISE EXCEPTION 'Current scoped shared borrower policy required'; END IF;
 ELSE
  IF NOT EXISTS(SELECT 1 FROM lending_group_participant x JOIN members m ON m.id=x.client_id
    WHERE x.id=NEW.participant_id AND x.cycle_id=c.id AND x.client_id=NEW.client_id AND x.loan_id IS NULL
      AND x.sacco_id=c.sacco_id AND x.station_id=c.station_id AND x.principal=NEW.principal AND x.monthly_payment=NEW.monthly_payment
      AND m.sacco_id=c.sacco_id AND m.station_id=c.station_id AND m.status='ACTIVE' AND m.is_member
      AND m.full_name=NEW.client_name AND m.member_no=NEW.client_number)
  THEN RAISE EXCEPTION 'Shared allocation must identify its scoped joint borrower'; END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER shared_facility_insert BEFORE INSERT ON lending_group_shared_facility FOR EACH ROW EXECUTE FUNCTION lending_group_shared_insert();
CREATE TRIGGER shared_allocation_insert BEFORE INSERT ON lending_group_shared_allocation FOR EACH ROW EXECUTE FUNCTION lending_group_shared_insert();

CREATE FUNCTION lending_group_shared_validate(cycle uuid) RETURNS void LANGUAGE plpgsql AS $$
DECLARE c lending_group_cycle;p lending_group_policy;f lending_group_shared_facility;a loan_applications;cnt integer;periods integer;
BEGIN
 SELECT * INTO c FROM lending_group_cycle WHERE id=cycle;
 SELECT * INTO f FROM lending_group_shared_facility WHERE cycle_id=cycle;
 IF f.cycle_id IS NULL THEN
  IF c.state='SUBMITTED' THEN RAISE EXCEPTION 'Submitted shared cycle requires one facility'; END IF;
  RETURN;
 END IF;
 SELECT * INTO p FROM lending_group_policy WHERE id=c.policy_id;
 SELECT * INTO a FROM loan_applications WHERE id=f.loan_id;
 SELECT count(*) INTO cnt FROM lending_group_shared_allocation WHERE cycle_id=cycle;
 IF c.model<>'SHARED' OR c.state NOT IN ('SUBMITTED','CANCELLED') OR p.capability_version<>2 OR p.shared_liability<>f.liability_rule
  OR ROW(a.sacco_id,a.station_id,a.loan_product_setting_id) IS DISTINCT FROM ROW(c.sacco_id,c.station_id,c.product_id)
  OR ROW(f.sacco_id,f.station_id) IS DISTINCT FROM ROW(c.sacco_id,c.station_id)
  OR a.applicant_member_id IS NOT NULL OR a.required_guarantors IS DISTINCT FROM 0 OR a.top_up_source_loan_id IS NOT NULL
  OR NOT EXISTS(SELECT 1 FROM loan_borrower_party b WHERE b.id=a.borrower_party_id AND b.sacco_id=c.sacco_id AND b.group_station_id=c.station_id AND b.group_id=c.group_id AND b.kind='JOINT_GROUP')
  OR NOT EXISTS(SELECT 1 FROM accountant_loan_record r WHERE r.loan_id=a.id AND r.sacco_id=c.sacco_id AND r.station_id=c.station_id)
  OR cnt<p.min_members OR cnt>p.max_members OR cnt<>(SELECT count(*) FROM lending_group_participant WHERE cycle_id=cycle)
  OR a.amount>p.max_principal OR a.amount IS DISTINCT FROM (f.snapshot->>'principal')::numeric
  OR a.financial_snapshot->>'loanMode' IS DISTINCT FROM 'CONFIGURED'
  OR (a.financial_snapshot->>'interestAmount')::numeric IS DISTINCT FROM (f.snapshot->>'interest')::numeric
  OR (a.financial_snapshot->>'recordedChargeTotal')::numeric IS DISTINCT FROM (f.snapshot->>'charges')::numeric
  OR (a.financial_snapshot->>'recordedChargeDeductions')::numeric IS DISTINCT FROM (f.snapshot->>'deductions')::numeric
  OR a.first_repayment_date IS DISTINCT FROM (c.form_data->>'firstPaymentDate')::date
  OR EXISTS(SELECT 1 FROM lending_group_guarantee WHERE cycle_id=cycle)
 THEN RAISE EXCEPTION 'Shared facility must agree with its one parent loan and frozen obligors'; END IF;
 IF (SELECT ROW(sum(principal),sum(interest),sum(charges),sum(deductions),sum(net)) FROM lending_group_shared_allocation WHERE cycle_id=cycle)
  IS DISTINCT FROM ROW(a.amount,(f.snapshot->>'interest')::numeric,(f.snapshot->>'charges')::numeric,(f.snapshot->>'deductions')::numeric,(f.snapshot->>'net')::numeric)
  OR EXISTS(SELECT 1 FROM lending_group_shared_allocation s JOIN lending_group_participant x ON x.id=s.participant_id
    WHERE s.cycle_id=cycle AND (x.cycle_id<>cycle OR x.client_id<>s.client_id OR x.loan_id IS NOT NULL OR x.principal<>s.principal
      OR s.liability_cap<>CASE WHEN f.liability_rule='JOINT_AND_SEVERAL' THEN (f.snapshot->>'totalPayable')::numeric ELSE s.principal+s.interest+s.charges END))
 THEN RAISE EXCEPTION 'Shared allocation components and obligations must conserve the parent'; END IF;
 periods=jsonb_array_length(f.snapshot->'schedule');
 IF periods IS NULL OR periods<1 OR periods>2600 OR periods IS DISTINCT FROM (a.financial_snapshot->>'numberOfPayments')::integer
  OR jsonb_array_length(f.snapshot->'dueShares') IS DISTINCT FROM cnt*periods
  OR (SELECT count(DISTINCT (d->>'clientId',d->>'number')) FROM jsonb_array_elements(f.snapshot->'dueShares') d)<>cnt*periods
  OR (SELECT count(DISTINCT d->>'number') FROM jsonb_array_elements(f.snapshot->'schedule') d)<>periods
  OR EXISTS(SELECT 1 FROM jsonb_array_elements(f.snapshot->'dueShares') d
    WHERE d->>'principal' IS NULL OR d->>'interest' IS NULL OR d->>'date' IS NULL OR (d->>'number')::integer NOT BETWEEN 1 AND periods
      OR (d->>'principal')::numeric<0 OR (d->>'interest')::numeric<0
      OR NOT EXISTS(SELECT 1 FROM lending_group_shared_allocation s WHERE s.cycle_id=cycle AND s.client_id=(d->>'clientId')::uuid))
  OR EXISTS(SELECT 1 FROM jsonb_array_elements(f.snapshot->'schedule') due
    LEFT JOIN (SELECT d->>'number' number,sum((d->>'principal')::numeric) principal,sum((d->>'interest')::numeric) interest,count(*) people,
      min(d->>'date') first_date,max(d->>'date') last_date FROM jsonb_array_elements(f.snapshot->'dueShares') d GROUP BY d->>'number') actual ON actual.number=due->>'number'
    WHERE ROW(actual.principal,actual.interest,actual.people,actual.first_date,actual.last_date) IS DISTINCT FROM ROW((due->>'principal')::numeric,(due->>'interest')::numeric,cnt::bigint,due->>'date',due->>'date'))
  OR EXISTS(SELECT 1 FROM lending_group_shared_allocation s LEFT JOIN (
    SELECT d->>'clientId' client_id,sum((d->>'principal')::numeric) principal,sum((d->>'interest')::numeric) interest FROM jsonb_array_elements(f.snapshot->'dueShares') d GROUP BY d->>'clientId') actual ON actual.client_id=s.client_id::text
    WHERE s.cycle_id=cycle AND ROW(s.principal,s.interest) IS DISTINCT FROM ROW(actual.principal,actual.interest))
 THEN RAISE EXCEPTION 'Every shared due component must conserve its parent and personal totals'; END IF;
 IF c.state='CANCELLED' AND a.status NOT IN ('WITHDRAWN','MANAGER_REJECTED','LOAN_OFFICER_REJECTED','CHAIRPERSON_REJECTED','BOARD_REJECTED','CREDIT_COMMITTEE_REJECTED','ACCOUNTANT_REJECTED','REJECTED')
 THEN RAISE EXCEPTION 'Cancelled shared cycle requires a closed application'; END IF;
END $$;
CREATE FUNCTION lending_group_shared_commit() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE cycle uuid;
BEGIN
 IF TG_TABLE_NAME='loan_applications' THEN
  SELECT cycle_id INTO cycle FROM lending_group_shared_facility WHERE loan_id=NEW.id;
  IF cycle IS NULL AND EXISTS(SELECT 1 FROM loan_borrower_party WHERE id=NEW.borrower_party_id AND kind='JOINT_GROUP') THEN RAISE EXCEPTION 'Joint borrower application requires its shared source'; END IF;
 ELSE cycle=NEW.cycle_id; END IF;
 IF cycle IS NOT NULL THEN PERFORM lending_group_shared_validate(cycle); END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER shared_facility_commit AFTER INSERT ON lending_group_shared_facility DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION lending_group_shared_commit();
CREATE CONSTRAINT TRIGGER shared_application_commit AFTER INSERT OR UPDATE ON loan_applications DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION lending_group_shared_commit();

DO $$ DECLARE definition text;marker text=' RAISE EXCEPTION ''Unsupported loan borrower or origination path'';'; BEGIN
 SELECT pg_get_functiondef('loan_borrower_identity_guard()'::regprocedure) INTO definition;
 IF strpos(definition,marker)=0 THEN RAISE EXCEPTION 'Borrower identity marker unavailable'; END IF;
 EXECUTE replace(definition,marker,$insert$
 IF party.kind='JOINT_GROUP' AND NEW.applicant_member_id IS NULL AND party.group_station_id=NEW.station_id
   AND NEW.status='RECORDED' AND NEW.top_up_source_loan_id IS NULL
   AND EXISTS(SELECT 1 FROM lending_group g WHERE g.id=party.group_id AND g.sacco_id=party.sacco_id AND g.station_id=NEW.station_id AND g.status='ACTIVE') THEN RETURN NEW; END IF;
$insert$||marker);
 SELECT pg_get_functiondef('lending_group_cycle_commit()'::regprocedure) INTO definition;
 marker=' SELECT count(*) INTO cnt FROM lending_group_participant WHERE cycle_id=c.id;';
 IF strpos(definition,marker)=0 THEN RAISE EXCEPTION 'Cycle commitment marker unavailable'; END IF;
 EXECUTE replace(definition,marker,' IF c.model=''SHARED'' THEN IF TG_TABLE_NAME=''lending_group_cycle'' THEN PERFORM lending_group_shared_validate(c.id); END IF; RETURN NULL; END IF;'||marker);
END $$;

-- Include the one shared parent in the existing all-or-none unfunded withdrawal protocol.
DO $$ DECLARE definition text;name text;marker text='UNION ALL SELECT cycle_id,loan_id FROM lending_group_facility'; BEGIN
 FOREACH name IN ARRAY ARRAY['lending_group_termination_guard','lending_group_termination_commit','lending_group_withdrawn_loan_guard','accountant_loan_terms_protect'] LOOP
  SELECT pg_get_functiondef((name||'()')::regprocedure) INTO definition;
  IF strpos(definition,marker)=0 THEN RAISE EXCEPTION 'Shared withdrawal marker unavailable in %',name; END IF;
  EXECUTE replace(definition,marker,marker||' UNION ALL SELECT cycle_id,loan_id FROM lending_group_shared_facility');
 END LOOP;
END $$;
-- Existing funding/ledger guards keep joint facilities closed until signed payout and servicing are installed.
