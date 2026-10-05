CREATE OR REPLACE FUNCTION cash_flow_validate_insert() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE j gl_journal; a cash_flow_allocations;
BEGIN
 IF TG_TABLE_NAME='cash_flow_allocations' THEN
  SELECT * INTO j FROM gl_journal WHERE id=NEW.journal_id AND sacco_id=NEW.sacco_id AND station_id=NEW.station_id;
  IF j.state IS DISTINCT FROM 'POSTED' OR j.source_type='OPENING' THEN RAISE EXCEPTION 'Posted period source required'; END IF;
  IF NOT EXISTS(SELECT 1 FROM members WHERE id=NEW.made_by AND sacco_id=NEW.sacco_id AND station_id=NEW.station_id) THEN RAISE EXCEPTION 'Cash flow maker scope invalid'; END IF;
  IF NEW.source_json::jsonb->>'journalId' IS DISTINCT FROM j.id::text OR NEW.source_json::jsonb->>'policyId' IS DISTINCT FROM j.policy_id::text OR (NEW.source_json::jsonb->>'policyVersion')::integer IS DISTINCT FROM j.policy_version THEN RAISE EXCEPTION 'Cash flow source identifiers invalid'; END IF;
  IF encode(sha256(convert_to(NEW.source_json,'UTF8')),'hex')<>NEW.source_checksum OR encode(sha256(convert_to(NEW.definition_json,'UTF8')),'hex')<>NEW.definition_checksum THEN RAISE EXCEPTION 'Cash flow checksum invalid'; END IF;
 ELSE
  SELECT * INTO a FROM cash_flow_allocations WHERE id=NEW.allocation_id;
  IF NEW.checker_id=a.made_by OR NEW.reviewed_at<a.made_at THEN RAISE EXCEPTION 'Independent cash flow reviewer required'; END IF;
  IF NOT EXISTS(SELECT 1 FROM members WHERE id=NEW.checker_id AND sacco_id=NEW.sacco_id AND station_id=NEW.station_id) THEN RAISE EXCEPTION 'Cash flow reviewer scope invalid'; END IF;
 END IF;
 RETURN NEW;
END; $$;
