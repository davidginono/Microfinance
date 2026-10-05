-- Classifications retain the exact immutable source and never alter posted money.
CREATE TABLE cash_flow_allocations (
 id uuid PRIMARY KEY, sacco_id varchar(255) NOT NULL, station_id varchar(255) NOT NULL,
 journal_id uuid NOT NULL, version integer NOT NULL CHECK(version>0), request_key uuid NOT NULL,
 made_by uuid NOT NULL REFERENCES members(id), made_at timestamptz NOT NULL,
 evidence varchar(1000) NOT NULL CHECK(length(trim(evidence))>0), noncash_evidence varchar(1000),
 source_json text NOT NULL, source_checksum varchar(64) NOT NULL CHECK(source_checksum ~ '^[0-9a-f]{64}$'),
 definition_json text NOT NULL, definition_checksum varchar(64) NOT NULL CHECK(definition_checksum ~ '^[0-9a-f]{64}$'),
 UNIQUE(journal_id,version), UNIQUE(sacco_id,station_id,request_key), UNIQUE(id,sacco_id,station_id),
 FOREIGN KEY(journal_id,sacco_id,station_id) REFERENCES gl_journal(id,sacco_id,station_id)
);
CREATE TABLE cash_flow_allocation_reviews (
 allocation_id uuid PRIMARY KEY, sacco_id varchar(255) NOT NULL, station_id varchar(255) NOT NULL,
 checker_id uuid NOT NULL REFERENCES members(id), evidence varchar(1000) NOT NULL CHECK(length(trim(evidence))>0),
 reviewed_at timestamptz NOT NULL,
 FOREIGN KEY(allocation_id,sacco_id,station_id) REFERENCES cash_flow_allocations(id,sacco_id,station_id)
);
CREATE INDEX ix_cash_allocation_scope_journal ON cash_flow_allocations(sacco_id,station_id,journal_id,version DESC);
CREATE FUNCTION cash_flow_protect_history() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Cash flow allocation evidence is append-only'; END; $$;
CREATE TRIGGER cash_flow_allocation_immutable BEFORE UPDATE OR DELETE ON cash_flow_allocations FOR EACH ROW EXECUTE FUNCTION cash_flow_protect_history();
CREATE TRIGGER cash_flow_review_immutable BEFORE UPDATE OR DELETE ON cash_flow_allocation_reviews FOR EACH ROW EXECUTE FUNCTION cash_flow_protect_history();
CREATE FUNCTION cash_flow_validate_insert() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE j gl_journal; a cash_flow_allocations;
BEGIN
 IF TG_TABLE_NAME='cash_flow_allocations' THEN
  SELECT * INTO j FROM gl_journal WHERE id=NEW.journal_id AND sacco_id=NEW.sacco_id AND station_id=NEW.station_id;
  IF j.state IS DISTINCT FROM 'POSTED' OR j.source_type='OPENING' THEN RAISE EXCEPTION 'Posted period source required'; END IF;
  IF encode(sha256(convert_to(NEW.source_json,'UTF8')),'hex')<>NEW.source_checksum OR encode(sha256(convert_to(NEW.definition_json,'UTF8')),'hex')<>NEW.definition_checksum THEN RAISE EXCEPTION 'Cash flow checksum invalid'; END IF;
 ELSE
  SELECT * INTO a FROM cash_flow_allocations WHERE id=NEW.allocation_id;
  IF NEW.checker_id=a.made_by OR NEW.reviewed_at<a.made_at THEN RAISE EXCEPTION 'Independent cash flow reviewer required'; END IF;
 END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER cash_flow_source_guard BEFORE INSERT ON cash_flow_allocations FOR EACH ROW EXECUTE FUNCTION cash_flow_validate_insert();
CREATE TRIGGER cash_flow_review_guard BEFORE INSERT ON cash_flow_allocation_reviews FOR EACH ROW EXECUTE FUNCTION cash_flow_validate_insert();
