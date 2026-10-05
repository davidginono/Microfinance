-- Institution aggregates retain trusted D bytes and minimal per-branch lineage.
CREATE TABLE financial_statement_institution_sources (
 id uuid PRIMARY KEY,sacco_id varchar(255) NOT NULL REFERENCES registered_saccos(sacco_id),
 period_id uuid NOT NULL REFERENCES accounting_period(id),source_json text NOT NULL CHECK(octet_length(source_json)<=8388608),
 checksum char(64) NOT NULL,branches_json text NOT NULL CHECK(length(branches_json)<=1000000),
 made_by uuid NOT NULL REFERENCES members(id),made_at timestamptz NOT NULL,
 UNIQUE(id,sacco_id),UNIQUE(sacco_id,period_id,checksum),
 CHECK(checksum=encode(sha256(convert_to(source_json,'UTF8')),'hex'))
);
CREATE TRIGGER financial_institution_source_immutable BEFORE UPDATE OR DELETE ON financial_statement_institution_sources FOR EACH ROW EXECUTE FUNCTION protect_financial_statement_evidence();
ALTER TABLE financial_statement_results ADD COLUMN dimension varchar(20) NOT NULL DEFAULT 'BRANCH' CHECK(dimension IN('BRANCH','INSTITUTION'));
ALTER TABLE financial_statement_results ADD COLUMN institution_source_id uuid;
ALTER TABLE financial_statement_results ADD COLUMN comparison_institution_source_id uuid;
ALTER TABLE financial_statement_results ALTER COLUMN close_review_id DROP NOT NULL;
ALTER TABLE financial_statement_results ADD UNIQUE(id,sacco_id);
DO $$ DECLARE c record; BEGIN
 FOR c IN SELECT conname FROM pg_constraint WHERE conrelid='financial_statement_results'::regclass AND contype='f' AND pg_get_constraintdef(oid) LIKE 'FOREIGN KEY (prior_result_id,%'
 LOOP EXECUTE format('ALTER TABLE financial_statement_results DROP CONSTRAINT %I',c.conname); END LOOP;
END $$;
ALTER TABLE financial_statement_results ADD FOREIGN KEY(prior_result_id,sacco_id) REFERENCES financial_statement_results(id,sacco_id);
ALTER TABLE financial_statement_results ADD FOREIGN KEY(institution_source_id,sacco_id) REFERENCES financial_statement_institution_sources(id,sacco_id);
ALTER TABLE financial_statement_results ADD FOREIGN KEY(comparison_institution_source_id,sacco_id) REFERENCES financial_statement_institution_sources(id,sacco_id);
ALTER TABLE financial_statement_results ADD CHECK((dimension='BRANCH' AND close_review_id IS NOT NULL AND institution_source_id IS NULL AND comparison_institution_source_id IS NULL) OR (dimension='INSTITUTION' AND close_review_id IS NULL AND comparison_close_id IS NULL AND institution_source_id IS NOT NULL));
DO $$ DECLARE c record; BEGIN
 FOR c IN SELECT conname FROM pg_constraint WHERE conrelid='financial_statement_results'::regclass AND contype='u' AND pg_get_constraintdef(oid) LIKE '%version_id, close_review_id, comparison_close_id%'
 LOOP EXECUTE format('ALTER TABLE financial_statement_results DROP CONSTRAINT %I',c.conname); END LOOP;
END $$;
CREATE UNIQUE INDEX ux_financial_branch_result ON financial_statement_results(sacco_id,station_id,version_id,close_review_id,comparison_close_id) NULLS NOT DISTINCT WHERE dimension='BRANCH';
CREATE UNIQUE INDEX ux_financial_institution_result ON financial_statement_results(sacco_id,version_id,institution_source_id,comparison_institution_source_id) NULLS NOT DISTINCT WHERE dimension='INSTITUTION';
CREATE INDEX ix_financial_institution_results ON financial_statement_results(sacco_id,made_at DESC,id) WHERE dimension='INSTITUTION';
CREATE INDEX ix_financial_closed_period_choices ON accounting_period(sacco_id,ends_on DESC,id) WHERE state='CLOSED';

CREATE FUNCTION check_financial_institution_source() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE p accounting_period; b jsonb; r accounting_close_review;
BEGIN
 SELECT * INTO p FROM accounting_period WHERE id=NEW.period_id AND sacco_id=NEW.sacco_id FOR SHARE;
 IF NOT FOUND OR p.state<>'CLOSED' OR NEW.source_json::jsonb->>'dimension' IS DISTINCT FROM 'INSTITUTION' OR NEW.source_json::jsonb->>'institution' IS DISTINCT FROM NEW.sacco_id
 OR NEW.source_json::jsonb->>'period' IS DISTINCT FROM NEW.period_id::text OR NEW.source_json::jsonb->>'from' IS DISTINCT FROM p.starts_on::text OR NEW.source_json::jsonb->>'through' IS DISTINCT FROM p.ends_on::text
 OR NEW.source_json::jsonb->'branchSources' IS DISTINCT FROM NEW.branches_json::jsonb OR jsonb_typeof(NEW.branches_json::jsonb) IS DISTINCT FROM 'array' OR jsonb_array_length(NEW.branches_json::jsonb) NOT BETWEEN 1 AND 1000
 THEN RAISE EXCEPTION 'Closed scoped institution source and branch lineage required'; END IF;
 FOR b IN SELECT value FROM jsonb_array_elements(NEW.branches_json::jsonb) LOOP
  SELECT * INTO r FROM accounting_close_review WHERE id=(b->>'reviewId')::uuid AND sacco_id=NEW.sacco_id AND station_id=b->>'branch' AND period_id=NEW.period_id AND action='CLOSE';
  IF NOT FOUND OR r.version<>(b->>'version')::integer OR r.checksum IS DISTINCT FROM b->>'checksum'
  OR r.recorded_at IS DISTINCT FROM (b->>'recordedCutoff')::timestamptz
  OR NOT EXISTS(SELECT 1 FROM accounting_close_decision d WHERE d.review_id=r.id AND d.checker_id=(b->>'reviewer')::uuid AND d.checker_id<>r.maker_id)
  OR r.recorded_at<=coalesce((SELECT max(d.decided_at) FROM accounting_close_review x JOIN accounting_close_decision d ON d.review_id=x.id WHERE x.sacco_id=NEW.sacco_id AND x.period_id=NEW.period_id AND x.action='REOPEN'),'-infinity')
  OR EXISTS(SELECT 1 FROM accounting_close_review x JOIN accounting_close_decision d ON d.review_id=x.id WHERE x.sacco_id=NEW.sacco_id AND x.station_id=r.station_id AND x.period_id=r.period_id AND x.version>r.version)
  THEN RAISE EXCEPTION 'Latest independent branch close lineage required'; END IF;
 END LOOP;
 RETURN NEW;
END $$;
CREATE TRIGGER financial_institution_source_check BEFORE INSERT ON financial_statement_institution_sources FOR EACH ROW EXECUTE FUNCTION check_financial_institution_source();

CREATE OR REPLACE FUNCTION check_financial_statement_source() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE source_review accounting_close_review; source_period accounting_period; source_id uuid; s financial_statement_institution_sources;
BEGIN
 IF NOT EXISTS(SELECT 1 FROM financial_statement_versions WHERE id=NEW.version_id AND sacco_id=NEW.sacco_id AND state='APPROVED')
 THEN RAISE EXCEPTION 'Independently approved statement mapping required'; END IF;
 IF NEW.result_json::jsonb->>'status' IS DISTINCT FROM 'FINAL' OR NEW.result_json::jsonb->>'institution' IS DISTINCT FROM NEW.sacco_id OR NEW.result_json::jsonb->>'branch' IS DISTINCT FROM NEW.station_id
 OR NEW.result_json::jsonb->>'versionId' IS DISTINCT FROM NEW.version_id::text OR NEW.result_json::jsonb->>'priorResultId' IS DISTINCT FROM NEW.prior_result_id::text OR NEW.checksum<>encode(sha256(convert_to(NEW.result_json,'UTF8')),'hex')
 THEN RAISE EXCEPTION 'Statement result metadata and checksum must agree'; END IF;
 IF NEW.dimension='INSTITUTION' THEN
  SELECT * INTO s FROM financial_statement_institution_sources WHERE id=NEW.institution_source_id AND sacco_id=NEW.sacco_id;
  IF NOT FOUND OR NEW.result_json::jsonb->>'dimension' IS DISTINCT FROM 'INSTITUTION' OR NEW.result_json::jsonb->>'closeReviewId' IS NOT NULL OR NEW.result_json::jsonb->>'comparisonCloseId' IS NOT NULL OR NEW.result_json::jsonb->>'institutionPeriodId' IS DISTINCT FROM s.period_id::text
  OR NEW.result_json::jsonb->>'institutionSourceChecksum' IS DISTINCT FROM s.checksum OR NEW.result_json::jsonb->'currentBranchSources' IS DISTINCT FROM s.branches_json::jsonb
  THEN RAISE EXCEPTION 'Institution result must pin retained aggregate and lineage'; END IF;
  SELECT * INTO source_period FROM accounting_period WHERE id=s.period_id AND sacco_id=NEW.sacco_id FOR SHARE;
  IF source_period.state<>'CLOSED' THEN RAISE EXCEPTION 'Closed institution period required'; END IF;
  IF NEW.comparison_institution_source_id IS NOT NULL THEN
   SELECT * INTO s FROM financial_statement_institution_sources WHERE id=NEW.comparison_institution_source_id AND sacco_id=NEW.sacco_id;
   IF NEW.result_json::jsonb->>'comparisonInstitutionPeriodId' IS DISTINCT FROM s.period_id::text OR NEW.result_json::jsonb->>'comparisonInstitutionSourceChecksum' IS DISTINCT FROM s.checksum OR NEW.result_json::jsonb->'comparisonBranchSources' IS DISTINCT FROM s.branches_json::jsonb THEN RAISE EXCEPTION 'Comparative institution lineage must agree'; END IF;
   SELECT * INTO source_period FROM accounting_period WHERE id=s.period_id AND sacco_id=NEW.sacco_id FOR SHARE;
   IF source_period.state<>'CLOSED' THEN RAISE EXCEPTION 'Closed comparative institution period required'; END IF;
  END IF;
  RETURN NEW;
 END IF;
 FOR source_id IN SELECT NEW.close_review_id UNION SELECT NEW.comparison_close_id WHERE NEW.comparison_close_id IS NOT NULL LOOP
  SELECT * INTO source_review FROM accounting_close_review WHERE id=source_id AND sacco_id=NEW.sacco_id AND station_id=NEW.station_id AND action='CLOSE';
  IF NOT FOUND OR NOT EXISTS(SELECT 1 FROM accounting_close_decision WHERE review_id=source_id) THEN RAISE EXCEPTION 'Scoped independently reviewed close required'; END IF;
  IF EXISTS(SELECT 1 FROM accounting_close_review r JOIN accounting_close_decision d ON d.review_id=r.id WHERE r.period_id=source_review.period_id AND r.sacco_id=NEW.sacco_id AND r.station_id=NEW.station_id AND r.version>source_review.version) THEN RAISE EXCEPTION 'Latest approved close required for publication'; END IF;
  SELECT * INTO source_period FROM accounting_period WHERE id=source_review.period_id AND sacco_id=NEW.sacco_id FOR SHARE;
  IF source_period.state<>'CLOSED' THEN RAISE EXCEPTION 'Closed period required for final statement'; END IF;
 END LOOP;
 IF coalesce(NEW.result_json::jsonb->>'dimension','BRANCH')<>'BRANCH' OR NEW.result_json::jsonb->>'closeReviewId' IS DISTINCT FROM NEW.close_review_id::text OR NEW.result_json::jsonb->>'closeChecksum' IS DISTINCT FROM (SELECT checksum FROM accounting_close_review WHERE id=NEW.close_review_id)
 THEN RAISE EXCEPTION 'Branch result must pin its reviewed close'; END IF;
 RETURN NEW;
END $$;
