-- Cohort writes share the owning GL setup boundary. Retain financial branch history.
CREATE FUNCTION accounting_branch_cohort_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE old_scope text; new_scope text; retained boolean;
BEGIN
 IF TG_OP='UPDATE' AND NEW.sacco_id IS NOT DISTINCT FROM OLD.sacco_id
    AND NEW.station_id IS NOT DISTINCT FROM OLD.station_id
    AND NEW.active IS NOT DISTINCT FROM OLD.active
    AND NEW.access_status IS NOT DISTINCT FROM OLD.access_status THEN RETURN NEW; END IF;
 IF TG_OP<>'INSERT' THEN old_scope:=OLD.sacco_id; END IF;
 IF TG_OP<>'DELETE' THEN new_scope:=NEW.sacco_id; END IF;
 -- Deterministic ordering also protects an unreferenced branch identifier move.
 IF old_scope IS NOT NULL AND new_scope IS NOT NULL AND old_scope<>new_scope THEN
   PERFORM pg_advisory_xact_lock(hashtextextended('GL_SETUP/'||least(old_scope,new_scope),0));
   PERFORM pg_advisory_xact_lock(hashtextextended('GL_SETUP/'||greatest(old_scope,new_scope),0));
 ELSE
   PERFORM pg_advisory_xact_lock(hashtextextended('GL_SETUP/'||coalesce(old_scope,new_scope),0));
 END IF;
 IF TG_OP='DELETE' OR (TG_OP='UPDATE' AND (NEW.sacco_id<>OLD.sacco_id OR NEW.station_id<>OLD.station_id)) THEN
   retained:=EXISTS(SELECT 1 FROM gl_journal WHERE sacco_id=OLD.sacco_id AND station_id=OLD.station_id)
     OR EXISTS(SELECT 1 FROM reconciliation_statement WHERE sacco_id=OLD.sacco_id AND station_id=OLD.station_id)
     OR EXISTS(SELECT 1 FROM accounting_close_review WHERE sacco_id=OLD.sacco_id AND station_id=OLD.station_id)
     OR EXISTS(SELECT 1 FROM loan_ledgers WHERE sacco_id=OLD.sacco_id AND station_id=OLD.station_id);
   IF retained THEN RAISE EXCEPTION 'Retained financial branch identity cannot be changed or deleted'; END IF;
 END IF;
 IF TG_OP='DELETE' THEN RETURN OLD; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER accounting_branch_cohort_guard BEFORE INSERT OR UPDATE OR DELETE ON sacco_stations
 FOR EACH ROW EXECUTE FUNCTION accounting_branch_cohort_guard();
