-- Deduction instructions are collection requests, never repayment or journal entries.
ALTER TABLE members ADD COLUMN employee_number varchar(50);
ALTER TABLE members ADD CONSTRAINT employee_number_format
 CHECK(employee_number IS NULL OR (length(trim(employee_number))>0 AND employee_number ~ '^[A-Za-z0-9/._ -]+$'));

CREATE TABLE payroll_employer (
 id uuid PRIMARY KEY, sacco_id varchar(255) NOT NULL, station_id varchar(255) NOT NULL,
 name varchar(160) NOT NULL CHECK(length(trim(name))>0),
 agreement_reference varchar(250) NOT NULL CHECK(length(trim(agreement_reference))>0),
 actor_id uuid NOT NULL REFERENCES members(id), created_at timestamptz NOT NULL,
 UNIQUE(sacco_id,station_id,id), FOREIGN KEY(sacco_id,station_id) REFERENCES sacco_stations(sacco_id,station_id)
);
CREATE UNIQUE INDEX payroll_employer_name ON payroll_employer(sacco_id,station_id,lower(name));
CREATE TABLE payroll_instruction (
 id uuid PRIMARY KEY, sacco_id varchar(255) NOT NULL, station_id varchar(255) NOT NULL,
 employer_id uuid NOT NULL, loan_id uuid NOT NULL REFERENCES loan_ledgers(loan_application_id),
 client_id uuid NOT NULL REFERENCES members(id), employee_number varchar(50) NOT NULL,
 monthly_limit numeric(18,2) NOT NULL CHECK(monthly_limit>0),
 effective_from date NOT NULL, effective_to date NOT NULL CHECK(effective_to>=effective_from),
 evidence varchar(500) NOT NULL CHECK(length(trim(evidence))>0),
 actor_id uuid NOT NULL REFERENCES members(id), created_at timestamptz NOT NULL,
 FOREIGN KEY(sacco_id,station_id,employer_id) REFERENCES payroll_employer(sacco_id,station_id,id)
);
CREATE INDEX payroll_instruction_employer ON payroll_instruction(sacco_id,station_id,employer_id,created_at,id);
CREATE INDEX payroll_instruction_loan ON payroll_instruction(loan_id);
CREATE TABLE payroll_instruction_stop (
 instruction_id uuid PRIMARY KEY REFERENCES payroll_instruction(id), actor_id uuid NOT NULL REFERENCES members(id),
 reason varchar(500) NOT NULL CHECK(length(trim(reason))>0), created_at timestamptz NOT NULL
);
CREATE TABLE payroll_batch (
 id uuid PRIMARY KEY, sacco_id varchar(255) NOT NULL, station_id varchar(255) NOT NULL,
 employer_id uuid NOT NULL, employer_name varchar(160) NOT NULL, agreement_reference varchar(250) NOT NULL,
 institution_name varchar(255) NOT NULL, branch_name varchar(255) NOT NULL,
 payroll_month date NOT NULL CHECK(extract(day from payroll_month)=1), deduction_date date NOT NULL,
 include_arrears boolean NOT NULL, cutoff timestamptz NOT NULL,
 request_key uuid NOT NULL, payload_hash char(64) NOT NULL, snapshot_hash char(64) NOT NULL,
 frozen_rows integer NOT NULL CHECK(frozen_rows BETWEEN 1 AND 1000), frozen_total numeric(18,2) NOT NULL CHECK(frozen_total>0),
 actor_id uuid NOT NULL REFERENCES members(id), actor_name varchar(255) NOT NULL,
 replaces_id uuid UNIQUE REFERENCES payroll_batch(id),
 FOREIGN KEY(sacco_id,station_id,employer_id) REFERENCES payroll_employer(sacco_id,station_id,id),
 UNIQUE(sacco_id,station_id,request_key),
 CHECK(deduction_date>=payroll_month AND deduction_date<payroll_month+interval '1 month')
);
CREATE INDEX payroll_batch_register ON payroll_batch(sacco_id,station_id,cutoff DESC,id);
CREATE INDEX payroll_batch_period ON payroll_batch(employer_id,payroll_month);
CREATE TABLE payroll_batch_line (
 batch_id uuid NOT NULL REFERENCES payroll_batch(id), line_number integer NOT NULL CHECK(line_number>0),
 instruction_id uuid NOT NULL REFERENCES payroll_instruction(id), loan_id uuid NOT NULL REFERENCES loan_ledgers(loan_application_id),
 client_id uuid NOT NULL REFERENCES members(id), employee_number varchar(50) NOT NULL,
 client_name varchar(255) NOT NULL, client_number varchar(255) NOT NULL, loan_number varchar(50) NOT NULL,
 principal numeric(18,2) NOT NULL CHECK(principal>=0), interest numeric(18,2) NOT NULL CHECK(interest>=0),
 amount numeric(18,2) NOT NULL CHECK(amount>=0 AND amount<=principal+interest),
 issue varchar(40) NOT NULL CHECK(issue IN ('','employeeChanged','expired','inactiveClient','loanUnavailable','noDue','overlap')),
 PRIMARY KEY(batch_id,line_number), UNIQUE(batch_id,instruction_id), UNIQUE(batch_id,loan_id),
 CHECK((issue='' AND amount>0) OR (issue<>'' AND amount=0))
);
CREATE TABLE payroll_batch_event (
 id uuid PRIMARY KEY, batch_id uuid NOT NULL REFERENCES payroll_batch(id),
 kind varchar(16) NOT NULL CHECK(kind IN ('SENT','CANCELLED','DOWNLOAD')),
 actor_id uuid NOT NULL REFERENCES members(id), evidence varchar(500) NOT NULL CHECK(length(trim(evidence))>0),
 created_at timestamptz NOT NULL
);
CREATE UNIQUE INDEX payroll_batch_single_event ON payroll_batch_event(batch_id,kind) WHERE kind IN ('SENT','CANCELLED');
CREATE INDEX payroll_batch_event_history ON payroll_batch_event(batch_id,created_at,id);
CREATE TABLE payroll_batch_document (
 batch_id uuid NOT NULL REFERENCES payroll_batch(id), language varchar(2) NOT NULL CHECK(language IN ('en','sw')),
 content bytea NOT NULL, checksum char(64) NOT NULL, created_at timestamptz NOT NULL,
 PRIMARY KEY(batch_id,language), CHECK(octet_length(content)>0 AND octet_length(content)<=20000000)
);

CREATE FUNCTION validate_payroll_instruction() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 PERFORM pg_advisory_xact_lock(hashtextextended('PAYROLL/'||NEW.sacco_id||'/'||NEW.station_id||'/EMPLOYER/'||NEW.employer_id,0));
 PERFORM pg_advisory_xact_lock(hashtextextended('PAYROLL/'||NEW.sacco_id||'/'||NEW.station_id||'/LOAN/'||NEW.loan_id,0));
 IF NOT EXISTS(SELECT 1 FROM loan_ledgers l JOIN loan_applications a ON a.id=l.loan_application_id
   JOIN members m ON m.id=NEW.client_id
   WHERE l.loan_application_id=NEW.loan_id AND l.sacco_id=NEW.sacco_id AND l.station_id=NEW.station_id
    AND a.sacco_id=NEW.sacco_id AND a.station_id=NEW.station_id AND a.applicant_member_id=NEW.client_id
    AND l.applicant_member_id=NEW.client_id AND m.sacco_id=NEW.sacco_id AND m.employee_number=NEW.employee_number)
 THEN RAISE EXCEPTION 'Payroll instruction scope or employee mapping mismatch'; END IF;
 IF EXISTS(SELECT 1 FROM payroll_instruction i WHERE i.sacco_id=NEW.sacco_id AND i.station_id=NEW.station_id
   AND NOT EXISTS(SELECT 1 FROM payroll_instruction_stop s WHERE s.instruction_id=i.id)
   AND (i.loan_id=NEW.loan_id OR (i.employer_id=NEW.employer_id AND
     ((lower(i.employee_number)=lower(NEW.employee_number) AND i.client_id<>NEW.client_id)
      OR (i.client_id=NEW.client_id AND i.employee_number<>NEW.employee_number)))))
 THEN RAISE EXCEPTION 'Conflicting active payroll instruction'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER payroll_instruction_scope BEFORE INSERT ON payroll_instruction FOR EACH ROW EXECUTE FUNCTION validate_payroll_instruction();
CREATE FUNCTION validate_payroll_line() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM payroll_batch b JOIN payroll_instruction i ON i.id=NEW.instruction_id
   WHERE b.id=NEW.batch_id AND i.sacco_id=b.sacco_id AND i.station_id=b.station_id AND i.employer_id=b.employer_id
    AND i.loan_id=NEW.loan_id AND i.client_id=NEW.client_id AND i.employee_number=NEW.employee_number)
 THEN RAISE EXCEPTION 'Payroll snapshot scope mismatch'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER payroll_line_scope BEFORE INSERT ON payroll_batch_line FOR EACH ROW EXECUTE FUNCTION validate_payroll_line();
CREATE FUNCTION validate_payroll_batch_totals() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE batch uuid; expected payroll_batch; actual_rows integer; actual_total numeric;
BEGIN
 IF TG_TABLE_NAME='payroll_batch' THEN batch:=NEW.id; ELSE batch:=NEW.batch_id; END IF;
 SELECT * INTO STRICT expected FROM payroll_batch WHERE id=batch;
 SELECT count(*),coalesce(sum(amount),0) INTO actual_rows,actual_total FROM payroll_batch_line WHERE batch_id=batch;
 IF actual_rows<>expected.frozen_rows OR actual_total<>expected.frozen_total
 THEN RAISE EXCEPTION 'Frozen payroll lines and totals must be complete and unchanged'; END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER payroll_batch_totals AFTER INSERT ON payroll_batch DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION validate_payroll_batch_totals();
CREATE CONSTRAINT TRIGGER payroll_line_totals AFTER INSERT ON payroll_batch_line DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION validate_payroll_batch_totals();
CREATE TRIGGER payroll_employer_immutable BEFORE UPDATE OR DELETE ON payroll_employer FOR EACH ROW EXECUTE FUNCTION protect_loan_financial_history();
CREATE TRIGGER payroll_instruction_immutable BEFORE UPDATE OR DELETE ON payroll_instruction FOR EACH ROW EXECUTE FUNCTION protect_loan_financial_history();
CREATE TRIGGER payroll_instruction_stop_immutable BEFORE UPDATE OR DELETE ON payroll_instruction_stop FOR EACH ROW EXECUTE FUNCTION protect_loan_financial_history();
CREATE TRIGGER payroll_batch_immutable BEFORE UPDATE OR DELETE ON payroll_batch FOR EACH ROW EXECUTE FUNCTION protect_loan_financial_history();
CREATE TRIGGER payroll_batch_line_immutable BEFORE UPDATE OR DELETE ON payroll_batch_line FOR EACH ROW EXECUTE FUNCTION protect_loan_financial_history();
CREATE TRIGGER payroll_batch_event_immutable BEFORE UPDATE OR DELETE ON payroll_batch_event FOR EACH ROW EXECUTE FUNCTION protect_loan_financial_history();
CREATE TRIGGER payroll_batch_document_immutable BEFORE UPDATE OR DELETE ON payroll_batch_document FOR EACH ROW EXECUTE FUNCTION protect_loan_financial_history();
-- Claims are exposed in the access matrix; none are assigned automatically.
