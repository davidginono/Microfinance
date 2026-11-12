-- Generated principal placement and versioned fee mappings. No journal or frozen contract is rewritten.
-- Apply with a verified deployment backup. Existing charge policies remain effective until staff save a new revision.
CREATE OR REPLACE FUNCTION gl_account_baseline() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE vid uuid:=gen_random_uuid(); fixed_heading boolean;
BEGIN
 IF NEW.current_version_id IS NULL THEN
  fixed_heading := NEW.kind='HEADING' AND (
    (NEW.parent_id IS NULL AND NEW.code IN ('100000','200000','300000','400000','500000'))
    OR (NEW.code IN ('110000','120000','210000','220000') AND EXISTS(
      SELECT 1 FROM gl_account p WHERE p.id=NEW.parent_id AND p.sacco_id=NEW.sacco_id
        AND p.kind='HEADING' AND p.parent_id IS NULL
        AND ((NEW.type='ASSET' AND p.code='100000') OR (NEW.type='LIABILITY' AND p.code='200000'))))
    OR EXISTS(SELECT 1 FROM gl_cash_bank_group g WHERE g.id=NEW.id AND g.sacco_id=NEW.sacco_id)
    OR EXISTS(SELECT 1 FROM gl_account p WHERE p.id=NEW.parent_id AND p.sacco_id=NEW.sacco_id
      AND p.active AND p.kind='HEADING' AND p.type=NEW.type AND p.normal_balance=NEW.normal_balance
      AND ((NEW.code='130000' AND p.code='100000' AND NEW.type='ASSET')
        OR (NEW.code='131000' AND p.code='130000' AND NEW.type='ASSET')
        OR (NEW.code='510000' AND p.code='500000' AND NEW.type='INCOME')
        OR (NEW.code IN ('511000','512000','513000') AND p.code='510000' AND NEW.type='INCOME')
        OR (NEW.code='112000' AND p.code='110000' AND NEW.type='ASSET')))
  );
  IF NOT fixed_heading AND EXISTS(SELECT 1 FROM gl_account_approval_policy WHERE sacco_id=NEW.sacco_id
      AND version=(SELECT max(version) FROM gl_account_approval_policy WHERE sacco_id=NEW.sacco_id)
      AND NOT create_automatic)
  THEN RAISE EXCEPTION 'Account creation requires an approved version'; END IF;
  INSERT INTO gl_account_version(id,sacco_id,account_id,number,operation,state,snapshot,creator_id,reason,mode,created_at,request_key,request_hash)
   VALUES(vid,NEW.sacco_id,NEW.id,1,'BASELINE','BASELINE',gl_account_snapshot(NEW),NEW.maker_id,'Provisioned account baseline','BASELINE',now(),gen_random_uuid(),repeat('0',64));
  UPDATE gl_account SET current_version_id=vid WHERE id=NEW.id;
 END IF;
 RETURN NULL;
END $$;

CREATE OR REPLACE FUNCTION pg_temp.loan_number_group(institution varchar, maker uuid, parent_code varchar, group_code varchar, group_name varchar, group_sw varchar) RETURNS uuid LANGUAGE plpgsql AS $$
DECLARE p gl_account; a gl_account;
BEGIN
 SELECT * INTO STRICT p FROM gl_account WHERE sacco_id=institution AND code=parent_code;
 IF NOT p.active OR p.kind<>'HEADING' THEN RAISE EXCEPTION 'Unavailable loan account parent % in %',parent_code,institution; END IF;
 SELECT * INTO a FROM gl_account WHERE sacco_id=institution AND code=group_code;
 IF NOT FOUND THEN
  IF EXISTS(SELECT 1 FROM gl_account_version WHERE sacco_id=institution AND operation='CREATE' AND state IN ('DRAFT','PENDING') AND snapshot->>'code'=group_code)
  THEN RAISE EXCEPTION 'Loan account group % has a pending version in %',group_code,institution; END IF;
  INSERT INTO gl_account(id,sacco_id,code,name,name_sw,type,normal_balance,kind,purpose,parent_id,maker_id,created_at)
   VALUES(gen_random_uuid(),institution,group_code,group_name,group_sw,p.type,p.normal_balance,'HEADING','OTHER',p.id,maker,now()) RETURNING * INTO a;
  INSERT INTO audit_log(id,entity_type,entity_id,action,event_status,sacco_id,action_description,reference_type,reference_value,after_state,created_at)
   VALUES(gen_random_uuid(),'ACCOUNT',a.id,'ACCOUNT_PROVISIONED','SUCCESS',institution,'System loan account heading (V150)','ACCOUNT',group_code,gl_account_snapshot(a),now());
 END IF;
 IF NOT a.active OR a.kind<>'HEADING' OR a.type<>p.type OR a.normal_balance<>p.normal_balance OR a.parent_id IS DISTINCT FROM p.id
 THEN RAISE EXCEPTION 'Incompatible loan account group % in %',group_code,institution; END IF;
 RETURN a.id;
END $$;

-- These two protections are suspended only inside this transactional, one-time migration.
-- Version and journal evidence protections remain enabled throughout.
ALTER TABLE gl_account DISABLE TRIGGER gl_account_protection;
ALTER TABLE gl_account DISABLE TRIGGER gl_account_version_commit;
DO $$
DECLARE r record; p uuid; family uuid; number_code varchar; vid uuid; old gl_account_version;
        pending gl_account_version; replacement uuid;
        proposed jsonb; reason_text varchar := 'Current Assets correction (V156); original approvals retained';
BEGIN
 FOR r IN
  SELECT g.sacco_id,g.created_by,g.station_id,g.principal_account_id account_id,true principal
  FROM loan_product_account_provision g
  WHERE EXISTS(SELECT 1 FROM gl_account_version v WHERE v.account_id=g.principal_account_id AND v.sacco_id=g.sacco_id
    AND (v.snapshot->>'code' ~ '^131[0-9]{3}$' OR v.snapshot->>'code' ~ '^LP-[A-F0-9]{32}-P$'))
  ORDER BY g.sacco_id,g.created_at,g.product_id
 LOOP
  PERFORM pg_advisory_xact_lock(hashtextextended('GL_SETUP/'||r.sacco_id,0));
  SELECT v.* INTO STRICT old FROM gl_account_version v
   WHERE v.sacco_id=r.sacco_id AND v.account_id=r.account_id AND v.operation IN ('CREATE','BASELINE')
   ORDER BY v.number DESC LIMIT 1;
  -- Rejected/withdrawn creations get a fresh number when explicitly revised.
  IF NOT EXISTS(SELECT 1 FROM gl_account WHERE id=r.account_id AND sacco_id=r.sacco_id)
    AND old.state NOT IN ('DRAFT','PENDING') THEN CONTINUE; END IF;
  IF EXISTS(SELECT 1 FROM gl_account WHERE id=r.account_id AND sacco_id=r.sacco_id) THEN
   SELECT v.* INTO STRICT old FROM gl_account a JOIN gl_account_version v ON v.id=a.current_version_id
    WHERE a.sacco_id=r.sacco_id AND a.id=r.account_id;
  END IF;
  IF old.snapshot->>'code' !~ '^(131[0-9]{3}|LP-[A-F0-9]{32}-P)$' THEN CONTINUE; END IF;
  p:=pg_temp.loan_number_group(r.sacco_id,r.created_by,'100000','110000','Current assets','Mali za muda mfupi');
  INSERT INTO gl_account_reporting_section(account_id,sacco_id,section) VALUES(p,r.sacco_id,'CURRENT_ASSETS') ON CONFLICT DO NOTHING;
  family:=pg_temp.loan_number_group(r.sacco_id,r.created_by,'110000','112000','Loan principal','Mtaji wa mkopo');
  SELECT n::text INTO number_code FROM generate_series(112001,112999) n
   WHERE NOT EXISTS(SELECT 1 FROM gl_account WHERE sacco_id=r.sacco_id AND code=n::text)
     AND NOT EXISTS(SELECT 1 FROM gl_account_version WHERE sacco_id=r.sacco_id AND operation='CREATE' AND state IN ('DRAFT','PENDING') AND snapshot->>'code'=n::text)
   ORDER BY n LIMIT 1;
  IF number_code IS NULL THEN RAISE EXCEPTION 'No available loan account number in %',r.sacco_id; END IF;
  vid:=gen_random_uuid();proposed:=old.snapshot||jsonb_build_object('code',number_code,'parentId',family);
  IF old.state IN ('DRAFT','PENDING') THEN
   UPDATE gl_account_version SET state='WITHDRAWN',revision=revision+1,decided_at=now(),decision_reason='System principal placement correction; replacement '||vid WHERE id=old.id;
   INSERT INTO gl_account_version_event(id,version_id,sacco_id,action,actor_id,station_id,reason,state,policy_version,mode,revision,before_snapshot,snapshot,recorded_at)
    SELECT gen_random_uuid(),id,sacco_id,'SYSTEM_PLACEMENT_SUPERSEDED',creator_id,station_id,decision_reason,state,policy_version,mode,revision,before_snapshot,snapshot,now() FROM gl_account_version WHERE id=old.id;
   INSERT INTO gl_account_version(id,sacco_id,account_id,number,operation,state,snapshot,creator_id,station_id,reason,policy_version,mode,created_at,submitted_at,request_key,request_hash)
    VALUES(vid,r.sacco_id,r.account_id,(SELECT max(number)+1 FROM gl_account_version WHERE sacco_id=r.sacco_id AND account_id=r.account_id),'CREATE',old.state,proposed,old.creator_id,old.station_id,reason_text,old.policy_version,old.mode,now(),CASE WHEN old.state='PENDING' THEN now() END,gen_random_uuid(),repeat('0',64));
  ELSE
   INSERT INTO gl_account_version(id,sacco_id,account_id,number,base_id,operation,state,before_snapshot,snapshot,creator_id,station_id,reason,mode,created_at,request_key,request_hash)
    VALUES(vid,r.sacco_id,r.account_id,(SELECT max(number)+1 FROM gl_account_version WHERE sacco_id=r.sacco_id AND account_id=r.account_id),old.id,'BASELINE','BASELINE',old.snapshot,proposed,old.creator_id,old.station_id,reason_text,'BASELINE',now(),gen_random_uuid(),repeat('0',64));
   UPDATE gl_account SET code=number_code,parent_id=family,current_version_id=vid WHERE id=r.account_id AND sacco_id=r.sacco_id;
   -- Rebase an open metadata/lifecycle proposal without changing its submitted evidence
   -- or making an approval decision on behalf of its reviewer.
   SELECT * INTO pending FROM gl_account_version WHERE sacco_id=r.sacco_id AND account_id=r.account_id AND state IN ('DRAFT','PENDING');
   IF FOUND THEN
    replacement:=gen_random_uuid();
    UPDATE gl_account_version SET state='WITHDRAWN',revision=revision+1,decided_at=now(),decision_reason='System principal placement correction; replacement '||replacement WHERE id=pending.id;
    INSERT INTO gl_account_version_event(id,version_id,sacco_id,action,actor_id,station_id,reason,state,policy_version,mode,revision,before_snapshot,snapshot,recorded_at)
     SELECT gen_random_uuid(),id,sacco_id,'SYSTEM_PLACEMENT_SUPERSEDED',creator_id,station_id,decision_reason,state,policy_version,mode,revision,before_snapshot,snapshot,now() FROM gl_account_version WHERE id=pending.id;
    INSERT INTO gl_account_version(id,sacco_id,account_id,number,base_id,operation,state,before_snapshot,snapshot,creator_id,station_id,reason,policy_version,mode,created_at,submitted_at,request_key,request_hash)
     VALUES(replacement,r.sacco_id,r.account_id,(SELECT max(number)+1 FROM gl_account_version WHERE sacco_id=r.sacco_id AND account_id=r.account_id),vid,pending.operation,pending.state,proposed,pending.snapshot||jsonb_build_object('code',number_code,'parentId',family),pending.creator_id,pending.station_id,pending.reason,pending.policy_version,pending.mode,now(),CASE WHEN pending.state='PENDING' THEN now() END,gen_random_uuid(),repeat('0',64));
    INSERT INTO gl_account_version_event(id,version_id,sacco_id,action,actor_id,station_id,reason,state,policy_version,mode,revision,before_snapshot,snapshot,recorded_at)
     SELECT gen_random_uuid(),id,sacco_id,'SYSTEM_NUMBER_CORRECTED',creator_id,station_id,reason,state,policy_version,mode,revision,before_snapshot,snapshot,now() FROM gl_account_version WHERE id=replacement;
   END IF;
  END IF;
  INSERT INTO gl_account_version_event(id,version_id,sacco_id,action,actor_id,station_id,reason,state,policy_version,mode,revision,before_snapshot,snapshot,recorded_at)
   SELECT gen_random_uuid(),id,sacco_id,'SYSTEM_NUMBER_CORRECTED',creator_id,coalesce(station_id,r.station_id),reason,state,policy_version,mode,revision,before_snapshot,snapshot,now() FROM gl_account_version WHERE id=vid;
  INSERT INTO audit_log(id,entity_type,entity_id,action,event_status,sacco_id,station_id,action_description,reference_type,reference_value,before_state,after_state,created_at)
   VALUES(gen_random_uuid(),'ACCOUNT',r.account_id,'LOAN_PRINCIPAL_PLACEMENT_FIXED','SUCCESS',r.sacco_id,r.station_id,reason_text,'ACCOUNT',number_code,
    jsonb_build_object('version',old.id,'account',old.snapshot),jsonb_build_object('version',vid,'account',proposed),now());
 END LOOP;
END $$;
ALTER TABLE gl_account ENABLE TRIGGER gl_account_protection;
ALTER TABLE gl_account ENABLE TRIGGER gl_account_version_commit;

-- A corrected baseline retains the original, real creation approval in the provision record.
CREATE OR REPLACE FUNCTION link_generated_product_accounts(product uuid) RETURNS void LANGUAGE plpgsql AS $$
DECLARE link loan_product_account_provision; changed integer;
BEGIN
 SELECT * INTO link FROM loan_product_account_provision WHERE product_id=product;
 IF NOT FOUND THEN RETURN; END IF;
 UPDATE loan_product_settings p SET principal_gl_account_id=link.principal_account_id,interest_gl_account_id=link.interest_account_id
 WHERE p.id=product AND p.sacco_id=link.sacco_id AND p.principal_gl_account_id IS NULL AND p.interest_gl_account_id IS NULL
  AND NOT EXISTS(
   SELECT 1 FROM (VALUES(link.principal_account_id,link.principal_version_id),(link.interest_account_id,link.interest_version_id)) pair(account_id,version_id)
   WHERE NOT EXISTS(SELECT 1 FROM gl_account a JOIN gl_account_version v ON v.id=a.current_version_id
    WHERE a.id=pair.account_id AND a.sacco_id=link.sacco_id AND a.active AND a.kind='POSTING' AND v.sacco_id=a.sacco_id AND v.account_id=a.id
      AND (v.state='APPROVED' OR (v.state='BASELINE' AND v.reason IN ('Current Assets correction (V156); original approvals retained','COA number correction (V150); original approvals retained')
        AND EXISTS(SELECT 1 FROM gl_account_version original WHERE original.id=pair.version_id AND original.state='APPROVED'))))
  );
 GET DIAGNOSTICS changed=ROW_COUNT;
 IF changed=1 THEN
  INSERT INTO audit_log(id,entity_type,entity_id,action,event_status,sacco_id,station_id,action_description,reference_type,reference_value,after_state,created_at)
   VALUES(gen_random_uuid(),'ACCOUNTING',product,'LOAN_PRODUCT_GENERATED_ACCOUNTS_LINKED','SUCCESS',link.sacco_id,link.station_id,'Approved generated accounts linked to loan product','LOAN_PRODUCT',product::text,jsonb_build_object('principal',link.principal_account_id,'interest',link.interest_account_id),now());
 END IF;
END $$;

ALTER TABLE loan_charge_policy
 ADD COLUMN accounting_version integer NOT NULL DEFAULT 1 CHECK(accounting_version IN (1,2)),
 ADD COLUMN application_account_id uuid,
 ADD COLUMN processing_account_id uuid,
 ADD FOREIGN KEY(application_account_id,sacco_id) REFERENCES gl_account(id,sacco_id),
 ADD FOREIGN KEY(processing_account_id,sacco_id) REFERENCES gl_account(id,sacco_id),
 ADD CHECK(accounting_version=1 OR management_account_id IS NULL);
CREATE INDEX loan_charge_policy_application_account_scope ON loan_charge_policy(sacco_id,application_account_id);
CREATE INDEX loan_charge_policy_processing_account_scope ON loan_charge_policy(sacco_id,processing_account_id);

CREATE FUNCTION loan_charge_income_policy_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.accounting_version=2 THEN
  IF (NEW.enabled AND (NEW.receivable_account_id IS NULL OR NEW.application_account_id IS NULL OR NEW.processing_account_id IS NULL))
   OR EXISTS(SELECT 1 FROM (VALUES(NEW.application_account_id),(NEW.processing_account_id)) selected(id)
     WHERE selected.id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM gl_account a WHERE a.id=selected.id AND a.sacco_id=NEW.sacco_id
       AND a.active AND a.type='INCOME' AND a.kind='POSTING' AND a.purpose='INCOME'))
  THEN RAISE EXCEPTION 'Fee income policies require active scoped Income posting accounts'; END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER loan_charge_income_policy_scope BEFORE INSERT ON loan_charge_policy
 FOR EACH ROW EXECUTE FUNCTION loan_charge_income_policy_guard();

CREATE TABLE loan_product_fee_account_provision (
 product_id uuid NOT NULL REFERENCES loan_product_settings(id),
 sacco_id varchar(255) NOT NULL REFERENCES registered_saccos(sacco_id),
 station_id varchar(255) NOT NULL,
 component varchar(16) NOT NULL CHECK(component IN ('APPLICATION','PROCESSING')),
 account_id uuid NOT NULL UNIQUE,
 version_id uuid NOT NULL REFERENCES gl_account_version(id),
 created_by uuid NOT NULL REFERENCES members(id),created_at timestamptz NOT NULL,
 PRIMARY KEY(product_id,component)
);
CREATE INDEX loan_product_fee_account_institution ON loan_product_fee_account_provision(sacco_id,product_id);
CREATE TRIGGER loan_product_fee_provision_immutable BEFORE UPDATE OR DELETE ON loan_product_fee_account_provision
 FOR EACH ROW EXECUTE FUNCTION gl_protect_evidence();
CREATE FUNCTION loan_product_fee_provision_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM loan_product_settings p JOIN gl_account_version v ON v.id=NEW.version_id
  WHERE p.id=NEW.product_id AND p.sacco_id=NEW.sacco_id AND v.sacco_id=p.sacco_id AND v.account_id=NEW.account_id
   AND v.creator_id=NEW.created_by AND v.station_id=NEW.station_id AND v.operation='CREATE'
   AND v.snapshot->>'type'='INCOME' AND v.snapshot->>'normalBalance'='CREDIT'
   AND v.snapshot->>'kind'='POSTING' AND v.snapshot->>'purpose'='INCOME')
 THEN RAISE EXCEPTION 'Generated fee accounts require scoped creation evidence'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER loan_product_fee_provision_scope BEFORE INSERT ON loan_product_fee_account_provision
 FOR EACH ROW EXECUTE FUNCTION loan_product_fee_provision_guard();

-- Extend the current guard, including all intervening migration fixes. Fail rather than silently omit a protection.
DO $$ DECLARE definition text; updated text; BEGIN
 SELECT pg_get_functiondef('loan_charge_terms_guard()'::regprocedure) INTO definition;
 updated:=replace(definition,'AND type=''LIABILITY''',
  'AND (type=''LIABILITY'' OR (p.accounting_version=2 AND NEW.kind IN(''APPLICATION'',''PROCESSING'') AND type=''INCOME''))');
 IF updated=definition THEN RAISE EXCEPTION 'Expected loan charge liability guard missing'; END IF;
 definition:=updated;
 updated:=replace(definition,
  '(NEW.kind IN(''APPLICATION'',''PROCESSING'') AND purpose=''CLEARING'' AND id=p.management_account_id)',
  '((NEW.kind IN(''APPLICATION'',''PROCESSING'') AND p.accounting_version=1 AND type=''LIABILITY'' AND purpose=''CLEARING'' AND id=p.management_account_id)
    OR (p.accounting_version=2 AND type=''INCOME'' AND kind=''POSTING'' AND purpose=''INCOME'' AND
       ((NEW.kind=''APPLICATION'' AND id=p.application_account_id) OR (NEW.kind=''PROCESSING'' AND id=p.processing_account_id))))');
 IF updated=definition THEN RAISE EXCEPTION 'Expected loan charge classification guard missing'; END IF;
 EXECUTE updated;
END $$;

CREATE OR REPLACE FUNCTION gl_account_requires_loan_source(account uuid) RETURNS boolean LANGUAGE sql STABLE AS $$
 SELECT coalesce((SELECT a.kind='CONTROL'
  OR a.purpose IN ('LOAN_PRINCIPAL','INTEREST_RECEIVABLE','FEE_RECEIVABLE','ALLOWANCE')
  OR EXISTS(SELECT 1 FROM loan_product_account_provision p WHERE p.sacco_id=a.sacco_id
    AND (p.principal_account_id=a.id OR p.interest_account_id=a.id))
  OR EXISTS(SELECT 1 FROM loan_product_fee_account_provision p WHERE p.sacco_id=a.sacco_id AND p.account_id=a.id)
  OR EXISTS(SELECT 1 FROM loan_charge_policy p WHERE p.sacco_id=a.sacco_id
    AND (p.receivable_account_id=a.id OR p.management_account_id=a.id OR p.insurance_account_id=a.id OR p.application_account_id=a.id OR p.processing_account_id=a.id))
  OR EXISTS(SELECT 1 FROM loan_charge_term t WHERE t.sacco_id=a.sacco_id AND (t.receivable_account_id=a.id OR t.liability_account_id=a.id))
 FROM gl_account a WHERE a.id=account),false)
$$;

-- Backfill definitions using recorded product provisioning provenance, never a fictional staff decision.
-- The current account-creation policy determines automatic versus independent approval.
DO $$
DECLARE r record; component text; family uuid; group_id uuid; aid uuid; vid uuid; number_code text;
        proposed jsonb; automatic boolean; policy integer; version_state text; version_mode text;
BEGIN
 FOR r IN SELECT g.*,coalesce(nullif(p.product_name,''),p.loan_type) product_name FROM loan_product_account_provision g
   JOIN loan_product_settings p ON p.id=g.product_id AND p.sacco_id=g.sacco_id ORDER BY g.sacco_id,g.created_at,g.product_id
 LOOP
  PERFORM pg_advisory_xact_lock(hashtextextended('GL_SETUP/'||r.sacco_id,0));
  group_id:=pg_temp.loan_number_group(r.sacco_id,r.created_by,'500000','510000','Loan income','Mapato ya mikopo');
  SELECT create_automatic,version INTO automatic,policy FROM gl_account_approval_policy WHERE sacco_id=r.sacco_id ORDER BY version DESC LIMIT 1;
  automatic:=coalesce(automatic,true);policy:=coalesce(policy,0);
  version_state:=CASE WHEN automatic THEN 'APPROVED' ELSE 'PENDING' END;
  version_mode:=CASE WHEN automatic THEN 'AUTOMATIC' ELSE 'INDEPENDENT' END;
  FOREACH component IN ARRAY ARRAY['APPLICATION','PROCESSING'] LOOP
   family:=pg_temp.loan_number_group(r.sacco_id,r.created_by,'510000',CASE WHEN component='APPLICATION' THEN '512000' ELSE '513000' END,
     CASE WHEN component='APPLICATION' THEN 'Application fees' ELSE 'Processing fees' END,CASE WHEN component='APPLICATION' THEN 'Ada za maombi' ELSE 'Ada za uchakataji' END);
   SELECT n::text INTO number_code FROM generate_series(CASE WHEN component='APPLICATION' THEN 512001 ELSE 513001 END,CASE WHEN component='APPLICATION' THEN 512999 ELSE 513999 END) n
    WHERE NOT EXISTS(SELECT 1 FROM gl_account WHERE sacco_id=r.sacco_id AND code=n::text)
     AND NOT EXISTS(SELECT 1 FROM gl_account_version WHERE sacco_id=r.sacco_id AND operation='CREATE' AND state IN ('DRAFT','PENDING') AND snapshot->>'code'=n::text)
    ORDER BY n LIMIT 1;
   IF number_code IS NULL THEN RAISE EXCEPTION 'No available fee account number in %',r.sacco_id; END IF;
   aid:=gen_random_uuid();vid:=gen_random_uuid();
   proposed:=jsonb_build_object('id',aid,'code',number_code,'name',left(r.product_name,130)||CASE WHEN component='APPLICATION' THEN ' - Application fee' ELSE ' - Processing fee' END,
     'nameSw',null,'description','Loan product '||r.product_id||' '||lower(component),'type','INCOME','normalBalance','CREDIT','kind','POSTING','purpose','INCOME','parentId',family,'active',true);
   INSERT INTO gl_account_version(id,sacco_id,account_id,number,operation,state,snapshot,creator_id,station_id,reason,policy_version,mode,created_at,submitted_at,decided_at,request_key,request_hash)
    VALUES(vid,r.sacco_id,aid,1,'CREATE',version_state,proposed,r.created_by,r.station_id,'System fee account generation (V156); original product provisioning provenance',policy,version_mode,now(),now(),CASE WHEN automatic THEN now() END,gen_random_uuid(),repeat('0',64));
   INSERT INTO gl_account_version_event(id,version_id,sacco_id,action,actor_id,station_id,reason,state,policy_version,mode,revision,snapshot,recorded_at)
    VALUES(gen_random_uuid(),vid,r.sacco_id,'SYSTEM_FEE_GENERATED',r.created_by,r.station_id,'System generation using product provisioning provenance (V156)',version_state,policy,version_mode,0,proposed,now());
   INSERT INTO loan_product_fee_account_provision VALUES(r.product_id,r.sacco_id,r.station_id,component,aid,vid,r.created_by,now());
   IF automatic THEN
    INSERT INTO gl_account(id,sacco_id,code,name,name_sw,description,type,normal_balance,kind,purpose,parent_id,maker_id,created_at,current_version_id)
     VALUES(aid,r.sacco_id,number_code,proposed->>'name',null,proposed->>'description','INCOME','CREDIT','POSTING','INCOME',family,r.created_by,now(),vid);
   END IF;
   INSERT INTO audit_log(id,entity_type,entity_id,action,event_status,sacco_id,station_id,action_description,reference_type,reference_value,after_state,created_at)
    VALUES(gen_random_uuid(),'ACCOUNT',aid,'PRODUCT_FEE_ACCOUNT_GENERATED','SUCCESS',r.sacco_id,r.station_id,'System generated fee account (V156); no charge policy activated','LOAN_PRODUCT',r.product_id::text,proposed,now());
  END LOOP;
 END LOOP;
END $$;
