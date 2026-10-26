-- The existing funding journal and ledger remain authoritative. Lender execution is part of funding.
CREATE TABLE lending_group_org_funding (
 cycle_id uuid PRIMARY KEY REFERENCES lending_group_org_agreement(cycle_id),
 loan_id uuid NOT NULL UNIQUE,sacco_id varchar(255) NOT NULL,station_id varchar(255) NOT NULL,
 posting_id uuid NOT NULL UNIQUE,journal_id uuid NOT NULL UNIQUE REFERENCES gl_journal(id),packet_hash varchar(64) NOT NULL,
 actor_id uuid NOT NULL REFERENCES members(id),actor_name varchar(255) NOT NULL,actor_number varchar(255) NOT NULL,
 signature_text text NOT NULL CHECK(length(trim(signature_text))>0),signed_at timestamptz NOT NULL,
 FOREIGN KEY(loan_id,sacco_id,station_id) REFERENCES loan_applications(id,sacco_id,station_id)
);
CREATE TRIGGER group_org_funding_immutable BEFORE UPDATE OR DELETE ON lending_group_org_funding FOR EACH ROW EXECUTE FUNCTION gl_protect_evidence();
CREATE FUNCTION lending_group_org_funding_insert() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE p lending_group_org_agreement; q lending_group_org_payout; m members; j gl_journal;
BEGIN
 SELECT * INTO p FROM lending_group_org_agreement WHERE cycle_id=NEW.cycle_id;
 SELECT * INTO q FROM lending_group_org_payout WHERE cycle_id=NEW.cycle_id;
 SELECT * INTO m FROM members WHERE id=NEW.actor_id;
 SELECT * INTO j FROM gl_journal WHERE id=NEW.journal_id;
 IF p.cycle_id IS NULL OR q.cycle_id IS NULL THEN RAISE EXCEPTION 'Complete organization agreement and payout required'; END IF;
 PERFORM lending_group_org_payout_scope(NEW.cycle_id,q.client_id,NEW.sacco_id,NEW.station_id,NEW.signed_at);
 IF ROW(NEW.loan_id,NEW.sacco_id,NEW.station_id,NEW.packet_hash) IS DISTINCT FROM ROW(p.loan_id,p.sacco_id,p.station_id,p.packet_hash)
  OR NEW.signed_at<q.authorized_at OR NEW.signed_at<(SELECT max(signed_at) FROM lending_group_org_signature WHERE cycle_id=NEW.cycle_id)
  OR ROW(m.sacco_id,m.station_id,m.status) IS DISTINCT FROM ROW(NEW.sacco_id,NEW.station_id,'ACTIVE'::varchar)
  OR NEW.actor_name IS DISTINCT FROM m.full_name OR NEW.actor_number IS DISTINCT FROM coalesce(m.staff_no,m.member_no)
  OR NEW.signature_text IS DISTINCT FROM m.signature_text
  OR j.id IS NULL OR ROW(j.sacco_id,j.station_id,j.maker_id) IS DISTINCT FROM ROW(NEW.sacco_id,NEW.station_id,NEW.actor_id)
  OR j.state<>'DRAFT' OR j.effective_date<(q.authorized_at AT TIME ZONE 'Africa/Dar_es_Salaam')::date
  OR j.effective_date<(SELECT max((signed_at AT TIME ZONE 'Africa/Dar_es_Salaam')::date) FROM lending_group_org_signature WHERE cycle_id=NEW.cycle_id)
  OR p.snapshot->'financial'->>'tenureUnit' NOT IN ('MONTHS','YEARS')
 THEN RAISE EXCEPTION 'Lender execution must bind the current signed agreement, payout and funding journal'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER group_org_funding_insert BEFORE INSERT ON lending_group_org_funding FOR EACH ROW EXECUTE FUNCTION lending_group_org_funding_insert();
CREATE FUNCTION lending_group_org_funding_commit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF NOT EXISTS(SELECT 1 FROM accountant_loan_post t JOIN gl_journal j ON j.id=t.journal_id
   JOIN loan_ledgers b ON b.loan_application_id=t.loan_id JOIN loan_applications l ON l.id=t.loan_id
   JOIN lending_group_org_payout q ON q.cycle_id=NEW.cycle_id
   JOIN lending_group_org_agreement p ON p.cycle_id=NEW.cycle_id
   WHERE t.id=NEW.posting_id AND t.loan_id=NEW.loan_id AND t.journal_id=NEW.journal_id AND t.kind='DISBURSEMENT'
    AND t.sacco_id=NEW.sacco_id AND t.station_id=NEW.station_id AND t.actor_id=NEW.actor_id AND t.state='POSTED' AND j.state='POSTED'
    AND b.applicant_member_id IS NULL AND b.principal=q.principal AND t.amount=q.principal
    AND l.first_repayment_date=(p.snapshot->'loan'->>'firstPaymentDate')::date
    AND l.payment_details_snapshot=q.destination AND l.disbursement_date=t.effective_date)
 THEN RAISE EXCEPTION 'Organization execution, ledger and posted funding must commit together'; END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER group_org_funding_commit AFTER INSERT ON lending_group_org_funding DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION lending_group_org_funding_commit();
CREATE OR REPLACE FUNCTION lending_group_organization_funding_guard() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF NEW.applicant_member_id IS NULL AND NEW.status IN ('DISBURSED','PAR','DEFAULTED','WRITTEN_OFF','PAID','DISBURSEMENT_REVERSED')
  AND NOT EXISTS(SELECT 1 FROM lending_group_org_funding f WHERE f.loan_id=NEW.id AND f.sacco_id=NEW.sacco_id AND f.station_id=NEW.station_id)
 THEN RAISE EXCEPTION 'Organization lender execution and posted payout are required'; END IF;
 RETURN NEW;
END $$;
ALTER TABLE loan_ledgers ALTER COLUMN applicant_member_id DROP NOT NULL;
CREATE OR REPLACE FUNCTION loan_ledger_borrower_guard() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF TG_OP='UPDATE' THEN
  IF ROW(NEW.loan_application_id,NEW.sacco_id,NEW.station_id,NEW.applicant_member_id)
    IS DISTINCT FROM ROW(OLD.loan_application_id,OLD.sacco_id,OLD.station_id,OLD.applicant_member_id)
  THEN RAISE EXCEPTION 'Ledger borrower identity is inherited from its loan'; END IF;
  RETURN NEW;
 END IF;
 IF NOT EXISTS(SELECT 1 FROM loan_applications a JOIN loan_borrower_party p ON p.id=a.borrower_party_id AND p.sacco_id=a.sacco_id
   WHERE a.id=NEW.loan_application_id AND ROW(a.sacco_id,a.station_id,a.applicant_member_id)
     IS NOT DISTINCT FROM ROW(NEW.sacco_id,NEW.station_id,NEW.applicant_member_id)
   AND ((p.kind='CLIENT' AND p.client_id=NEW.applicant_member_id)
     OR (p.kind='ORGANIZATION' AND NEW.applicant_member_id IS NULL AND p.group_station_id=NEW.station_id
       AND EXISTS(SELECT 1 FROM lending_group_org_funding f WHERE f.loan_id=a.id AND f.sacco_id=a.sacco_id AND f.station_id=a.station_id))))
 THEN RAISE EXCEPTION 'Ledger borrower scope must agree with its loan'; END IF;
 RETURN NEW;
END $$;

-- A personal receipt cannot adopt an organization loan through SQL NULL comparison.
DO $$ DECLARE definition text; BEGIN
 SELECT pg_get_functiondef('accountant_loan_receipt_commit()'::regprocedure) INTO definition;
 IF strpos(definition,'a.applicant_member_id<>r.client_id')=0 THEN RAISE EXCEPTION 'Receipt borrower guard marker unavailable'; END IF;
 EXECUTE replace(definition,'a.applicant_member_id<>r.client_id','a.applicant_member_id IS DISTINCT FROM r.client_id');
END $$;
