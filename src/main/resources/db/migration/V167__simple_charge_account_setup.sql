-- New mappings require real scoped accounts, without policy/agreement/licence
-- references. Existing immutable policies, contracts and postings are retained.
DO $$
DECLARE item record; removed integer:=0;
BEGIN
 FOR item IN SELECT conname FROM pg_constraint
  WHERE conrelid='loan_charge_policy'::regclass AND contype='c'
   AND (pg_get_constraintdef(oid) LIKE '%policy_reference%'
        OR pg_get_constraintdef(oid) LIKE '%insurer_verification%')
 LOOP
  EXECUTE format('ALTER TABLE loan_charge_policy DROP CONSTRAINT %I',item.conname);
  removed:=removed+1;
 END LOOP;
 IF removed<>2 THEN RAISE EXCEPTION 'Expected two charge reference constraints'; END IF;
END $$;
ALTER TABLE loan_charge_policy
 ADD CONSTRAINT loan_charge_policy_receivable_required CHECK(NOT enabled OR receivable_account_id IS NOT NULL),
 ADD CONSTRAINT loan_charge_policy_insurer_required CHECK(insurance_account_id IS NULL OR length(trim(insurer))>0);

-- Append a completed setup on the same day; never overwrite the disabled row.
ALTER TABLE loan_charge_policy ADD COLUMN revision_no bigint GENERATED ALWAYS AS IDENTITY;
ALTER TABLE loan_charge_policy DROP CONSTRAINT loan_charge_policy_sacco_id_product_id_effective_from_key;
CREATE INDEX loan_charge_policy_effective_revision
 ON loan_charge_policy(sacco_id,product_id,effective_from DESC,revision_no DESC);

DO $$
DECLARE definition text; updated text;
BEGIN
 SELECT pg_get_functiondef('loan_charge_terms_guard()'::regprocedure) INTO definition;
 updated:=replace(definition,'AND length(trim(p.insurer_verification))>0','AND length(trim(p.insurer))>0');
 IF updated=definition THEN RAISE EXCEPTION 'Expected insurer verification guard'; END IF;
 EXECUTE updated;
END $$;
