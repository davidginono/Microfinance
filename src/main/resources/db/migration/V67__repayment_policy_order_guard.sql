-- Tanzania Mainland Tier 2 policies retain the statutory component priority.
ALTER TABLE repayment_accounting_policy
    ADD CONSTRAINT repayment_policy_statutory_order CHECK (allocation_order='INTEREST_FIRST') NOT VALID;
-- Retain immutable historical snapshots; the service also rejects a nonstatutory effective policy.

-- Preserve ordinary library deletion semantics while protecting system definitions.
CREATE OR REPLACE FUNCTION protect_repayment_definitions() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF OLD.system_key IS NOT NULL OR (TG_OP='UPDATE' AND NEW.system_key IS DISTINCT FROM OLD.system_key)
 THEN RAISE EXCEPTION 'System repayment definitions cannot be edited or removed'; END IF;
 RETURN CASE WHEN TG_OP='DELETE' THEN OLD ELSE NEW END;
END $$;
