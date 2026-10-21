-- Pending accounts do not yet exist in gl_account. Keep their original version evidence
-- until both account approvals have produced effective posting accounts.
CREATE TABLE loan_product_account_provision (
 product_id uuid PRIMARY KEY REFERENCES loan_product_settings(id),
 sacco_id varchar(255) NOT NULL REFERENCES registered_saccos(sacco_id),
 station_id varchar(255) NOT NULL,
 principal_account_id uuid NOT NULL UNIQUE,
 interest_account_id uuid NOT NULL UNIQUE,
 principal_version_id uuid NOT NULL REFERENCES gl_account_version(id),
 interest_version_id uuid NOT NULL REFERENCES gl_account_version(id),
 created_by uuid NOT NULL REFERENCES members(id),
 created_at timestamptz NOT NULL,
 CHECK(principal_account_id<>interest_account_id)
);
CREATE TRIGGER loan_product_account_provision_immutable BEFORE UPDATE OR DELETE ON loan_product_account_provision
 FOR EACH ROW EXECUTE FUNCTION gl_protect_evidence();

CREATE FUNCTION link_generated_product_accounts(product uuid) RETURNS void LANGUAGE plpgsql AS $$
DECLARE link loan_product_account_provision; changed integer;
BEGIN
 SELECT * INTO link FROM loan_product_account_provision WHERE product_id=product;
 IF NOT FOUND THEN RETURN; END IF;
 UPDATE loan_product_settings p
 SET principal_gl_account_id=link.principal_account_id,interest_gl_account_id=link.interest_account_id
 WHERE p.id=product AND p.sacco_id=link.sacco_id
  AND p.principal_gl_account_id IS NULL AND p.interest_gl_account_id IS NULL
  AND EXISTS(SELECT 1 FROM gl_account a JOIN gl_account_version v ON v.id=a.current_version_id
   WHERE a.id=link.principal_account_id AND a.sacco_id=link.sacco_id AND a.active AND a.kind='POSTING'
    AND v.sacco_id=a.sacco_id AND v.account_id=a.id AND v.state='APPROVED')
  AND EXISTS(SELECT 1 FROM gl_account a JOIN gl_account_version v ON v.id=a.current_version_id
   WHERE a.id=link.interest_account_id AND a.sacco_id=link.sacco_id AND a.active AND a.kind='POSTING'
    AND v.sacco_id=a.sacco_id AND v.account_id=a.id AND v.state='APPROVED');
 GET DIAGNOSTICS changed=ROW_COUNT;
 IF changed=1 THEN
  INSERT INTO audit_log(id,entity_type,entity_id,action,event_status,sacco_id,station_id,action_description,reference_type,reference_value,after_state,created_at)
  VALUES(gen_random_uuid(),'ACCOUNTING',product,'LOAN_PRODUCT_GENERATED_ACCOUNTS_LINKED','SUCCESS',link.sacco_id,link.station_id,
   'Approved generated accounts linked to loan product','LOAN_PRODUCT',product::text,
   jsonb_build_object('principal',link.principal_account_id,'interest',link.interest_account_id),now());
 END IF;
END $$;

CREATE FUNCTION register_generated_product_accounts() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM loan_product_settings p JOIN gl_account_version a ON a.id=NEW.principal_version_id
  JOIN gl_account_version i ON i.id=NEW.interest_version_id
  WHERE p.id=NEW.product_id AND p.sacco_id=NEW.sacco_id AND a.sacco_id=NEW.sacco_id AND i.sacco_id=NEW.sacco_id
   AND a.account_id=NEW.principal_account_id AND i.account_id=NEW.interest_account_id
   AND a.operation='CREATE' AND i.operation='CREATE' AND a.creator_id=NEW.created_by AND i.creator_id=NEW.created_by
   AND a.station_id=NEW.station_id AND i.station_id=NEW.station_id
   AND a.snapshot->>'kind'='POSTING' AND i.snapshot->>'kind'='POSTING')
 THEN RAISE EXCEPTION 'Generated product accounts must retain scoped creation versions'; END IF;
 PERFORM link_generated_product_accounts(NEW.product_id);
 RETURN NULL;
END $$;
CREATE TRIGGER generated_product_accounts_registered AFTER INSERT ON loan_product_account_provision
 FOR EACH ROW EXECUTE FUNCTION register_generated_product_accounts();

CREATE FUNCTION generated_product_account_approved() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE product uuid;
BEGIN
 FOR product IN SELECT product_id FROM loan_product_account_provision
  WHERE sacco_id=NEW.sacco_id AND (principal_account_id=NEW.id OR interest_account_id=NEW.id)
 LOOP PERFORM link_generated_product_accounts(product); END LOOP;
 RETURN NULL;
END $$;
CREATE TRIGGER generated_product_account_effective AFTER INSERT OR UPDATE OF current_version_id ON gl_account
 FOR EACH ROW EXECUTE FUNCTION generated_product_account_approved();

