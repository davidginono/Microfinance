-- Group contracts, guarantees, collections and corrections retain their permanent audit trail.
-- A future approved financial-record lifecycle must explicitly supersede this protection.
CREATE TRIGGER group_lending_audit_immutable BEFORE UPDATE OR DELETE ON audit_log
 FOR EACH ROW WHEN (OLD.entity_type='GROUP_LENDING') EXECUTE FUNCTION gl_protect_evidence();
