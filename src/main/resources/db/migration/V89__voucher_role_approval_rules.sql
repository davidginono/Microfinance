-- Existing decisions and policy revisions remain unchanged. New routing uses model 2.
ALTER TABLE voucher_workflow ADD COLUMN auto_post boolean NOT NULL DEFAULT false;
ALTER TABLE voucher_workflow ADD COLUMN model_version integer NOT NULL DEFAULT 1;
ALTER TABLE voucher_workflow ADD COLUMN rule_name varchar(120);
ALTER TABLE voucher_workflow_history ADD COLUMN decision_stage integer;
CREATE INDEX ix_voucher_workflow_votes ON voucher_workflow_history(workflow_id,round,decision_stage,actor_id) WHERE action='APPROVE';
CREATE INDEX ix_voucher_workflow_auto_post ON voucher_workflow(updated_at,id)
 WHERE auto_post AND approval='WORKFLOW_APPROVED' AND validation='VALIDATED' AND posting='NOT_POSTED';

-- Pending requests use their recorded revision, even when a newer rule is saved.
CREATE OR REPLACE FUNCTION validate_voucher_workflow_envelope() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE w voucher_workflow;
BEGIN
 SELECT * INTO STRICT w FROM voucher_workflow WHERE id=NEW.id;
 IF w.posting='POSTED' THEN
  IF w.item_count=0 OR w.item_count<>(SELECT count(*) FROM voucher_workflow_item WHERE workflow_id=w.id)
    OR (w.scope='SINGLE' AND w.item_count<>1)
    OR (w.config_version>0 AND NOT EXISTS(SELECT 1 FROM voucher_approval_configuration_history h
       WHERE h.sacco_id=w.sacco_id AND h.station_id=w.station_id AND h.type=w.type AND h.version=w.config_version))
    OR EXISTS(SELECT 1 FROM voucher_workflow_item WHERE workflow_id=w.id AND (voucher_id IS NULL OR plan_hash IS NULL OR issues<>'[]'::jsonb))
    THEN RAISE EXCEPTION 'Every approved voucher must commit together'; END IF;
 END IF;
 RETURN NULL;
END $$;
