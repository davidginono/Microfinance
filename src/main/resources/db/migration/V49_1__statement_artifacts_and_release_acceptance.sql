ALTER TABLE accounting_policies ADD CONSTRAINT uq_policy_release_institution UNIQUE(id,sacco_id);
CREATE TABLE statement_output_sets (
 id uuid PRIMARY KEY,sacco_id varchar(255) NOT NULL,station_id varchar(255) NOT NULL,result_id uuid NOT NULL,
 source_checksum varchar(64) NOT NULL,result_json text NOT NULL CHECK(octet_length(result_json)<=1500000),result_checksum varchar(64) NOT NULL,
 layout_json text NOT NULL CHECK(length(layout_json)<=2000),layout_checksum varchar(64) NOT NULL,font_checksum varchar(64) NOT NULL,
 generated_by uuid NOT NULL REFERENCES members(id),generated_at timestamptz NOT NULL,
 logo bytea CHECK(octet_length(logo) BETWEEN 1 AND 5000000),logo_media_type varchar(32),logo_checksum varchar(64),
 FOREIGN KEY(result_id,sacco_id,station_id) REFERENCES financial_statement_results(id,sacco_id,station_id),
 UNIQUE(id,sacco_id,station_id),UNIQUE(sacco_id,station_id,result_id,layout_checksum),
 CHECK((logo IS NULL AND logo_media_type IS NULL AND logo_checksum IS NULL) OR
       (logo IS NOT NULL AND logo_media_type IN('image/png','image/jpeg') AND logo_checksum IS NOT NULL))
);
CREATE INDEX ix_statement_outputs_scope ON statement_output_sets(sacco_id,station_id,generated_at DESC,id);
CREATE TABLE statement_output_artifacts (
 id uuid PRIMARY KEY,output_id uuid NOT NULL REFERENCES statement_output_sets(id),
 format varchar(4) NOT NULL CHECK(format IN('CSV','XLSX','PDF')),payload bytea NOT NULL CHECK(octet_length(payload) BETWEEN 1 AND 33554432),
 checksum varchar(64) NOT NULL,UNIQUE(output_id,format)
);
CREATE TABLE statement_output_reviews (
 output_id uuid PRIMARY KEY REFERENCES statement_output_sets(id),reviewer uuid NOT NULL REFERENCES members(id),
 evidence varchar(1000) NOT NULL CHECK(length(evidence)>0),reviewed_at timestamptz NOT NULL
);
CREATE TABLE accounting_release_requests (
 id uuid PRIMARY KEY,sacco_id varchar(255) NOT NULL REFERENCES registered_saccos(sacco_id),station_id varchar(255) NOT NULL,
 request_key uuid NOT NULL,requested_by uuid NOT NULL REFERENCES members(id),requested_at timestamptz NOT NULL,
 policy_id uuid NOT NULL,policy_version integer NOT NULL CHECK(policy_version>0),period_id uuid NOT NULL,
 mapping_id uuid NOT NULL,template_id uuid NOT NULL,statement_output_id uuid NOT NULL,
 first_sample uuid NOT NULL,second_sample uuid NOT NULL CHECK(second_sample<>first_sample),
 dependency_json text NOT NULL CHECK(octet_length(dependency_json)<=64000),dependency_checksum varchar(64) NOT NULL,
 proposal_evidence varchar(1000) NOT NULL CHECK(length(proposal_evidence)>0),
 FOREIGN KEY(policy_id,sacco_id) REFERENCES accounting_policies(id,sacco_id),
 FOREIGN KEY(period_id,sacco_id) REFERENCES accounting_period(id,sacco_id),
 FOREIGN KEY(mapping_id,sacco_id) REFERENCES financial_statement_versions(id,sacco_id),
 FOREIGN KEY(template_id,sacco_id) REFERENCES financial_statement_templates(id,sacco_id),
 FOREIGN KEY(statement_output_id,sacco_id,station_id) REFERENCES statement_output_sets(id,sacco_id,station_id),
 FOREIGN KEY(first_sample,sacco_id,station_id) REFERENCES report_runs(id,sacco_id,station_id),
 FOREIGN KEY(second_sample,sacco_id,station_id) REFERENCES report_runs(id,sacco_id,station_id),
 UNIQUE(sacco_id,station_id,requested_by,request_key),UNIQUE(id,sacco_id)
);
CREATE INDEX ix_release_scope ON accounting_release_requests(sacco_id,station_id,requested_at DESC,id);
CREATE INDEX ix_release_policy ON accounting_release_requests(sacco_id,station_id,policy_id,policy_version);
CREATE INDEX ix_release_period ON accounting_release_requests(sacco_id,period_id);
CREATE INDEX ix_release_mapping ON accounting_release_requests(sacco_id,template_id,mapping_id);
CREATE TABLE accounting_release_decisions (
 release_id uuid NOT NULL REFERENCES accounting_release_requests(id),
 stage varchar(16) NOT NULL CHECK(stage IN('ACCOUNTANT','COMPLIANCE','STAFF')),
 approved boolean NOT NULL,reviewer uuid NOT NULL REFERENCES members(id),
 evidence varchar(1000) NOT NULL CHECK(length(evidence)>0),decided_at timestamptz NOT NULL,
 PRIMARY KEY(release_id,stage),UNIQUE(release_id,reviewer)
);
CREATE TABLE accounting_release_invalidations (
 id uuid PRIMARY KEY,release_id uuid NOT NULL REFERENCES accounting_release_requests(id),
 kind varchar(32) NOT NULL CHECK(kind IN('PERIOD_REOPENED','MAPPING_APPROVED','RETIRED','WITHDRAWN')),
 actor_id uuid NOT NULL REFERENCES members(id),reason varchar(2000) NOT NULL CHECK(length(reason)>0),recorded_at timestamptz NOT NULL
);
CREATE INDEX ix_release_invalidations ON accounting_release_invalidations(release_id);
CREATE FUNCTION protect_accounting_release_evidence() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Accounting release and statement artifacts are immutable retained evidence'; END; $$;
DO $$ DECLARE t text; BEGIN FOREACH t IN ARRAY ARRAY['statement_output_sets','statement_output_artifacts','statement_output_reviews',
 'accounting_release_requests','accounting_release_decisions','accounting_release_invalidations'] LOOP
 EXECUTE format('CREATE TRIGGER %I BEFORE UPDATE OR DELETE ON %I FOR EACH ROW EXECUTE FUNCTION protect_accounting_release_evidence()',t||'_immutable',t);
END LOOP; END $$;
CREATE FUNCTION check_accounting_release_decision() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE requester uuid;
BEGIN
 SELECT requested_by INTO requester FROM accounting_release_requests WHERE id=NEW.release_id FOR UPDATE;
 IF requester=NEW.reviewer THEN RAISE EXCEPTION 'An independent reviewer must decide institution release'; END IF;
 IF EXISTS(SELECT 1 FROM accounting_release_invalidations WHERE release_id=NEW.release_id) OR
    EXISTS(SELECT 1 FROM accounting_release_decisions WHERE release_id=NEW.release_id AND NOT approved)
 THEN RAISE EXCEPTION 'Invalidated or rejected release requires a new verified proposal'; END IF;
 IF NEW.stage='COMPLIANCE' AND NOT EXISTS(SELECT 1 FROM accounting_release_decisions WHERE release_id=NEW.release_id AND stage='ACCOUNTANT' AND approved)
 OR NEW.stage='STAFF' AND (SELECT count(*) FROM accounting_release_decisions WHERE release_id=NEW.release_id AND stage IN('ACCOUNTANT','COMPLIANCE') AND approved)<>2
 THEN RAISE EXCEPTION 'Complete the prior independent release decision first'; END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER release_decision_gate BEFORE INSERT ON accounting_release_decisions FOR EACH ROW EXECUTE FUNCTION check_accounting_release_decision();
CREATE FUNCTION check_statement_output_review() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.reviewer=(SELECT generated_by FROM statement_output_sets WHERE id=NEW.output_id)
 THEN RAISE EXCEPTION 'Statement output requires an independent reviewer'; END IF;
 IF (SELECT count(*) FROM statement_output_artifacts WHERE output_id=NEW.output_id)<>3
 THEN RAISE EXCEPTION 'Review requires all three retained statement formats'; END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER statement_output_review_gate BEFORE INSERT ON statement_output_reviews FOR EACH ROW EXECUTE FUNCTION check_statement_output_review();
