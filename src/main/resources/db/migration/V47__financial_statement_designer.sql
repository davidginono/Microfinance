CREATE TABLE financial_statement_templates (
 id uuid PRIMARY KEY,sacco_id varchar(255) NOT NULL REFERENCES registered_saccos(sacco_id),
 made_by uuid NOT NULL REFERENCES members(id),made_at timestamptz NOT NULL,UNIQUE(id,sacco_id)
);
CREATE TABLE financial_statement_versions (
 id uuid PRIMARY KEY,template_id uuid NOT NULL,sacco_id varchar(255) NOT NULL,
 version integer NOT NULL CHECK(version>0),definition text NOT NULL CHECK(length(definition)<=200000),
 made_by uuid NOT NULL REFERENCES members(id),made_at timestamptz NOT NULL,
 evidence varchar(1000) NOT NULL,state varchar(20) NOT NULL DEFAULT 'DRAFT' CHECK(state IN('DRAFT','APPROVED','RETIRED')),
 checked_by uuid REFERENCES members(id),checked_at timestamptz,review_evidence varchar(1000),
 FOREIGN KEY(template_id,sacco_id) REFERENCES financial_statement_templates(id,sacco_id),UNIQUE(template_id,version),UNIQUE(id,sacco_id),
 CHECK((state='DRAFT' AND checked_by IS NULL AND checked_at IS NULL AND review_evidence IS NULL) OR
 (state IN('APPROVED','RETIRED') AND checked_by IS NOT NULL AND checked_by<>made_by AND checked_at IS NOT NULL AND length(review_evidence)>0))
);
CREATE INDEX ix_financial_versions_scope ON financial_statement_versions(sacco_id,made_at DESC,id);
CREATE FUNCTION protect_financial_statement_version() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Financial statement versions are retained'; END IF;
 IF ROW(NEW.id,NEW.template_id,NEW.sacco_id,NEW.version,NEW.definition,NEW.made_by,NEW.made_at,NEW.evidence)
 IS DISTINCT FROM ROW(OLD.id,OLD.template_id,OLD.sacco_id,OLD.version,OLD.definition,OLD.made_by,OLD.made_at,OLD.evidence)
 OR NOT((OLD.state='DRAFT' AND NEW.state='APPROVED') OR(OLD.state='APPROVED' AND NEW.state='RETIRED'))
 OR(OLD.state='APPROVED' AND ROW(NEW.checked_by,NEW.checked_at,NEW.review_evidence) IS DISTINCT FROM ROW(OLD.checked_by,OLD.checked_at,OLD.review_evidence))
 THEN RAISE EXCEPTION 'Create a new statement version or approve independently'; END IF;RETURN NEW;END; $$;
CREATE TRIGGER financial_statement_version_immutable BEFORE UPDATE OR DELETE ON financial_statement_versions FOR EACH ROW EXECUTE FUNCTION protect_financial_statement_version();

-- Frozen statements contain source evidence, complete account values and mandatory disclosures.
CREATE TABLE financial_statement_results (
 id uuid PRIMARY KEY,sacco_id varchar(255) NOT NULL,station_id varchar(255) NOT NULL,
 version_id uuid NOT NULL,close_review_id uuid NOT NULL REFERENCES accounting_close_review(id),comparison_close_id uuid REFERENCES accounting_close_review(id),
 result_json text NOT NULL CHECK(length(result_json)<=1000000),checksum char(64) NOT NULL,
 made_by uuid NOT NULL REFERENCES members(id),made_at timestamptz NOT NULL,
 prior_result_id uuid,
 FOREIGN KEY(version_id,sacco_id) REFERENCES financial_statement_versions(id,sacco_id),
 FOREIGN KEY(prior_result_id,sacco_id,station_id) REFERENCES financial_statement_results(id,sacco_id,station_id),
 UNIQUE(id,sacco_id,station_id),UNIQUE NULLS NOT DISTINCT(sacco_id,station_id,version_id,close_review_id,comparison_close_id)
);
CREATE INDEX ix_financial_results_scope ON financial_statement_results(sacco_id,station_id,made_at DESC,id);
CREATE FUNCTION protect_financial_statement_evidence() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'Financial statement evidence is immutable'; END; $$;
CREATE TRIGGER financial_statement_result_immutable BEFORE UPDATE OR DELETE ON financial_statement_results FOR EACH ROW EXECUTE FUNCTION protect_financial_statement_evidence();

-- Official layouts are externally supplied protected artifacts, never editable institution layouts.
CREATE TABLE regulatory_statement_formats (
 id uuid PRIMARY KEY,authority varchar(120) NOT NULL,format_key varchar(80) NOT NULL,version varchar(40) NOT NULL,
 format_json text NOT NULL CHECK(length(format_json)<=200000),checksum char(64) NOT NULL,official_reference varchar(1000) NOT NULL,
 applicability_evidence varchar(1000) NOT NULL,registered_at timestamptz NOT NULL,
 UNIQUE(format_key,version)
);
CREATE TRIGGER regulatory_format_immutable BEFORE UPDATE OR DELETE ON regulatory_statement_formats FOR EACH ROW EXECUTE FUNCTION protect_financial_statement_evidence();
CREATE TABLE regulatory_statement_submissions (
 id uuid PRIMARY KEY,sacco_id varchar(255) NOT NULL REFERENCES registered_saccos(sacco_id),station_id varchar(255) NOT NULL,
 format_id uuid NOT NULL REFERENCES regulatory_statement_formats(id),result_id uuid NOT NULL,
 period_from date NOT NULL,period_through date NOT NULL,deadline date NOT NULL,
 file_reference varchar(1000) NOT NULL,file_checksum char(64) NOT NULL,file_bytes bytea NOT NULL CHECK(octet_length(file_bytes) BETWEEN 1 AND 2000000),validation_evidence varchar(1000) NOT NULL,
 corrections_id uuid REFERENCES regulatory_statement_submissions(id),made_by uuid NOT NULL REFERENCES members(id),made_at timestamptz NOT NULL,
 FOREIGN KEY(result_id,sacco_id,station_id) REFERENCES financial_statement_results(id,sacco_id,station_id),
 UNIQUE(id,sacco_id),CHECK(period_from<=period_through),CHECK(deadline>=period_through)
);
CREATE INDEX ix_regulatory_submissions_scope ON regulatory_statement_submissions(sacco_id,station_id,made_at DESC,id);
CREATE TABLE regulatory_statement_reviews (
 submission_id uuid PRIMARY KEY REFERENCES regulatory_statement_submissions(id),sacco_id varchar(255) NOT NULL,
 checker_id uuid NOT NULL REFERENCES members(id),evidence varchar(1000) NOT NULL,decided_at timestamptz NOT NULL,
 FOREIGN KEY(submission_id,sacco_id) REFERENCES regulatory_statement_submissions(id,sacco_id)
);
CREATE TRIGGER regulatory_submission_immutable BEFORE UPDATE OR DELETE ON regulatory_statement_submissions FOR EACH ROW EXECUTE FUNCTION protect_financial_statement_evidence();
CREATE TRIGGER regulatory_review_immutable BEFORE UPDATE OR DELETE ON regulatory_statement_reviews FOR EACH ROW EXECUTE FUNCTION protect_financial_statement_evidence();
CREATE FUNCTION check_regulatory_reviewer() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF EXISTS(SELECT 1 FROM regulatory_statement_submissions WHERE id=NEW.submission_id AND made_by=NEW.checker_id) THEN RAISE EXCEPTION 'Independent regulatory reviewer required'; END IF;RETURN NEW;END; $$;
CREATE TRIGGER regulatory_review_check BEFORE INSERT ON regulatory_statement_reviews FOR EACH ROW EXECUTE FUNCTION check_regulatory_reviewer();

CREATE FUNCTION check_financial_statement_source() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE source_review accounting_close_review; source_period accounting_period; source_id uuid;
BEGIN
 IF NOT EXISTS(SELECT 1 FROM financial_statement_versions WHERE id=NEW.version_id AND sacco_id=NEW.sacco_id AND state='APPROVED')
 THEN RAISE EXCEPTION 'Independently approved statement mapping required'; END IF;
 FOR source_id IN SELECT NEW.close_review_id UNION SELECT NEW.comparison_close_id WHERE NEW.comparison_close_id IS NOT NULL LOOP
  SELECT * INTO source_review FROM accounting_close_review WHERE id=source_id AND sacco_id=NEW.sacco_id AND station_id=NEW.station_id AND action='CLOSE';
  IF NOT FOUND OR NOT EXISTS(SELECT 1 FROM accounting_close_decision WHERE review_id=source_id)
  THEN RAISE EXCEPTION 'Scoped independently reviewed close required'; END IF;
  IF EXISTS(SELECT 1 FROM accounting_close_review r JOIN accounting_close_decision d ON d.review_id=r.id
   WHERE r.period_id=source_review.period_id AND r.sacco_id=NEW.sacco_id AND r.station_id=NEW.station_id AND r.version>source_review.version)
  THEN RAISE EXCEPTION 'Latest approved close required for publication'; END IF;
  SELECT * INTO source_period FROM accounting_period WHERE id=source_review.period_id AND sacco_id=NEW.sacco_id FOR SHARE;
  IF source_period.state<>'CLOSED' THEN RAISE EXCEPTION 'Closed period required for final statement'; END IF;
 END LOOP;
 IF NEW.result_json::jsonb->>'status' IS DISTINCT FROM 'FINAL' OR NEW.result_json::jsonb->>'institution' IS DISTINCT FROM NEW.sacco_id OR NEW.result_json::jsonb->>'branch' IS DISTINCT FROM NEW.station_id
 OR NEW.result_json::jsonb->>'versionId' IS DISTINCT FROM NEW.version_id::text OR NEW.result_json::jsonb->>'closeReviewId' IS DISTINCT FROM NEW.close_review_id::text
 OR NEW.result_json::jsonb->>'closeChecksum' IS DISTINCT FROM (SELECT checksum FROM accounting_close_review WHERE id=NEW.close_review_id)
 OR NEW.checksum<>encode(sha256(convert_to(NEW.result_json,'UTF8')),'hex')
 THEN RAISE EXCEPTION 'Statement result metadata and checksum must agree'; END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER financial_statement_source_check BEFORE INSERT ON financial_statement_results FOR EACH ROW EXECUTE FUNCTION check_financial_statement_source();
