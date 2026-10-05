-- Retain full bounded institution/comparative financial provenance without truncation.
-- Existing immutable output bytes and checksums remain unchanged.
ALTER TABLE statement_output_sets DROP CONSTRAINT statement_output_sets_result_json_check;
ALTER TABLE statement_output_sets ADD CONSTRAINT statement_output_sets_result_json_check CHECK (octet_length(result_json) BETWEEN 1 AND 8388608);

-- Immutable materialized summary fields prevent page reads from parsing large evidence payloads.
ALTER TABLE statement_output_sets
 ADD COLUMN result_dimension text GENERATED ALWAYS AS (coalesce(result_json::jsonb->>'dimension','BRANCH')) STORED,
 ADD COLUMN title_en text GENERATED ALWAYS AS (result_json::jsonb#>>'{definition,titleEn}') STORED,
 ADD COLUMN title_sw text GENERATED ALWAYS AS (result_json::jsonb#>>'{definition,titleSw}') STORED,
 ADD COLUMN starts_on_iso text GENERATED ALWAYS AS (result_json::jsonb->>'from') STORED,
 ADD COLUMN ends_on_iso text GENERATED ALWAYS AS (result_json::jsonb->>'through') STORED;
CREATE INDEX ix_statement_outputs_scope_dimension_page ON statement_output_sets(sacco_id,station_id,result_dimension,generated_at DESC,id);
