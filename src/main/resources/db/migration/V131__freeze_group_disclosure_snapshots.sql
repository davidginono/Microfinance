-- Existing financial guards protect known pricing components; also freeze this new evidence component.
CREATE FUNCTION lending_group_disclosure_snapshot_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.financial_snapshot->'groupDisclosure' IS DISTINCT FROM OLD.financial_snapshot->'groupDisclosure' THEN
  RAISE EXCEPTION 'Group disclosure evidence cannot be rewritten';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER lending_group_disclosure_snapshot_guard BEFORE UPDATE ON loan_applications
 FOR EACH ROW EXECUTE FUNCTION lending_group_disclosure_snapshot_guard();
