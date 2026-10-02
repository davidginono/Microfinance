ALTER TABLE operational_report_template_versions ADD CONSTRAINT uq_report_template_institution UNIQUE(id,sacco_id);
CREATE TABLE report_runs (
 id uuid PRIMARY KEY, sacco_id varchar(255) NOT NULL REFERENCES registered_saccos(sacco_id),
 station_id varchar(255) NOT NULL, requested_by uuid NOT NULL REFERENCES members(id),
 request_key uuid NOT NULL, request_hash varchar(64) NOT NULL, template_version_id uuid NOT NULL REFERENCES operational_report_template_versions(id),
 template_version integer NOT NULL CHECK(template_version>0), dataset_version integer NOT NULL CHECK(dataset_version=1),
 metric_version varchar(32) NOT NULL, dataset varchar(32) NOT NULL CHECK(dataset IN('COLLECTIONS','DISBURSEMENTS','LOAN_PORTFOLIO')), definition text NOT NULL CHECK(length(definition)<=12000),
 from_date date NOT NULL, through_date date NOT NULL CHECK(through_date>=from_date), recorded_cutoff timestamptz NOT NULL,
 formats varchar(32) NOT NULL, status varchar(16) NOT NULL CHECK(status IN('QUEUED','RUNNING','READY','APPROVED','FAILED','CANCELLED')),
 requested_at timestamptz NOT NULL, started_at timestamptz, generated_at timestamptz,
 worker_token uuid, lease_until timestamptz, attempts integer NOT NULL DEFAULT 0 CHECK(attempts BETWEEN 0 AND 3),
 cancel_requested boolean NOT NULL DEFAULT false, failure_key varchar(80),
 row_count bigint CHECK(row_count>=0 AND row_count<=20000), untracked_count bigint CHECK(untracked_count>=0),
 coverage_key varchar(100), totals text CHECK(length(totals)<=4096), snapshot_id varchar(128),
 result_checksum varchar(64), approved_by uuid REFERENCES members(id), approved_at timestamptz, approval_evidence varchar(200),
 restates_id uuid, restatement_reason varchar(200),
 UNIQUE(sacco_id,station_id,requested_by,request_key), UNIQUE(id,sacco_id,station_id),
 FOREIGN KEY(restates_id,sacco_id,station_id) REFERENCES report_runs(id,sacco_id,station_id),
 FOREIGN KEY(template_version_id,sacco_id) REFERENCES operational_report_template_versions(id,sacco_id),
 CHECK((restates_id IS NULL AND restatement_reason IS NULL) OR (restates_id IS NOT NULL AND restatement_reason IS NOT NULL AND length(restatement_reason)>0)),
 CHECK((status NOT IN('READY','APPROVED')) OR (generated_at IS NOT NULL AND row_count IS NOT NULL AND result_checksum IS NOT NULL AND snapshot_id IS NOT NULL)),
 CHECK((status='APPROVED' AND approved_by IS NOT NULL AND approved_by<>requested_by AND approved_at IS NOT NULL AND approval_evidence IS NOT NULL AND length(approval_evidence)>0)
 OR (status<>'APPROVED' AND approved_by IS NULL AND approved_at IS NULL AND approval_evidence IS NULL))
);
CREATE INDEX ix_report_runs_scope ON report_runs(sacco_id,station_id,requested_at DESC,id);
CREATE INDEX ix_report_runs_requester ON report_runs(sacco_id,requested_by,status);
CREATE INDEX ix_report_runs_active_quota ON report_runs(sacco_id,requested_by) WHERE status IN('QUEUED','RUNNING');
CREATE INDEX ix_report_runs_queue ON report_runs(status,requested_at,sacco_id,id) WHERE status IN('QUEUED','RUNNING');
CREATE TABLE report_result_pages (
 run_id uuid NOT NULL REFERENCES report_runs(id), page integer NOT NULL CHECK(page>=0),
 row_count integer NOT NULL CHECK(row_count BETWEEN 0 AND 1000), payload text NOT NULL CHECK(octet_length(payload)<=1048576),
 checksum varchar(64) NOT NULL, PRIMARY KEY(run_id,page)
);
CREATE TABLE report_artifacts (
 id uuid PRIMARY KEY, run_id uuid NOT NULL REFERENCES report_runs(id), format varchar(4) NOT NULL CHECK(format IN('CSV','XLSX','PDF')),
 payload bytea NOT NULL CHECK(octet_length(payload)<=33554432), checksum varchar(64) NOT NULL,
 generated_at timestamptz NOT NULL, UNIQUE(run_id,format)
);
CREATE TABLE report_run_assets (
 run_id uuid PRIMARY KEY REFERENCES report_runs(id), media_type varchar(32) NOT NULL CHECK(media_type IN('image/png','image/jpeg')),
 payload bytea NOT NULL CHECK(octet_length(payload) BETWEEN 1 AND 5000000), checksum varchar(64) NOT NULL
);
CREATE FUNCTION protect_report_run() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Report history requires approved retention lifecycle'; END IF;
 IF ROW(NEW.id,NEW.sacco_id,NEW.station_id,NEW.requested_by,NEW.request_key,NEW.request_hash,NEW.template_version_id,NEW.template_version,NEW.dataset_version,NEW.metric_version,NEW.dataset,NEW.definition,NEW.from_date,NEW.through_date,NEW.recorded_cutoff,NEW.formats,NEW.requested_at,NEW.restates_id,NEW.restatement_reason)
 IS DISTINCT FROM ROW(OLD.id,OLD.sacco_id,OLD.station_id,OLD.requested_by,OLD.request_key,OLD.request_hash,OLD.template_version_id,OLD.template_version,OLD.dataset_version,OLD.metric_version,OLD.dataset,OLD.definition,OLD.from_date,OLD.through_date,OLD.recorded_cutoff,OLD.formats,OLD.requested_at,OLD.restates_id,OLD.restatement_reason)
 THEN RAISE EXCEPTION 'Report request is immutable'; END IF;
 IF NOT ((OLD.status='QUEUED' AND NEW.status IN('RUNNING','CANCELLED')) OR
  (OLD.status='RUNNING' AND NEW.status IN('RUNNING','READY','FAILED','CANCELLED')) OR
  (OLD.status='FAILED' AND NEW.status IN('QUEUED','CANCELLED')) OR
  (OLD.status='READY' AND NEW.status='APPROVED')) THEN RAISE EXCEPTION 'Invalid report run transition'; END IF;
 IF NEW.status='READY' THEN
  IF (SELECT COALESCE(sum(row_count),0) FROM report_result_pages WHERE run_id=NEW.id)<>NEW.row_count OR
   (SELECT count(*) FROM report_artifacts WHERE run_id=NEW.id)<>cardinality(string_to_array(NEW.formats,',')) OR
   EXISTS(SELECT 1 FROM unnest(string_to_array(NEW.formats,',')) requested(format) WHERE NOT EXISTS(SELECT 1 FROM report_artifacts a WHERE a.run_id=NEW.id AND a.format=requested.format))
  THEN RAISE EXCEPTION 'A completed report requires every result page and requested artifact'; END IF;
 END IF;
 IF OLD.status IN('READY','APPROVED') THEN
  IF OLD.status='APPROVED' OR NEW.status<>'APPROVED' OR
    (to_jsonb(NEW)-ARRAY['status','approved_by','approved_at','approval_evidence']) IS DISTINCT FROM
    (to_jsonb(OLD)-ARRAY['status','approved_by','approved_at','approval_evidence'])
  THEN RAISE EXCEPTION 'Final report is immutable; create an approved restatement'; END IF;
 END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER report_run_protection BEFORE UPDATE OR DELETE ON report_runs FOR EACH ROW EXECUTE FUNCTION protect_report_run();
CREATE FUNCTION protect_report_result() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE run_status varchar(16);
BEGIN
 IF TG_OP<>'INSERT' THEN RAISE EXCEPTION 'Frozen report results and artifacts are immutable'; END IF;
 SELECT status INTO run_status FROM report_runs WHERE id=NEW.run_id;
 IF run_status<>'RUNNING' THEN RAISE EXCEPTION 'Results must be captured by an active report worker'; END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER report_page_protection BEFORE INSERT OR UPDATE OR DELETE ON report_result_pages FOR EACH ROW EXECUTE FUNCTION protect_report_result();
CREATE TRIGGER report_artifact_protection BEFORE INSERT OR UPDATE OR DELETE ON report_artifacts FOR EACH ROW EXECUTE FUNCTION protect_report_result();
CREATE TRIGGER report_asset_protection BEFORE INSERT OR UPDATE OR DELETE ON report_run_assets FOR EACH ROW EXECUTE FUNCTION protect_report_result();
