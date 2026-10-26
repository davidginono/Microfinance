-- Existing immutable envelopes retain their original client ID. New envelopes identify a typed borrower.
ALTER TABLE accountant_loan_receipt ADD COLUMN borrower_party_id uuid;
ALTER TABLE accountant_loan_receipt ADD COLUMN payer_name varchar(160) NOT NULL DEFAULT '';
ALTER TABLE accountant_loan_receipt ALTER COLUMN client_id DROP NOT NULL;
ALTER TABLE accountant_loan_receipt ADD CONSTRAINT fk_receipt_borrower FOREIGN KEY(borrower_party_id,sacco_id) REFERENCES loan_borrower_party(id,sacco_id);
CREATE FUNCTION accountant_loan_receipt_borrower_guard() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF NOT EXISTS(SELECT 1 FROM loan_borrower_party p WHERE p.id=coalesce(NEW.borrower_party_id,NEW.client_id) AND p.sacco_id=NEW.sacco_id
   AND ((p.kind='CLIENT' AND p.client_id=NEW.client_id)
     OR (p.kind='ORGANIZATION' AND NEW.client_id IS NULL AND p.group_station_id=NEW.station_id AND length(trim(NEW.payer_name))>0)))
 THEN RAISE EXCEPTION 'Receipt must identify its scoped borrower without personalizing organization debt'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER loan_receipt_borrower_guard BEFORE INSERT ON accountant_loan_receipt FOR EACH ROW EXECUTE FUNCTION accountant_loan_receipt_borrower_guard();
DO $$ DECLARE definition text; BEGIN
 SELECT pg_get_functiondef('accountant_loan_receipt_commit()'::regprocedure) INTO definition;
 IF strpos(definition,'a.applicant_member_id IS DISTINCT FROM r.client_id')=0
   OR strpos(definition,'original.client_id<>r.client_id')=0
 THEN RAISE EXCEPTION 'Receipt borrower conservation marker unavailable'; END IF;
 definition=replace(definition,'a.applicant_member_id IS DISTINCT FROM r.client_id','a.borrower_party_id IS DISTINCT FROM coalesce(r.borrower_party_id,r.client_id)');
 definition=replace(definition,'original.client_id<>r.client_id','coalesce(original.borrower_party_id,original.client_id) IS DISTINCT FROM coalesce(r.borrower_party_id,r.client_id)');
 definition=replace(definition,'OR original.transaction_count<>cnt','OR original.payer_name IS DISTINCT FROM r.payer_name OR original.transaction_count<>cnt');
 EXECUTE definition;
END $$;
