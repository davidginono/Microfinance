-- Optional client subledgers for ordinary vouchers; historical documents remain unchanged.
ALTER TABLE accounting_voucher ADD COLUMN client_id uuid REFERENCES members(id);
ALTER TABLE accounting_voucher ADD COLUMN client_number varchar(255);
ALTER TABLE accounting_voucher ADD CONSTRAINT voucher_client_identity CHECK
 ((client_id IS NULL AND client_number IS NULL) OR
  (client_id IS NOT NULL AND client_number IS NOT NULL AND length(trim(client_number))>0));
CREATE FUNCTION validate_voucher_client() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE original accounting_voucher;
BEGIN
 IF NEW.reverses_id IS NOT NULL THEN
  SELECT * INTO original FROM accounting_voucher WHERE id=NEW.reverses_id
   AND sacco_id=NEW.sacco_id AND station_id=NEW.station_id;
  IF NOT FOUND OR ROW(NEW.client_id,NEW.client_number) IS DISTINCT FROM ROW(original.client_id,original.client_number) THEN
   RAISE EXCEPTION 'Voucher corrections must preserve the recorded client subledger';
  END IF;
 ELSIF NEW.client_id IS NOT NULL AND NOT EXISTS(
  SELECT 1 FROM members WHERE id=NEW.client_id AND sacco_id=NEW.sacco_id AND station_id=NEW.station_id
   AND is_member AND status='ACTIVE' AND member_no=NEW.client_number
 ) THEN RAISE EXCEPTION 'An active client in the voucher branch is required';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER voucher_client_scope BEFORE INSERT ON accounting_voucher FOR EACH ROW EXECUTE FUNCTION validate_voucher_client();
