-- Resolve the fixed family from the customized chart using its actual parent relationship.
CREATE VIEW gl_cash_bank_group AS
 SELECT a.id,a.sacco_id
 FROM gl_account a
 JOIN gl_account_reporting_section s ON s.account_id=a.parent_id AND s.sacco_id=a.sacco_id
 WHERE s.section='CURRENT_ASSETS' AND a.code='111000' AND a.active
   AND a.kind='HEADING' AND a.type='ASSET' AND a.normal_balance='DEBIT';

-- Fixed structural headings use the existing system baseline under either approval policy.
CREATE OR REPLACE FUNCTION gl_account_baseline() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE vid uuid:=gen_random_uuid();
        fixed_heading boolean;
BEGIN
 IF NEW.current_version_id IS NULL THEN
  fixed_heading := NEW.kind='HEADING' AND (
    (NEW.parent_id IS NULL AND NEW.code IN ('100000','200000','300000','400000','500000'))
    OR (NEW.code IN ('110000','120000','210000','220000') AND EXISTS(
      SELECT 1 FROM gl_account p WHERE p.id=NEW.parent_id AND p.sacco_id=NEW.sacco_id
        AND p.kind='HEADING' AND p.parent_id IS NULL
        AND ((NEW.type='ASSET' AND p.code='100000') OR (NEW.type='LIABILITY' AND p.code='200000'))))
    OR EXISTS(SELECT 1 FROM gl_cash_bank_group g WHERE g.id=NEW.id AND g.sacco_id=NEW.sacco_id)
  );
  IF NOT fixed_heading AND EXISTS(
    SELECT 1 FROM gl_account_approval_policy WHERE sacco_id=NEW.sacco_id
      AND version=(SELECT max(version) FROM gl_account_approval_policy WHERE sacco_id=NEW.sacco_id)
      AND NOT create_automatic
  ) THEN RAISE EXCEPTION 'Account creation requires an approved version'; END IF;

  INSERT INTO gl_account_version(id,sacco_id,account_id,number,operation,state,snapshot,creator_id,reason,mode,created_at,request_key,request_hash)
   VALUES(vid,NEW.sacco_id,NEW.id,1,'BASELINE','BASELINE',gl_account_snapshot(NEW),NEW.maker_id,'Provisioned account baseline','BASELINE',now(),gen_random_uuid(),repeat('0',64));
  UPDATE gl_account SET current_version_id=vid WHERE id=NEW.id;
 END IF;
 RETURN NULL;
END $$;
