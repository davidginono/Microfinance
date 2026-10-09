-- Retain existing history; new repayment corrections require a different accountant.
CREATE FUNCTION accountant_loan_correction_actor_guard() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF NEW.kind='REVERSAL' AND EXISTS(SELECT 1 FROM accountant_loan_post original
   WHERE original.id=NEW.reverses_id AND original.actor_id=NEW.actor_id)
 THEN RAISE EXCEPTION 'A different accountant must record the repayment correction'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER accountant_loan_correction_actor_guard BEFORE INSERT ON accountant_loan_post
 FOR EACH ROW EXECUTE FUNCTION accountant_loan_correction_actor_guard();
