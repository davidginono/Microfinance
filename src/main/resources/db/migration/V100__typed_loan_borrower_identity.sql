-- A loan owns one borrower identity; its ledger inherits that identity through loan_application_id.
-- This additive step retains the person-only application/ledger columns until all group readers are ready.
DO $$ BEGIN
 IF EXISTS(SELECT 1 FROM loan_applications a LEFT JOIN members m ON m.id=a.applicant_member_id
   WHERE m.id IS NULL OR a.sacco_id IS DISTINCT FROM m.sacco_id)
 THEN RAISE EXCEPTION 'Existing loan borrower institution mismatch requires review'; END IF;
 IF EXISTS(SELECT 1 FROM loan_ledgers l JOIN loan_applications a ON a.id=l.loan_application_id
   WHERE ROW(l.sacco_id,l.station_id,l.applicant_member_id) IS DISTINCT FROM ROW(a.sacco_id,a.station_id,a.applicant_member_id))
 THEN RAISE EXCEPTION 'Existing ledger borrower scope mismatch requires review'; END IF;
END $$;
ALTER TABLE members ADD CONSTRAINT uq_member_borrower_scope UNIQUE(id,sacco_id);
CREATE TABLE loan_borrower_party (
 id uuid PRIMARY KEY,sacco_id varchar(255) NOT NULL,
 kind varchar(16) NOT NULL CHECK(kind IN ('CLIENT','JOINT_GROUP','ORGANIZATION')),
 client_id uuid,group_id uuid,group_station_id varchar(255),
 created_at timestamptz NOT NULL DEFAULT transaction_timestamp(),
 CHECK((kind='CLIENT' AND client_id IS NOT NULL AND id=client_id AND group_id IS NULL AND group_station_id IS NULL)
    OR (kind IN ('JOINT_GROUP','ORGANIZATION') AND client_id IS NULL AND group_id IS NOT NULL AND group_station_id IS NOT NULL)),
 UNIQUE(id,sacco_id),UNIQUE(sacco_id,client_id),UNIQUE(sacco_id,group_id,kind),
 FOREIGN KEY(client_id,sacco_id) REFERENCES members(id,sacco_id) ON DELETE CASCADE,
 FOREIGN KEY(group_id,sacco_id,group_station_id) REFERENCES lending_group(id,sacco_id,station_id)
);
CREATE TRIGGER loan_borrower_party_immutable BEFORE UPDATE ON loan_borrower_party FOR EACH ROW EXECUTE FUNCTION gl_protect_evidence();
INSERT INTO loan_borrower_party(id,sacco_id,kind,client_id)
 SELECT DISTINCT a.applicant_member_id,a.sacco_id,'CLIENT',a.applicant_member_id FROM loan_applications a;
ALTER TABLE loan_applications ADD COLUMN borrower_party_id uuid;
-- Only new identity metadata is populated. Financial terms, statuses, balances and approvals are untouched.
UPDATE loan_applications SET borrower_party_id=applicant_member_id;
-- Run existing deferred financial checks before further table DDL; no triggers are disabled.
SET CONSTRAINTS ALL IMMEDIATE;
ALTER TABLE loan_applications ALTER COLUMN borrower_party_id SET NOT NULL;
ALTER TABLE loan_applications ADD CONSTRAINT fk_loan_borrower_party FOREIGN KEY(borrower_party_id,sacco_id) REFERENCES loan_borrower_party(id,sacco_id);
CREATE INDEX ix_loan_borrower_party_scope ON loan_applications(sacco_id,station_id,borrower_party_id,id);
CREATE FUNCTION loan_borrower_identity_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE party loan_borrower_party;
BEGIN
 IF TG_OP='UPDATE' AND ROW(NEW.borrower_party_id,NEW.applicant_member_id,NEW.sacco_id)
   IS DISTINCT FROM ROW(OLD.borrower_party_id,OLD.applicant_member_id,OLD.sacco_id)
 THEN RAISE EXCEPTION 'Loan borrower identity is immutable'; END IF;
 IF TG_OP='UPDATE' THEN RETURN NEW; END IF;
 IF NEW.borrower_party_id IS NULL THEN NEW.borrower_party_id=NEW.applicant_member_id; END IF;
 IF NEW.applicant_member_id IS NOT NULL THEN
  INSERT INTO loan_borrower_party(id,sacco_id,kind,client_id)
   VALUES(NEW.applicant_member_id,NEW.sacco_id,'CLIENT',NEW.applicant_member_id) ON CONFLICT(id) DO NOTHING;
 END IF;
 SELECT * INTO party FROM loan_borrower_party WHERE id=NEW.borrower_party_id AND sacco_id=NEW.sacco_id;
 IF NOT FOUND OR party.kind<>'CLIENT' OR NEW.applicant_member_id IS DISTINCT FROM party.client_id
 THEN RAISE EXCEPTION 'Group borrower origination is not enabled until its contract and ledger paths are ready'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER loan_borrower_identity_guard BEFORE INSERT OR UPDATE ON loan_applications FOR EACH ROW EXECUTE FUNCTION loan_borrower_identity_guard();
CREATE FUNCTION loan_ledger_borrower_guard() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF TG_OP='UPDATE' THEN
  IF ROW(NEW.loan_application_id,NEW.sacco_id,NEW.station_id,NEW.applicant_member_id)
    IS DISTINCT FROM ROW(OLD.loan_application_id,OLD.sacco_id,OLD.station_id,OLD.applicant_member_id)
  THEN RAISE EXCEPTION 'Ledger borrower identity is inherited from its loan'; END IF;
  RETURN NEW;
 END IF;
 IF NOT EXISTS(SELECT 1 FROM loan_applications a JOIN loan_borrower_party p ON p.id=a.borrower_party_id AND p.sacco_id=a.sacco_id
   WHERE a.id=NEW.loan_application_id AND ROW(a.sacco_id,a.station_id,a.applicant_member_id)
     IS NOT DISTINCT FROM ROW(NEW.sacco_id,NEW.station_id,NEW.applicant_member_id) AND p.kind='CLIENT' AND p.client_id=NEW.applicant_member_id)
 THEN RAISE EXCEPTION 'Ledger borrower scope must agree with its loan'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER loan_ledger_borrower_guard BEFORE INSERT OR UPDATE ON loan_ledgers FOR EACH ROW EXECUTE FUNCTION loan_ledger_borrower_guard();
CREATE VIEW loan_borrower_directory AS
 SELECT p.id party_id,p.sacco_id,p.kind,p.client_id,p.group_id,p.group_station_id,
  CASE WHEN p.kind='CLIENT' THEN m.full_name ELSE g.name END full_name,
  CASE WHEN p.kind='CLIENT' THEN m.member_no ELSE g.code END member_no
 FROM loan_borrower_party p
 LEFT JOIN members m ON m.id=p.client_id AND m.sacco_id=p.sacco_id
 LEFT JOIN lending_group g ON g.id=p.group_id AND g.sacco_id=p.sacco_id AND g.station_id=p.group_station_id;
