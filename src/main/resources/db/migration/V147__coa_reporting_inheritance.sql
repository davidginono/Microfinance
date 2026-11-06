-- Reporting settings belong to immutable account versions, not an independently editable mapping registry.
-- No existing account, journal, submitted version or frozen report is rewritten.
CREATE TABLE gl_account_reporting_section (
 account_id uuid PRIMARY KEY, sacco_id varchar(255) NOT NULL, section varchar(32) NOT NULL,
 UNIQUE(sacco_id,section),
 FOREIGN KEY(account_id,sacco_id) REFERENCES gl_account(id,sacco_id),
 CHECK(section IN ('CURRENT_ASSETS','NON_CURRENT_ASSETS','CURRENT_LIABILITIES','NON_CURRENT_LIABILITIES'))
);
CREATE TRIGGER gl_account_reporting_section_immutable BEFORE UPDATE OR DELETE ON gl_account_reporting_section
 FOR EACH ROW EXECUTE FUNCTION gl_protect_evidence();
CREATE FUNCTION gl_reporting_section_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE a gl_account; p gl_account;
BEGIN
 SELECT * INTO STRICT a FROM gl_account WHERE id=NEW.account_id AND sacco_id=NEW.sacco_id;
 SELECT * INTO STRICT p FROM gl_account WHERE id=a.parent_id AND sacco_id=a.sacco_id;
 IF a.kind<>'HEADING' OR NOT a.active OR p.kind<>'HEADING' OR p.parent_id IS NOT NULL OR
  a.code<>(CASE NEW.section WHEN 'CURRENT_ASSETS' THEN '110000' WHEN 'NON_CURRENT_ASSETS' THEN '120000'
                           WHEN 'CURRENT_LIABILITIES' THEN '210000' ELSE '220000' END) OR
  NOT ((a.type='ASSET' AND p.type='ASSET' AND NEW.section IN ('CURRENT_ASSETS','NON_CURRENT_ASSETS')) OR
       (a.type='LIABILITY' AND p.type='LIABILITY' AND NEW.section IN ('CURRENT_LIABILITIES','NON_CURRENT_LIABILITIES')))
 THEN RAISE EXCEPTION 'Invalid reporting section group'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER gl_reporting_section_insert BEFORE INSERT ON gl_account_reporting_section FOR EACH ROW EXECUTE FUNCTION gl_reporting_section_guard();
-- Existing compatible customized groups become usable immediately, before another chart visit.
-- This binds structural roles only; no account, version, balance or journal is modified.
INSERT INTO gl_account_reporting_section(account_id,sacco_id,section)
 SELECT a.id,a.sacco_id,CASE a.code WHEN '110000' THEN 'CURRENT_ASSETS' WHEN '120000' THEN 'NON_CURRENT_ASSETS'
                                  WHEN '210000' THEN 'CURRENT_LIABILITIES' ELSE 'NON_CURRENT_LIABILITIES' END
 FROM gl_account a JOIN gl_account p ON p.id=a.parent_id AND p.sacco_id=a.sacco_id
 WHERE a.active AND p.active AND a.kind='HEADING' AND p.kind='HEADING' AND p.parent_id IS NULL
   AND ((a.code IN ('110000','120000') AND a.type='ASSET' AND a.normal_balance='DEBIT' AND p.code='100000' AND p.type='ASSET' AND p.normal_balance='DEBIT')
     OR (a.code IN ('210000','220000') AND a.type='LIABILITY' AND a.normal_balance='CREDIT' AND p.code='200000' AND p.type='LIABILITY' AND p.normal_balance='CREDIT'));
-- Optional reporting facts exist only in the current approved snapshot. Old snapshots omit the key.
CREATE OR REPLACE FUNCTION gl_account_snapshot(a gl_account) RETURNS jsonb LANGUAGE sql STABLE AS $$
 SELECT jsonb_build_object('id',a.id,'code',a.code,'name',a.name,'nameSw',a.name_sw,'description',a.description,
  'type',a.type,'normalBalance',a.normal_balance,'kind',a.kind,'purpose',a.purpose,'parentId',a.parent_id,'active',a.active)
  || coalesce((SELECT jsonb_build_object('reporting',v.snapshot->'reporting') FROM gl_account_version v
       WHERE v.id=a.current_version_id AND v.sacco_id=a.sacco_id AND v.account_id=a.id AND v.snapshot ? 'reporting'),'{}'::jsonb)
$$;
CREATE OR REPLACE FUNCTION gl_account_baseline() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE vid uuid:=gen_random_uuid();
BEGIN
 IF NEW.current_version_id IS NULL THEN
  IF NOT (NEW.kind='HEADING' AND ((NEW.parent_id IS NULL AND NEW.code IN ('100000','200000','300000','400000','500000')) OR (NEW.code IN ('110000','120000','210000','220000') AND EXISTS(SELECT 1 FROM gl_account p WHERE p.id=NEW.parent_id AND p.sacco_id=NEW.sacco_id AND p.kind='HEADING' AND p.parent_id IS NULL AND ((NEW.type='ASSET' AND p.code='100000') OR (NEW.type='LIABILITY' AND p.code='200000')))))) AND EXISTS(SELECT 1 FROM gl_account_approval_policy WHERE sacco_id=NEW.sacco_id AND version=(SELECT max(version) FROM gl_account_approval_policy WHERE sacco_id=NEW.sacco_id) AND NOT create_automatic)
   THEN RAISE EXCEPTION 'Account creation requires an approved version'; END IF;
  INSERT INTO gl_account_version(id,sacco_id,account_id,number,operation,state,snapshot,creator_id,reason,mode,created_at,request_key,request_hash)
   VALUES(vid,NEW.sacco_id,NEW.id,1,'BASELINE','BASELINE',gl_account_snapshot(NEW),NEW.maker_id,'Provisioned account baseline','BASELINE',now(),gen_random_uuid(),repeat('0',64));
  UPDATE gl_account SET current_version_id=vid WHERE id=NEW.id;
 END IF;
 RETURN NULL;
END $$;
