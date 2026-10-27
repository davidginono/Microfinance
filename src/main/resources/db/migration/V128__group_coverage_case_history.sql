-- Operational cases trace existing obligations and posted money. They never create a receivable.
CREATE TABLE lending_group_coverage_policy (
 id uuid PRIMARY KEY,sacco_id varchar(255) NOT NULL,station_id varchar(255) NOT NULL,product_id uuid NOT NULL REFERENCES loan_product_settings(id),
 revision integer NOT NULL CHECK(revision>0),previous_id uuid REFERENCES lending_group_coverage_policy(id),request_key uuid NOT NULL,payload_hash varchar(64) NOT NULL,
 notice_days integer NOT NULL CHECK(notice_days BETWEEN 14 AND 365),case_days integer NOT NULL CHECK(case_days BETWEEN 1 AND 365),promise_days integer NOT NULL CHECK(promise_days BETWEEN 1 AND 365),
 reference varchar(500) NOT NULL CHECK(length(trim(reference))>0),enabled boolean NOT NULL,recorded_by uuid NOT NULL REFERENCES members(id),recorded_at timestamptz NOT NULL,
 UNIQUE(sacco_id,station_id,product_id,revision),UNIQUE(sacco_id,station_id,request_key)
);
CREATE UNIQUE INDEX ix_coverage_policy_successor ON lending_group_coverage_policy(previous_id) WHERE previous_id IS NOT NULL;
CREATE INDEX ix_coverage_policy_scope ON lending_group_coverage_policy(sacco_id,station_id,product_id,revision DESC);
CREATE TABLE lending_group_coverage_case (
 id uuid PRIMARY KEY,cycle_id uuid NOT NULL,loan_id uuid NOT NULL,source_id uuid NOT NULL,source_kind varchar(16) NOT NULL CHECK(source_kind IN ('RECIPROCAL','PERSONAL','JOINT','VOLUNTARY')),
 sacco_id varchar(255) NOT NULL,station_id varchar(255) NOT NULL,client_id uuid NOT NULL REFERENCES members(id),client_number varchar(255) NOT NULL,client_name varchar(255) NOT NULL,borrower_name varchar(255) NOT NULL,loan_number varchar(255) NOT NULL,
 policy_id uuid NOT NULL REFERENCES lending_group_coverage_policy(id),request_key uuid NOT NULL,payload_hash varchar(64) NOT NULL,
 call_date date NOT NULL,expires_on date NOT NULL CHECK(expires_on>=call_date),oldest_due date NOT NULL CHECK(oldest_due<call_date),
 requested numeric(18,2) NOT NULL CHECK(requested>0),source_cap numeric(18,2) NOT NULL CHECK(source_cap>=requested),arrears_snapshot numeric(18,2) NOT NULL CHECK(arrears_snapshot>=requested),
 borrower_notice_date date NOT NULL,person_notice_date date NOT NULL,borrower_notice_evidence varchar(500) NOT NULL CHECK(length(trim(borrower_notice_evidence))>0),person_notice_evidence varchar(500) NOT NULL CHECK(length(trim(person_notice_evidence))>0),
 notice_language varchar(8) NOT NULL CHECK(notice_language IN ('SW','BOTH')),filename varchar(200) NOT NULL,notice_pdf bytea NOT NULL CHECK(octet_length(notice_pdf) BETWEEN 5 AND 5242880),notice_sha256 varchar(64) NOT NULL,notice_pages integer NOT NULL CHECK(notice_pages BETWEEN 1 AND 50),
 terms_reference varchar(500) NOT NULL,recorded_by uuid NOT NULL REFERENCES members(id),recorded_at timestamptz NOT NULL,
 UNIQUE(id,loan_id,client_id,sacco_id,station_id),UNIQUE(sacco_id,station_id,request_key),
 FOREIGN KEY(cycle_id,sacco_id,station_id) REFERENCES lending_group_cycle(id,sacco_id,station_id),FOREIGN KEY(loan_id,sacco_id,station_id) REFERENCES loan_applications(id,sacco_id,station_id)
);
CREATE INDEX ix_coverage_case_cycle ON lending_group_coverage_case(sacco_id,station_id,cycle_id,recorded_at DESC,id);
CREATE INDEX ix_coverage_case_person ON lending_group_coverage_case(client_id,sacco_id,station_id,recorded_at DESC,id);
CREATE INDEX ix_coverage_case_loan ON lending_group_coverage_case(loan_id,source_id);
CREATE TABLE lending_group_coverage_event (
 id uuid PRIMARY KEY,case_id uuid NOT NULL REFERENCES lending_group_coverage_case(id),sequence integer NOT NULL CHECK(sequence>0),kind varchar(16) NOT NULL CHECK(kind IN ('ACKNOWLEDGED','PROMISED','DISPUTED','CANCELLED','CLOSED')),
 amount numeric(18,2),promise_on date,reason varchar(500) NOT NULL,request_key uuid NOT NULL,payload_hash varchar(64) NOT NULL,
 actor_id uuid NOT NULL REFERENCES members(id),recorded_at timestamptz NOT NULL,
 CHECK((kind='PROMISED' AND amount>0 AND promise_on IS NOT NULL) OR (kind<>'PROMISED' AND amount IS NULL AND promise_on IS NULL)),
 CHECK(kind='ACKNOWLEDGED' OR length(trim(reason))>0),UNIQUE(case_id,sequence),UNIQUE(case_id,request_key)
);
CREATE TABLE lending_group_coverage_payment (
 id uuid PRIMARY KEY,case_id uuid NOT NULL REFERENCES lending_group_coverage_case(id),posting_id uuid NOT NULL REFERENCES accountant_loan_post(id),contribution_id uuid REFERENCES lending_group_contribution(id),
 amount numeric(18,2) NOT NULL CHECK(amount>0),principal numeric(18,2) NOT NULL CHECK(principal>=0),interest numeric(18,2) NOT NULL CHECK(interest>=0),charges numeric(18,2) NOT NULL CHECK(charges>=0),
 CHECK(amount=principal+interest+charges),payer_text varchar(500) NOT NULL,evidence varchar(500) NOT NULL CHECK(length(trim(evidence))>0),
 request_key uuid NOT NULL,payload_hash varchar(64) NOT NULL,actor_id uuid NOT NULL REFERENCES members(id),recorded_at timestamptz NOT NULL,UNIQUE(case_id,request_key)
);
CREATE INDEX ix_coverage_payment_source ON lending_group_coverage_payment(posting_id,contribution_id);
CREATE INDEX ix_coverage_payment_case ON lending_group_coverage_payment(case_id,recorded_at,id);
CREATE TABLE lending_group_coverage_payment_correction (
 id uuid PRIMARY KEY,payment_id uuid NOT NULL UNIQUE REFERENCES lending_group_coverage_payment(id),reason varchar(500) NOT NULL CHECK(length(trim(reason))>0),
 request_key uuid NOT NULL,actor_id uuid NOT NULL REFERENCES members(id),recorded_at timestamptz NOT NULL,UNIQUE(payment_id,request_key)
);
CREATE FUNCTION lending_coverage_due(application uuid,cutoff date) RETURNS numeric LANGUAGE sql STABLE AS $$
 SELECT CASE WHEN count(*)=0 THEN NULL ELSE coalesce(sum(principal-principal_paid+interest-interest_paid-interest_excluded-interest_waived) FILTER(WHERE due_date<cutoff),0) END
 FROM loan_ledger_installments WHERE loan_application_id=application;
$$;
CREATE VIEW lending_group_coverage_cash AS
 SELECT x.*,p.effective_date,p.reference,p.reverses_id,
  (SELECT r.id FROM accountant_loan_post r WHERE r.reverses_id=p.id) reversed_by,
  c.id corrected_by,CASE WHEN c.id IS NOT NULL OR EXISTS(SELECT 1 FROM accountant_loan_post r WHERE r.reverses_id=p.id) THEN 0 ELSE x.amount END net_amount
 FROM lending_group_coverage_payment x JOIN accountant_loan_post p ON p.id=x.posting_id
 LEFT JOIN lending_group_coverage_payment_correction c ON c.payment_id=x.id;
CREATE VIEW lending_group_coverage_balance AS
 SELECT c.id,c.loan_id,c.source_id,c.source_kind,c.client_id,c.sacco_id,c.station_id,c.requested,c.expires_on,
  lending_coverage_due(c.loan_id,c.call_date) remaining_due,
  coalesce((SELECT sum(net_amount) FROM lending_group_coverage_cash WHERE case_id=c.id),0) received,
  (SELECT kind FROM lending_group_coverage_event WHERE case_id=c.id AND kind IN ('CANCELLED','CLOSED') ORDER BY sequence DESC LIMIT 1) closure,
  CASE WHEN lending_coverage_due(c.loan_id,c.call_date) IS NULL THEN NULL ELSE greatest(0,least(c.requested-coalesce((SELECT sum(net_amount) FROM lending_group_coverage_cash WHERE case_id=c.id),0),lending_coverage_due(c.loan_id,c.call_date))) END unpaid
 FROM lending_group_coverage_case c;

CREATE TRIGGER coverage_policy_immutable BEFORE UPDATE OR DELETE ON lending_group_coverage_policy FOR EACH ROW EXECUTE FUNCTION gl_protect_evidence();
CREATE TRIGGER coverage_case_immutable BEFORE UPDATE OR DELETE ON lending_group_coverage_case FOR EACH ROW EXECUTE FUNCTION gl_protect_evidence();
CREATE TRIGGER coverage_event_immutable BEFORE UPDATE OR DELETE ON lending_group_coverage_event FOR EACH ROW EXECUTE FUNCTION gl_protect_evidence();
CREATE TRIGGER coverage_payment_immutable BEFORE UPDATE OR DELETE ON lending_group_coverage_payment FOR EACH ROW EXECUTE FUNCTION gl_protect_evidence();
CREATE TRIGGER coverage_correction_immutable BEFORE UPDATE OR DELETE ON lending_group_coverage_payment_correction FOR EACH ROW EXECUTE FUNCTION gl_protect_evidence();

CREATE VIEW lending_group_coverage_source AS
 SELECT x.id source_id,'RECIPROCAL'::text source_kind,x.cycle_id,x.loan_id,x.guarantor_id client_id,x.sacco_id,x.station_id,x.amount_cap cap,x.terms_reference
 FROM lending_group_guarantee x JOIN lending_group_consent_evidence e ON e.cycle_id=x.cycle_id AND e.client_id=x.guarantor_id AND e.sacco_id=x.sacco_id AND e.station_id=x.station_id
 UNION ALL
 SELECT x.id,'PERSONAL',x.cycle_id,x.loan_id,x.client_id,x.sacco_id,x.station_id,x.amount_cap,x.document_metadata->>'documentReference'
 FROM lending_group_personal_guarantee x JOIN lending_group_personal_decision d ON d.offer_id=x.id AND d.decision='ACCEPTED'
 UNION ALL
 SELECT x.participant_id,CASE WHEN f.liability_rule='JOINT_AND_SEVERAL' THEN 'JOINT' ELSE 'VOLUNTARY' END,x.cycle_id,f.loan_id,x.client_id,f.sacco_id,f.station_id,x.liability_cap,p.agreement_reference
 FROM lending_group_shared_allocation x JOIN lending_group_shared_facility f ON f.cycle_id=x.cycle_id
 JOIN lending_group_cycle c ON c.id=f.cycle_id JOIN lending_group_policy p ON p.id=c.policy_id
 JOIN lending_group_shared_payout signed ON signed.cycle_id=x.cycle_id AND signed.client_id=x.client_id;

CREATE FUNCTION lending_coverage_scope() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE l loan_applications;p lending_group_coverage_policy;c lending_group_coverage_case;s record;available numeric;already numeric;
BEGIN
 IF TG_TABLE_NAME='lending_group_coverage_policy' THEN
  PERFORM pg_advisory_xact_lock(hashtextextended('COVERAGE_POLICY/'||NEW.sacco_id||'/'||NEW.station_id||'/'||NEW.product_id::text,0));
  SELECT * INTO p FROM lending_group_coverage_policy WHERE sacco_id=NEW.sacco_id AND station_id=NEW.station_id AND product_id=NEW.product_id ORDER BY revision DESC LIMIT 1;
  IF NEW.previous_id IS DISTINCT FROM p.id OR NEW.revision<>coalesce(p.revision,0)+1
   OR NOT EXISTS(SELECT 1 FROM loan_product_settings WHERE id=NEW.product_id AND sacco_id=NEW.sacco_id)
   OR NOT EXISTS(SELECT 1 FROM members WHERE id=NEW.recorded_by AND sacco_id=NEW.sacco_id AND station_id=NEW.station_id AND status='ACTIVE')
  THEN RAISE EXCEPTION 'Coverage policy scope or revision changed';END IF;
  RETURN NEW;
 END IF;
 SELECT * INTO l FROM loan_applications WHERE id=NEW.loan_id AND sacco_id=NEW.sacco_id AND station_id=NEW.station_id FOR UPDATE;
 SELECT * INTO s FROM lending_group_coverage_source WHERE source_id=NEW.source_id AND source_kind=NEW.source_kind AND cycle_id=NEW.cycle_id AND loan_id=l.id AND client_id=NEW.client_id AND sacco_id=NEW.sacco_id AND station_id=NEW.station_id;
 PERFORM pg_advisory_xact_lock(hashtextextended('COVERAGE_POLICY/'||NEW.sacco_id||'/'||NEW.station_id||'/'||l.loan_product_setting_id::text,0));
 SELECT * INTO p FROM lending_group_coverage_policy WHERE id=NEW.policy_id AND sacco_id=NEW.sacco_id AND station_id=NEW.station_id AND product_id=l.loan_product_setting_id;
 IF l.id IS NULL OR s.source_id IS NULL OR p.id IS NULL OR NOT p.enabled
  OR EXISTS(SELECT 1 FROM lending_group_coverage_policy n WHERE n.sacco_id=p.sacco_id AND n.station_id=p.station_id AND n.product_id=p.product_id AND n.revision>p.revision)
  OR l.status NOT IN ('DISBURSED','PAR','DEFAULTED','WRITTEN_OFF')
  OR NOT EXISTS(SELECT 1 FROM loan_ledgers WHERE loan_application_id=l.id AND sacco_id=l.sacco_id AND station_id=l.station_id)
  OR NOT EXISTS(SELECT 1 FROM accountant_loan_post WHERE loan_id=l.id AND kind='DISBURSEMENT' AND state='POSTED' AND sacco_id=l.sacco_id AND station_id=l.station_id)
  OR NOT EXISTS(SELECT 1 FROM lending_group_cycle WHERE id=NEW.cycle_id AND state='SUBMITTED' AND sacco_id=l.sacco_id AND station_id=l.station_id)
  OR NEW.source_cap IS DISTINCT FROM s.cap OR NEW.terms_reference IS DISTINCT FROM s.terms_reference
  OR NEW.loan_number IS DISTINCT FROM coalesce(l.loan_id,l.application_number::text)
  OR NEW.borrower_name IS DISTINCT FROM (SELECT full_name FROM loan_borrower_directory WHERE party_id=l.borrower_party_id AND sacco_id=l.sacco_id)
  OR NEW.call_date IS DISTINCT FROM (NEW.recorded_at AT TIME ZONE 'Africa/Dar_es_Salaam')::date
  OR NEW.expires_on<>NEW.call_date+p.case_days
  OR NEW.borrower_notice_date>NEW.call_date-p.notice_days OR NEW.person_notice_date>NEW.call_date-p.notice_days
  OR NEW.borrower_notice_date<NEW.oldest_due OR NEW.person_notice_date<NEW.oldest_due
  OR least(NEW.borrower_notice_date,NEW.person_notice_date)<(SELECT min(effective_date) FROM accountant_loan_post WHERE loan_id=l.id AND kind='DISBURSEMENT' AND state='POSTED')
  OR NEW.arrears_snapshot IS DISTINCT FROM lending_coverage_due(l.id,NEW.call_date)
  OR NEW.oldest_due IS DISTINCT FROM (SELECT min(due_date) FROM loan_ledger_installments WHERE loan_application_id=l.id AND due_date<NEW.call_date AND principal-principal_paid+interest-interest_paid-interest_excluded-interest_waived>0)
  OR NOT EXISTS(SELECT 1 FROM members WHERE id=NEW.client_id AND sacco_id=l.sacco_id AND station_id=l.station_id AND status='ACTIVE' AND is_member AND member_no=NEW.client_number AND full_name=NEW.client_name)
  OR NOT EXISTS(SELECT 1 FROM members WHERE id=NEW.recorded_by AND sacco_id=l.sacco_id AND station_id=l.station_id AND status='ACTIVE')
 THEN RAISE EXCEPTION 'Coverage call requires current scoped policy, obligation, overdue ledger and served notices';END IF;
 SELECT coalesce(sum(unpaid),0) INTO already FROM lending_group_coverage_balance WHERE loan_id=l.id AND closure IS NULL AND expires_on>=NEW.call_date;
 IF NEW.requested>NEW.arrears_snapshot-already THEN RAISE EXCEPTION 'Coverage calls cannot reserve the same shortfall twice';END IF;
 SELECT coalesce(sum(received),0) INTO already FROM lending_group_coverage_balance WHERE loan_id=l.id AND source_id=NEW.source_id AND source_kind=NEW.source_kind;
 IF NEW.requested>s.cap-already THEN RAISE EXCEPTION 'Coverage exceeds the recorded remaining cap';END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER coverage_policy_scope BEFORE INSERT ON lending_group_coverage_policy FOR EACH ROW EXECUTE FUNCTION lending_coverage_scope();
CREATE TRIGGER coverage_case_scope BEFORE INSERT ON lending_group_coverage_case FOR EACH ROW EXECUTE FUNCTION lending_coverage_scope();

CREATE FUNCTION lending_coverage_event_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE c lending_group_coverage_case;p lending_group_coverage_policy;last_sequence integer;current_due numeric;
BEGIN
 SELECT * INTO c FROM lending_group_coverage_case WHERE id=NEW.case_id;
 PERFORM 1 FROM loan_applications WHERE id=c.loan_id FOR UPDATE;
 PERFORM 1 FROM lending_group_coverage_case WHERE id=c.id FOR UPDATE;
 SELECT coalesce(max(sequence),0) INTO last_sequence FROM lending_group_coverage_event WHERE case_id=c.id;
 IF NEW.sequence<>last_sequence+1 OR EXISTS(SELECT 1 FROM lending_group_coverage_event WHERE case_id=c.id AND kind IN ('CANCELLED','CLOSED'))
  OR NEW.recorded_at<c.recorded_at
  OR NOT EXISTS(SELECT 1 FROM members WHERE id=NEW.actor_id AND sacco_id=c.sacco_id AND station_id=c.station_id AND status='ACTIVE')
 THEN RAISE EXCEPTION 'Coverage event is stale or outside scope';END IF;
 IF NEW.kind IN ('ACKNOWLEDGED','PROMISED','DISPUTED') THEN
  IF NEW.actor_id<>c.client_id OR NEW.recorded_at<c.recorded_at OR (NEW.recorded_at AT TIME ZONE 'Africa/Dar_es_Salaam')::date>c.expires_on
  THEN RAISE EXCEPTION 'Only the named person can respond to an open call';END IF;
  IF NEW.kind='PROMISED' THEN
   SELECT * INTO p FROM lending_group_coverage_policy WHERE id=c.policy_id;
   SELECT unpaid INTO current_due FROM lending_group_coverage_balance WHERE id=c.id;
   IF current_due IS NULL OR NEW.amount>current_due OR NEW.promise_on<(NEW.recorded_at AT TIME ZONE 'Africa/Dar_es_Salaam')::date
    OR NEW.promise_on>least(c.expires_on,(NEW.recorded_at AT TIME ZONE 'Africa/Dar_es_Salaam')::date+p.promise_days)
   THEN RAISE EXCEPTION 'A promise must fit the remaining original shortfall and frozen expiry';END IF;
  END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER coverage_event_scope BEFORE INSERT ON lending_group_coverage_event FOR EACH ROW EXECUTE FUNCTION lending_coverage_event_guard();

CREATE FUNCTION lending_coverage_payment_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE c lending_group_coverage_case;p accountant_loan_post;x lending_group_contribution;prior lending_group_coverage_payment;total numeric;
BEGIN
 IF TG_TABLE_NAME='lending_group_coverage_payment_correction' THEN
  SELECT * INTO prior FROM lending_group_coverage_payment WHERE id=NEW.payment_id;
  SELECT * INTO c FROM lending_group_coverage_case WHERE id=prior.case_id;
  PERFORM 1 FROM loan_applications WHERE id=c.loan_id FOR UPDATE;
  IF prior.id IS NULL OR prior.actor_id=NEW.actor_id OR NEW.recorded_at<prior.recorded_at
   OR NOT EXISTS(SELECT 1 FROM members WHERE id=NEW.actor_id AND sacco_id=c.sacco_id AND station_id=c.station_id AND status='ACTIVE')
  THEN RAISE EXCEPTION 'Coverage attribution correction requires a different scoped staff member';END IF;
  RETURN NEW;
 END IF;
 SELECT * INTO c FROM lending_group_coverage_case WHERE id=NEW.case_id;
 SELECT * INTO p FROM accountant_loan_post WHERE id=NEW.posting_id;
 PERFORM 1 FROM loan_applications WHERE id=c.loan_id FOR UPDATE;
 IF c.id IS NULL OR p.id IS NULL OR p.kind<>'PAYMENT' OR p.state<>'POSTED' OR p.loan_id<>c.loan_id OR p.sacco_id<>c.sacco_id OR p.station_id<>c.station_id
  OR p.recorded_at<c.recorded_at OR p.effective_date<c.call_date OR NEW.recorded_at<p.recorded_at
  OR EXISTS(SELECT 1 FROM accountant_loan_post WHERE reverses_id=p.id)
  OR NOT EXISTS(SELECT 1 FROM members WHERE id=NEW.actor_id AND sacco_id=c.sacco_id AND station_id=c.station_id AND status='ACTIVE')
 THEN RAISE EXCEPTION 'Coverage received requires its original posted loan payment';END IF;
 IF NEW.contribution_id IS NOT NULL THEN
  SELECT x0.* INTO x FROM lending_group_contribution x0 JOIN lending_group_receipt_loan r ON r.receipt_id=x0.receipt_id AND r.loan_id=x0.loan_id AND r.posting_id=p.id
   WHERE x0.id=NEW.contribution_id AND x0.loan_id=c.loan_id AND x0.sacco_id=c.sacco_id AND x0.station_id=c.station_id AND x0.payer_id=c.client_id AND x0.reverses_id IS NULL;
  IF x.id IS NULL OR ROW(NEW.amount,NEW.principal,NEW.interest,NEW.charges) IS DISTINCT FROM ROW(x.amount,x.principal,x.interest,x.charges)
   OR NEW.payer_text IS DISTINCT FROM x.payer_number||' - '||x.payer_name
  THEN RAISE EXCEPTION 'Coverage must retain the identified contribution and exact posted components';END IF;
 ELSE
  IF EXISTS(SELECT 1 FROM lending_group_receipt_loan WHERE posting_id=p.id)
   OR ROW(NEW.amount,NEW.principal,NEW.interest,NEW.charges) IS DISTINCT FROM ROW(p.amount,p.principal_amount,p.interest_amount,p.charge_amount)
   OR NEW.payer_text IS DISTINCT FROM (SELECT coalesce(nullif(r.payer_name,''),(SELECT min(payer) FROM lending_group_shared_movement WHERE posting_id=p.id),p.notes,'') FROM accountant_loan_post original LEFT JOIN accountant_loan_receipt_row rr ON rr.posting_id=original.id LEFT JOIN accountant_loan_receipt r ON r.id=rr.receipt_id WHERE original.id=p.id)
   OR length(trim(NEW.payer_text))=0
  THEN RAISE EXCEPTION 'Coverage must identify a complete actual payer payment';END IF;
 END IF;
 IF EXISTS(SELECT 1 FROM lending_group_coverage_payment old WHERE old.posting_id=p.id AND old.contribution_id IS NOT DISTINCT FROM NEW.contribution_id
   AND NOT EXISTS(SELECT 1 FROM lending_group_coverage_payment_correction correction WHERE correction.payment_id=old.id))
 THEN RAISE EXCEPTION 'A posted payer source cannot be attributed to two coverage cases';END IF;
 SELECT coalesce(sum(b.received),0) INTO total FROM lending_group_coverage_balance b WHERE b.loan_id=c.loan_id AND b.source_id=c.source_id AND b.source_kind=c.source_kind;
 IF total+NEW.amount>c.source_cap THEN RAISE EXCEPTION 'Coverage cash attribution exceeds the retained source ceiling';END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER coverage_payment_scope BEFORE INSERT ON lending_group_coverage_payment FOR EACH ROW EXECUTE FUNCTION lending_coverage_payment_guard();
CREATE TRIGGER coverage_correction_scope BEFORE INSERT ON lending_group_coverage_payment_correction FOR EACH ROW EXECUTE FUNCTION lending_coverage_payment_guard();
