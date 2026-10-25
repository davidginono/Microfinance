-- Ending an unfunded cycle preserves contracts, signatures and reviewer decisions.
DO $$ DECLARE definition text; BEGIN
 SELECT pg_get_constraintdef(oid) INTO definition FROM pg_constraint WHERE conrelid='loan_applications'::regclass AND conname='loan_applications_status_check';
 ALTER TABLE loan_applications DROP CONSTRAINT loan_applications_status_check;
 EXECUTE 'ALTER TABLE loan_applications ADD CONSTRAINT loan_applications_status_check CHECK (status=''WITHDRAWN'' OR ('||regexp_replace(definition,'^CHECK \((.*)\)$','\1')||'))';
END $$;
CREATE TABLE lending_group_termination (
 cycle_id uuid PRIMARY KEY,sacco_id varchar(255) NOT NULL,station_id varchar(255) NOT NULL,
 kind varchar(16) NOT NULL CHECK(kind IN ('WITHDRAWN','REFUSED')),
 actor_id uuid NOT NULL REFERENCES members(id),reason varchar(500) NOT NULL CHECK(length(trim(reason)) BETWEEN 1 AND 500),
 recorded_at timestamptz NOT NULL,
 FOREIGN KEY(cycle_id,sacco_id,station_id) REFERENCES lending_group_cycle(id,sacco_id,station_id)
);
CREATE TRIGGER group_termination_immutable BEFORE UPDATE OR DELETE ON lending_group_termination FOR EACH ROW EXECUTE FUNCTION gl_protect_evidence();
CREATE FUNCTION lending_group_termination_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE c lending_group_cycle;
BEGIN
 SELECT * INTO c FROM lending_group_cycle WHERE id=NEW.cycle_id FOR UPDATE;
 IF c.state NOT IN ('DRAFT','SUBMITTED') OR c.sacco_id<>NEW.sacco_id OR c.station_id<>NEW.station_id
  OR NOT EXISTS(SELECT 1 FROM members m WHERE m.id=NEW.actor_id AND m.sacco_id=c.sacco_id AND m.station_id=c.station_id AND m.status='ACTIVE')
 THEN RAISE EXCEPTION 'Only a scoped unfunded cycle may end'; END IF;
 -- The same rows are locked by funding; either funding or termination wins, never both.
 PERFORM l.id FROM loan_applications l JOIN lending_group_participant p ON p.loan_id=l.id WHERE p.cycle_id=c.id ORDER BY l.id FOR UPDATE OF l;
 IF EXISTS(SELECT 1 FROM lending_group_participant p JOIN loan_applications l ON l.id=p.loan_id WHERE p.cycle_id=c.id
   AND (l.status IN ('DISBURSED','PAR','DEFAULTED','WRITTEN_OFF','PAID','DISBURSEMENT_REVERSED')
     OR EXISTS(SELECT 1 FROM loan_ledgers ledger WHERE ledger.loan_application_id=l.id)
     OR EXISTS(SELECT 1 FROM accountant_loan_post posting WHERE posting.loan_id=l.id)))
 THEN RAISE EXCEPTION 'A funded cycle cannot be withdrawn'; END IF;
 IF NEW.kind='REFUSED' AND (c.state<>'SUBMITTED' OR NOT EXISTS(SELECT 1 FROM lending_group_participant p WHERE p.cycle_id=c.id AND p.client_id=NEW.actor_id)
   OR EXISTS(SELECT 1 FROM lending_group_consent_evidence e WHERE e.cycle_id=c.id AND e.client_id=NEW.actor_id))
 THEN RAISE EXCEPTION 'Only unsigned participants may refuse'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER group_termination_guard BEFORE INSERT ON lending_group_termination FOR EACH ROW EXECUTE FUNCTION lending_group_termination_guard();
CREATE OR REPLACE FUNCTION lending_group_cycle_protect() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF TG_OP='DELETE' OR OLD.state='CANCELLED' OR NEW.version<>OLD.version+1
 OR (to_jsonb(NEW)-ARRAY['state','version','form_data','submitted_at']) IS DISTINCT FROM (to_jsonb(OLD)-ARRAY['state','version','form_data','submitted_at'])
 OR (OLD.state='SUBMITTED' AND (NEW.state<>'CANCELLED' OR NEW.form_data IS DISTINCT FROM OLD.form_data OR NEW.submitted_at IS DISTINCT FROM OLD.submitted_at))
 OR (NEW.state='CANCELLED' AND (NEW.form_data IS DISTINCT FROM OLD.form_data OR NEW.submitted_at IS DISTINCT FROM OLD.submitted_at))
 OR (NEW.state='DRAFT' AND NEW.submitted_at IS NOT NULL) OR (NEW.state='SUBMITTED' AND NEW.submitted_at IS NULL)
 THEN RAISE EXCEPTION 'Frozen group contracts are immutable'; END IF;
 RETURN NEW;
END $$;
CREATE FUNCTION lending_group_termination_commit() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE cycle uuid; c lending_group_cycle;
BEGIN
 IF TG_TABLE_NAME='lending_group_cycle' THEN cycle=NEW.id; ELSE cycle=NEW.cycle_id; END IF;
 SELECT * INTO c FROM lending_group_cycle WHERE id=cycle;
 IF (c.state='CANCELLED') IS DISTINCT FROM EXISTS(SELECT 1 FROM lending_group_termination t WHERE t.cycle_id=cycle)
 OR (c.state='CANCELLED' AND EXISTS(SELECT 1 FROM lending_group_participant p JOIN loan_applications l ON l.id=p.loan_id WHERE p.cycle_id=cycle
   AND l.status NOT IN ('WITHDRAWN','MANAGER_REJECTED','LOAN_OFFICER_REJECTED','CHAIRPERSON_REJECTED','BOARD_REJECTED','CREDIT_COMMITTEE_REJECTED','ACCOUNTANT_REJECTED','REJECTED')))
 THEN RAISE EXCEPTION 'Termination and unfunded loan withdrawal must commit together'; END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER group_termination_commit AFTER INSERT ON lending_group_termination DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION lending_group_termination_commit();
CREATE CONSTRAINT TRIGGER group_terminated_cycle_commit AFTER UPDATE ON lending_group_cycle DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION lending_group_termination_commit();
CREATE FUNCTION lending_group_withdrawn_loan_guard() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF TG_OP='INSERT' AND NEW.status='WITHDRAWN' THEN RAISE EXCEPTION 'Withdrawn loans retain their original application'; END IF;
 IF TG_OP='UPDATE' AND (OLD.status='WITHDRAWN' OR NEW.status='WITHDRAWN' OR EXISTS(SELECT 1 FROM lending_group_participant p JOIN lending_group_termination t ON t.cycle_id=p.cycle_id WHERE p.loan_id=NEW.id)) THEN
  IF (NEW.status IS DISTINCT FROM OLD.status AND NEW.status<>'WITHDRAWN')
   OR NOT EXISTS(SELECT 1 FROM lending_group_participant p JOIN lending_group_termination t ON t.cycle_id=p.cycle_id WHERE p.loan_id=NEW.id)
   OR EXISTS(SELECT 1 FROM loan_ledgers ledger WHERE ledger.loan_application_id=NEW.id)
  THEN RAISE EXCEPTION 'Withdrawn group loans cannot resume or fund'; END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER group_withdrawn_loan_guard BEFORE INSERT OR UPDATE ON loan_applications FOR EACH ROW EXECUTE FUNCTION lending_group_withdrawn_loan_guard();

CREATE OR REPLACE FUNCTION lending_group_cycle_commit() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE c lending_group_cycle;p lending_group_policy;cnt integer;
BEGIN
 IF TG_TABLE_NAME='lending_group_cycle' THEN SELECT * INTO c FROM lending_group_cycle WHERE id=NEW.id;
 ELSE SELECT * INTO c FROM lending_group_cycle WHERE id=NEW.cycle_id; END IF;
 SELECT * INTO p FROM lending_group_policy WHERE id=c.policy_id;
 SELECT count(*) INTO cnt FROM lending_group_participant WHERE cycle_id=c.id;
 IF cnt>0 AND c.state NOT IN ('SUBMITTED','CANCELLED') THEN RAISE EXCEPTION 'Frozen participants require submitted cycle'; END IF;
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
     ('WITHDRAWN','PAID','DISBURSEMENT_REVERSED','MANAGER_REJECTED','LOAN_OFFICER_REJECTED','CHAIRPERSON_REJECTED','BOARD_REJECTED','CREDIT_COMMITTEE_REJECTED','ACCOUNTANT_REJECTED','REJECTED'))>least(x.guarantee_capacity,p.max_guarantee))
  THEN RAISE EXCEPTION 'Group guarantee capacity exceeded'; END IF;
 END IF;
 RETURN NULL;
END $$;

-- Keep scope predicates visible to PostgreSQL before commitment aggregation.
CREATE OR REPLACE VIEW lending_guarantor_commitment AS
 WITH ordinary AS (
  SELECT g.guarantor_member_id guarantor_id,l.id loan_id,l.sacco_id,l.station_id,l.amount principal_amount,
   CASE WHEN l.amount>0 AND terms.interest>=0
    AND ((r.loan_id IS NULL AND NOT coalesce(l.financial_snapshot ? 'recordedChargeTotal',false)) OR
      terms.charges>=0)
    THEN l.amount+terms.interest+coalesce(terms.charges,0) END amount_cap
  FROM guarantor_requests g JOIN loan_applications l ON l.id=g.loan_application_id
  LEFT JOIN accountant_loan_record r ON r.loan_id=l.id
  CROSS JOIN LATERAL(SELECT
   CASE WHEN jsonb_typeof(l.financial_snapshot->'interestAmount')='number' THEN (l.financial_snapshot->>'interestAmount')::numeric END interest,
   CASE WHEN jsonb_typeof(l.financial_snapshot->'recordedChargeTotal')='number' THEN (l.financial_snapshot->>'recordedChargeTotal')::numeric END charges) terms
  WHERE g.status='APPROVED' AND g.guarantor_member_id IS NOT NULL
   AND l.status NOT IN ('DRAFT','WITHDRAWN','PAID','DISBURSEMENT_REVERSED','MANAGER_REJECTED','LOAN_OFFICER_REJECTED','CHAIRPERSON_REJECTED','BOARD_REJECTED','CREDIT_COMMITTEE_REJECTED','ACCOUNTANT_REJECTED','REJECTED')
 ), combined AS (
  SELECT * FROM ordinary
  UNION ALL
  SELECT g.guarantor_id,l.id,g.sacco_id,g.station_id,l.amount,g.amount_cap
  FROM lending_group_guarantee g JOIN loan_applications l ON l.id=g.loan_id AND l.sacco_id=g.sacco_id AND l.station_id=g.station_id
  JOIN lending_group_cycle c ON c.id=g.cycle_id
  WHERE c.state='SUBMITTED'
   AND l.status NOT IN ('WITHDRAWN','PAID','DISBURSEMENT_REVERSED','MANAGER_REJECTED','LOAN_OFFICER_REJECTED','CHAIRPERSON_REJECTED','BOARD_REJECTED','CREDIT_COMMITTEE_REJECTED','ACCOUNTANT_REJECTED','REJECTED')
 )
 SELECT guarantor_id,loan_id,sacco_id,station_id,max(principal_amount) principal_amount,
  CASE WHEN bool_or(amount_cap IS NULL) THEN NULL ELSE max(amount_cap) END amount_cap
 FROM combined GROUP BY guarantor_id,loan_id,sacco_id,station_id;

-- Extend only the transition predicate; retain the current recorded-contract immutability checks.
DO $$ DECLARE definition text; original text='IF NEW.status IS DISTINCT FROM OLD.status AND NOT ('; BEGIN
 SELECT pg_get_functiondef('accountant_loan_terms_protect()'::regprocedure) INTO definition;
 IF strpos(definition,original)=0 THEN RAISE EXCEPTION 'Recorded transition guard was not found'; END IF;
 definition=replace(definition,original,original||$replacement$(NEW.status='WITHDRAWN' AND EXISTS(SELECT 1 FROM lending_group_participant p JOIN lending_group_termination t ON t.cycle_id=p.cycle_id WHERE p.loan_id=NEW.id)) OR $replacement$);
 EXECUTE definition;
END $$;
