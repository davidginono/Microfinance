-- Correct generated account numbering without rewriting approved snapshots or journal lines.
-- Structural headings are system baselines; posting-account approval remains institution-owned.
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
        OR (NEW.code='511000' AND p.code='510000' AND NEW.type='INCOME')))
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
        proposed jsonb; reason_text varchar := 'COA number correction (V150); original approvals retained';
BEGIN
 FOR r IN
  SELECT g.sacco_id,g.created_by,g.station_id,x.account_id,x.principal
  FROM loan_product_account_provision g
  CROSS JOIN LATERAL (VALUES(g.principal_account_id,true),(g.interest_account_id,false)) x(account_id,principal)
  WHERE EXISTS(SELECT 1 FROM gl_account_version v WHERE v.account_id=x.account_id AND v.sacco_id=g.sacco_id AND v.snapshot->>'code' ~ '^LP-[A-F0-9]{32}-[PI]$')
  ORDER BY g.sacco_id,g.created_at,g.product_id,x.principal DESC
 LOOP
  SELECT v.* INTO STRICT old FROM gl_account_version v
   WHERE v.sacco_id=r.sacco_id AND v.account_id=r.account_id AND v.operation IN ('CREATE','BASELINE')
   ORDER BY v.number DESC LIMIT 1 FOR UPDATE;
  -- Rejected/withdrawn creations get a fresh number when explicitly revised.
  IF NOT EXISTS(SELECT 1 FROM gl_account WHERE id=r.account_id AND sacco_id=r.sacco_id)
    AND old.state NOT IN ('DRAFT','PENDING') THEN CONTINUE; END IF;
  IF EXISTS(SELECT 1 FROM gl_account WHERE id=r.account_id AND sacco_id=r.sacco_id) THEN
   SELECT v.* INTO STRICT old FROM gl_account a JOIN gl_account_version v ON v.id=a.current_version_id
    WHERE a.sacco_id=r.sacco_id AND a.id=r.account_id;
  END IF;
  IF old.snapshot->>'code' !~ '^LP-[A-F0-9]{32}-[PI]$' THEN CONTINUE; END IF;
  p:=pg_temp.loan_number_group(r.sacco_id,r.created_by,CASE WHEN r.principal THEN '100000' ELSE '500000' END,
     CASE WHEN r.principal THEN '130000' ELSE '510000' END,CASE WHEN r.principal THEN 'Loans' ELSE 'Loan income' END,
     CASE WHEN r.principal THEN 'Mikopo' ELSE 'Mapato ya mikopo' END);
  family:=pg_temp.loan_number_group(r.sacco_id,r.created_by,CASE WHEN r.principal THEN '130000' ELSE '510000' END,
     CASE WHEN r.principal THEN '131000' ELSE '511000' END,CASE WHEN r.principal THEN 'Loan principal' ELSE 'Loan interest' END,
     CASE WHEN r.principal THEN 'Mtaji wa mkopo' ELSE 'Riba ya mkopo' END);
  SELECT n::text INTO number_code FROM generate_series(CASE WHEN r.principal THEN 131001 ELSE 511001 END,CASE WHEN r.principal THEN 131999 ELSE 511999 END) n
   WHERE NOT EXISTS(SELECT 1 FROM gl_account WHERE sacco_id=r.sacco_id AND code=n::text)
     AND NOT EXISTS(SELECT 1 FROM gl_account_version WHERE sacco_id=r.sacco_id AND operation='CREATE' AND state IN ('DRAFT','PENDING') AND snapshot->>'code'=n::text)
   ORDER BY n LIMIT 1;
  IF number_code IS NULL THEN RAISE EXCEPTION 'No available loan account number in %',r.sacco_id; END IF;
  vid:=gen_random_uuid();proposed:=old.snapshot||jsonb_build_object('code',number_code,'parentId',family);
  IF old.state IN ('DRAFT','PENDING') THEN
   UPDATE gl_account_version SET state='WITHDRAWN',revision=revision+1,decided_at=now(),decision_reason='System numbering correction; replacement '||vid WHERE id=old.id;
   INSERT INTO gl_account_version_event(id,version_id,sacco_id,action,actor_id,station_id,reason,state,policy_version,mode,revision,before_snapshot,snapshot,recorded_at)
    SELECT gen_random_uuid(),id,sacco_id,'SYSTEM_NUMBER_SUPERSEDED',creator_id,station_id,decision_reason,state,policy_version,mode,revision,before_snapshot,snapshot,now() FROM gl_account_version WHERE id=old.id;
   INSERT INTO gl_account_version(id,sacco_id,account_id,number,operation,state,snapshot,creator_id,station_id,reason,policy_version,mode,created_at,submitted_at,request_key,request_hash)
    VALUES(vid,r.sacco_id,r.account_id,(SELECT max(number)+1 FROM gl_account_version WHERE sacco_id=r.sacco_id AND account_id=r.account_id),'CREATE',old.state,proposed,old.creator_id,old.station_id,reason_text,old.policy_version,old.mode,now(),CASE WHEN old.state='PENDING' THEN now() END,gen_random_uuid(),repeat('0',64));
  ELSE
   INSERT INTO gl_account_version(id,sacco_id,account_id,number,base_id,operation,state,before_snapshot,snapshot,creator_id,station_id,reason,mode,created_at,request_key,request_hash)
    VALUES(vid,r.sacco_id,r.account_id,(SELECT max(number)+1 FROM gl_account_version WHERE sacco_id=r.sacco_id AND account_id=r.account_id),old.id,'BASELINE','BASELINE',old.snapshot,proposed,old.creator_id,old.station_id,reason_text,'BASELINE',now(),gen_random_uuid(),repeat('0',64));
   UPDATE gl_account SET code=number_code,parent_id=family,current_version_id=vid WHERE id=r.account_id AND sacco_id=r.sacco_id;
   -- Rebase an open metadata/lifecycle proposal without changing its submitted evidence
   -- or making an approval decision on behalf of its reviewer.
   SELECT * INTO pending FROM gl_account_version WHERE sacco_id=r.sacco_id AND account_id=r.account_id AND state IN ('DRAFT','PENDING') FOR UPDATE;
   IF FOUND THEN
    replacement:=gen_random_uuid();
    UPDATE gl_account_version SET state='WITHDRAWN',revision=revision+1,decided_at=now(),decision_reason='System numbering correction; replacement '||replacement WHERE id=pending.id;
    INSERT INTO gl_account_version_event(id,version_id,sacco_id,action,actor_id,station_id,reason,state,policy_version,mode,revision,before_snapshot,snapshot,recorded_at)
     SELECT gen_random_uuid(),id,sacco_id,'SYSTEM_NUMBER_SUPERSEDED',creator_id,station_id,decision_reason,state,policy_version,mode,revision,before_snapshot,snapshot,now() FROM gl_account_version WHERE id=pending.id;
    INSERT INTO gl_account_version(id,sacco_id,account_id,number,base_id,operation,state,before_snapshot,snapshot,creator_id,station_id,reason,policy_version,mode,created_at,submitted_at,request_key,request_hash)
     VALUES(replacement,r.sacco_id,r.account_id,(SELECT max(number)+1 FROM gl_account_version WHERE sacco_id=r.sacco_id AND account_id=r.account_id),vid,pending.operation,pending.state,proposed,pending.snapshot||jsonb_build_object('code',number_code,'parentId',family),pending.creator_id,pending.station_id,pending.reason,pending.policy_version,pending.mode,now(),CASE WHEN pending.state='PENDING' THEN now() END,gen_random_uuid(),repeat('0',64));
    INSERT INTO gl_account_version_event(id,version_id,sacco_id,action,actor_id,station_id,reason,state,policy_version,mode,revision,before_snapshot,snapshot,recorded_at)
     SELECT gen_random_uuid(),id,sacco_id,'SYSTEM_NUMBER_CORRECTED',creator_id,station_id,reason,state,policy_version,mode,revision,before_snapshot,snapshot,now() FROM gl_account_version WHERE id=replacement;
   END IF;
  END IF;
  INSERT INTO gl_account_version_event(id,version_id,sacco_id,action,actor_id,station_id,reason,state,policy_version,mode,revision,before_snapshot,snapshot,recorded_at)
   SELECT gen_random_uuid(),id,sacco_id,'SYSTEM_NUMBER_CORRECTED',creator_id,coalesce(station_id,r.station_id),reason,state,policy_version,mode,revision,before_snapshot,snapshot,now() FROM gl_account_version WHERE id=vid;
  INSERT INTO audit_log(id,entity_type,entity_id,action,event_status,sacco_id,station_id,action_description,reference_type,reference_value,before_state,after_state,created_at)
   VALUES(gen_random_uuid(),'ACCOUNT',r.account_id,'LOAN_ACCOUNT_NUMBER_CORRECTED','SUCCESS',r.sacco_id,r.station_id,reason_text,'ACCOUNT',number_code,
    jsonb_build_object('version',old.id,'account',old.snapshot),jsonb_build_object('version',vid,'account',proposed),now());
 END LOOP;
END $$;
ALTER TABLE gl_account ENABLE TRIGGER gl_account_protection;
ALTER TABLE gl_account ENABLE TRIGGER gl_account_version_commit;
DROP FUNCTION pg_temp.loan_number_group(varchar,uuid,varchar,varchar,varchar,varchar);

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
      AND (v.state='APPROVED' OR (v.state='BASELINE' AND v.reason='COA number correction (V150); original approvals retained'
        AND EXISTS(SELECT 1 FROM gl_account_version original WHERE original.id=pair.version_id AND original.state='APPROVED'))))
  );
 GET DIAGNOSTICS changed=ROW_COUNT;
 IF changed=1 THEN
  INSERT INTO audit_log(id,entity_type,entity_id,action,event_status,sacco_id,station_id,action_description,reference_type,reference_value,after_state,created_at)
   VALUES(gen_random_uuid(),'ACCOUNTING',product,'LOAN_PRODUCT_GENERATED_ACCOUNTS_LINKED','SUCCESS',link.sacco_id,link.station_id,'Approved generated accounts linked to loan product','LOAN_PRODUCT',product::text,jsonb_build_object('principal',link.principal_account_id,'interest',link.interest_account_id),now());
 END IF;
END $$;
