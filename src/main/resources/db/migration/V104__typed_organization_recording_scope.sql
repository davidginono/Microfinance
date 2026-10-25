-- Retain the individual recording checks and admit only a real, scoped organization identity.
-- The deferred organization source guard still requires its facility, authority and frozen payout.
CREATE OR REPLACE FUNCTION accountant_loan_record_scope() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF NOT EXISTS(
  SELECT 1 FROM loan_applications a
  JOIN loan_borrower_party b ON b.id=a.borrower_party_id AND b.sacco_id=a.sacco_id
  LEFT JOIN members m ON m.id=b.client_id
  LEFT JOIN lending_group g ON g.id=b.group_id AND g.sacco_id=b.sacco_id AND g.station_id=b.group_station_id
  JOIN members u ON u.id=NEW.actor_id
  WHERE a.id=NEW.loan_id AND a.sacco_id=NEW.sacco_id AND a.station_id=NEW.station_id AND a.status='RECORDED'
    AND u.sacco_id=NEW.sacco_id AND u.station_id=NEW.station_id
    AND ((b.kind='CLIENT' AND m.id=a.applicant_member_id AND m.sacco_id=NEW.sacco_id AND m.station_id=NEW.station_id AND m.is_member)
      OR (b.kind='ORGANIZATION' AND a.applicant_member_id IS NULL AND b.group_station_id=NEW.station_id AND g.legal_form='ORGANIZATION' AND g.status='ACTIVE'))
 ) THEN RAISE EXCEPTION 'Loan recording scope must agree'; END IF;
 RETURN NEW;
END $$;
