-- Preserve V52's applied checksum; correct the trigger in a forward migration.
-- The two trigger tables expose different NEW fields, so select the field
-- in separate PL/pgSQL branches rather than one SQL expression.
CREATE OR REPLACE FUNCTION gl_template_balance_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE t uuid;
BEGIN
 IF TG_TABLE_NAME='gl_template_version' THEN t:=NEW.id; ELSE t:=NEW.template_id; END IF;
 IF (SELECT count(*) FROM gl_template_line WHERE template_id=t) NOT BETWEEN 2 AND 10
    OR EXISTS(SELECT 1 FROM gl_template_line WHERE template_id=t GROUP BY component HAVING count(*)<>2 OR count(DISTINCT account_id)<>2)
    OR EXISTS(SELECT 1 FROM gl_template_line WHERE template_id=t AND component='TOTAL') AND (SELECT count(DISTINCT component) FROM gl_template_line WHERE template_id=t)>1
    OR NOT EXISTS(SELECT 1 FROM gl_template_version v JOIN gl_transaction_code c ON c.id=v.transaction_id AND c.sacco_id=v.sacco_id WHERE v.id=t AND c.source_event=v.source_event)
 THEN RAISE EXCEPTION 'Template must balance independently for each amount component'; END IF;
 RETURN NULL;
END $$;
