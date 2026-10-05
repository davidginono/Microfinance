-- Existing release decisions remain retained; only fresh version-2 source proofs grant posting access.
ALTER TABLE accounting_release_requests ADD COLUMN source_guard_version integer NOT NULL DEFAULT 1 CHECK(source_guard_version IN(1,2));
ALTER TABLE accounting_release_requests DROP CONSTRAINT accounting_release_requests_dependency_json_check;
ALTER TABLE accounting_release_requests ADD CONSTRAINT accounting_release_dependency_size CHECK(octet_length(dependency_json)<=8388608);
ALTER TABLE accounting_release_invalidations DROP CONSTRAINT accounting_release_invalidations_kind_check;
ALTER TABLE accounting_release_invalidations ADD CONSTRAINT accounting_release_invalidation_kind CHECK(kind IN('PERIOD_REOPENED','MAPPING_APPROVED','RETIRED','WITHDRAWN','CASH_FLOW_APPROVED'));

CREATE INDEX ix_release_current_policy_guard ON accounting_release_requests(sacco_id,policy_id,policy_version,requested_at DESC,id) WHERE source_guard_version=2;
CREATE INDEX ix_statement_output_dimension ON statement_output_sets(sacco_id,station_id,(coalesce(result_json::jsonb->>'dimension','BRANCH')),generated_at DESC,id);
CREATE TABLE accounting_release_branch_sources(
 release_id uuid NOT NULL,role varchar(10) NOT NULL CHECK(role IN('CURRENT','COMPARISON')),sacco_id varchar(255) NOT NULL,station_id varchar(255) NOT NULL,
 close_review_id uuid NOT NULL REFERENCES accounting_close_review(id),close_version integer NOT NULL CHECK(close_version>0),recorded_cutoff timestamptz NOT NULL,close_checksum varchar(64) NOT NULL CHECK(close_checksum~'^[0-9a-f]{64}$'),close_reviewer uuid NOT NULL REFERENCES members(id),
 opening_id uuid NOT NULL REFERENCES gl_cutover_coverage(id),opening_checksum varchar(64) NOT NULL CHECK(opening_checksum~'^[0-9a-f]{64}$'),opening_maker uuid NOT NULL REFERENCES members(id),opening_reviewer uuid NOT NULL REFERENCES members(id),CHECK(opening_maker<>opening_reviewer),
 PRIMARY KEY(release_id,role,station_id),FOREIGN KEY(release_id,sacco_id) REFERENCES accounting_release_requests(id,sacco_id)
);
CREATE INDEX ix_release_covered_branch ON accounting_release_branch_sources(sacco_id,station_id,release_id) WHERE role='CURRENT';
CREATE TABLE accounting_release_cash_sources(
 release_id uuid NOT NULL,role varchar(10) NOT NULL CHECK(role IN('CURRENT','COMPARISON')),sacco_id varchar(255) NOT NULL,station_id varchar(255) NOT NULL,
 journal_id uuid NOT NULL,allocation_id uuid NOT NULL,allocation_version integer NOT NULL CHECK(allocation_version>0),source_checksum varchar(64) NOT NULL CHECK(source_checksum~'^[0-9a-f]{64}$'),definition_checksum varchar(64) NOT NULL CHECK(definition_checksum~'^[0-9a-f]{64}$'),reviewer uuid NOT NULL REFERENCES members(id),reviewed_at timestamptz NOT NULL,
 PRIMARY KEY(release_id,role,journal_id),FOREIGN KEY(release_id,role,station_id) REFERENCES accounting_release_branch_sources(release_id,role,station_id),
 FOREIGN KEY(release_id,sacco_id) REFERENCES accounting_release_requests(id,sacco_id),FOREIGN KEY(journal_id,sacco_id,station_id) REFERENCES gl_journal(id,sacco_id,station_id),FOREIGN KEY(allocation_id,sacco_id,station_id) REFERENCES cash_flow_allocations(id,sacco_id,station_id)
);
CREATE INDEX ix_release_cash_version ON accounting_release_cash_sources(sacco_id,journal_id,allocation_version,release_id);
CREATE TRIGGER release_branch_sources_immutable BEFORE UPDATE OR DELETE ON accounting_release_branch_sources FOR EACH ROW EXECUTE FUNCTION protect_accounting_release_evidence();
CREATE TRIGGER release_cash_sources_immutable BEFORE UPDATE OR DELETE ON accounting_release_cash_sources FOR EACH ROW EXECUTE FUNCTION protect_accounting_release_evidence();

CREATE FUNCTION validate_accounting_release_branch_source() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE parent accounting_release_requests; source jsonb; frozen jsonb;
BEGIN
 SELECT * INTO parent FROM accounting_release_requests WHERE id=NEW.release_id AND sacco_id=NEW.sacco_id;
 IF parent.source_guard_version IS DISTINCT FROM 2 OR EXISTS(SELECT 1 FROM accounting_release_decisions WHERE release_id=NEW.release_id) OR EXISTS(SELECT 1 FROM accounting_release_invalidations WHERE release_id=NEW.release_id) THEN RAISE EXCEPTION 'Retained release sources cannot be extended after review'; END IF;
 source=parent.dependency_json::jsonb->(CASE NEW.role WHEN 'CURRENT' THEN 'currentSource' ELSE 'comparisonSource' END);
 IF NOT EXISTS(SELECT 1 FROM jsonb_array_elements(source->'branches') p WHERE p->>'branch'=NEW.station_id AND p->>'role'=NEW.role AND p->>'reviewId'=NEW.close_review_id::text AND (p->>'version')::integer=NEW.close_version AND (p->>'recordedCutoff')::timestamptz=NEW.recorded_cutoff AND p->>'checksum'=NEW.close_checksum AND p->>'reviewer'=NEW.close_reviewer::text AND p->>'openingId'=NEW.opening_id::text AND p->>'openingChecksum'=NEW.opening_checksum AND p->>'openingMaker'=NEW.opening_maker::text AND p->>'openingReviewer'=NEW.opening_reviewer::text) THEN RAISE EXCEPTION 'Release branch proof must match its retained receipt'; END IF;
 IF NOT EXISTS(SELECT 1 FROM accounting_close_review c JOIN accounting_close_decision d ON d.review_id=c.id JOIN gl_cutover_coverage o ON o.id=NEW.opening_id AND o.sacco_id=c.sacco_id AND o.station_id=c.station_id JOIN gl_journal j ON j.id=o.opening_journal_id AND j.sacco_id=o.sacco_id AND j.station_id=o.station_id WHERE c.id=NEW.close_review_id AND c.sacco_id=NEW.sacco_id AND c.station_id=NEW.station_id AND c.action='CLOSE' AND c.period_id=(source->>'period')::uuid AND c.version=NEW.close_version AND c.recorded_at=NEW.recorded_cutoff AND c.checksum=NEW.close_checksum AND encode(sha256(convert_to(c.snapshot_json,'UTF8')),'hex')=c.checksum AND d.checker_id=NEW.close_reviewer AND d.checker_id<>c.maker_id AND o.complete AND j.state='POSTED' AND j.payload_hash=NEW.opening_checksum AND j.maker_id=NEW.opening_maker AND j.checker_id=NEW.opening_reviewer AND j.checked_at<=NEW.recorded_cutoff AND j.posted_at<=NEW.recorded_cutoff AND c.snapshot_json::jsonb#>>'{reviewedOpening,id}'=o.id::text AND c.snapshot_json::jsonb#>>'{reviewedOpening,payloadChecksum}'=j.payload_hash) THEN RAISE EXCEPTION 'Release branch close and independent opening must agree'; END IF;
 SELECT result_json::jsonb INTO frozen FROM statement_output_sets WHERE id=parent.statement_output_id AND sacco_id=parent.sacco_id;
 IF coalesce(frozen->>'dimension','BRANCH')='INSTITUTION' THEN
  IF NOT EXISTS(SELECT 1 FROM jsonb_array_elements(frozen->(CASE NEW.role WHEN 'CURRENT' THEN 'currentBranchSources' ELSE 'comparisonBranchSources' END)) p WHERE p->>'branch'=NEW.station_id AND p->>'reviewId'=NEW.close_review_id::text AND (p->>'version')::integer=NEW.close_version AND (p->>'recordedCutoff')::timestamptz=NEW.recorded_cutoff AND p->>'checksum'=NEW.close_checksum AND p->>'reviewer'=NEW.close_reviewer::text) THEN RAISE EXCEPTION 'Release scope cannot exceed frozen institution statement'; END IF;
 ELSE
  IF NEW.station_id<>parent.station_id OR frozen->>(CASE NEW.role WHEN 'CURRENT' THEN 'closeReviewId' ELSE 'comparisonCloseId' END) IS DISTINCT FROM NEW.close_review_id::text OR frozen->>(CASE NEW.role WHEN 'CURRENT' THEN 'closeChecksum' ELSE 'comparisonCloseChecksum' END) IS DISTINCT FROM NEW.close_checksum THEN RAISE EXCEPTION 'Branch release proof must match frozen statement'; END IF;
 END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER release_branch_source_guard BEFORE INSERT ON accounting_release_branch_sources FOR EACH ROW EXECUTE FUNCTION validate_accounting_release_branch_source();

CREATE FUNCTION validate_accounting_release_cash_source() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE parent accounting_release_requests; source jsonb;
BEGIN
 SELECT * INTO parent FROM accounting_release_requests WHERE id=NEW.release_id AND sacco_id=NEW.sacco_id;
 IF parent.source_guard_version IS DISTINCT FROM 2 OR EXISTS(SELECT 1 FROM accounting_release_decisions WHERE release_id=NEW.release_id) OR EXISTS(SELECT 1 FROM accounting_release_invalidations WHERE release_id=NEW.release_id) THEN RAISE EXCEPTION 'Retained release cash sources cannot be extended after review'; END IF;
 source=parent.dependency_json::jsonb->(CASE NEW.role WHEN 'CURRENT' THEN 'currentSource' ELSE 'comparisonSource' END);
 IF NOT EXISTS(SELECT 1 FROM jsonb_array_elements(source->'cashVersions') p WHERE p->>'role'=NEW.role AND p->>'journalId'=NEW.journal_id::text AND p->>'allocationId'=NEW.allocation_id::text AND (p->>'version')::integer=NEW.allocation_version AND p->>'sourceChecksum'=NEW.source_checksum AND p->>'definitionChecksum'=NEW.definition_checksum AND p->>'reviewer'=NEW.reviewer::text AND (p->>'reviewedAt')::timestamptz=NEW.reviewed_at) THEN RAISE EXCEPTION 'Release cash proof must match its retained receipt'; END IF;
 IF NOT EXISTS(SELECT 1 FROM cash_flow_allocations a JOIN cash_flow_allocation_reviews r ON r.allocation_id=a.id WHERE a.id=NEW.allocation_id AND a.sacco_id=NEW.sacco_id AND a.station_id=NEW.station_id AND a.journal_id=NEW.journal_id AND a.version=NEW.allocation_version AND a.source_checksum=NEW.source_checksum AND a.definition_checksum=NEW.definition_checksum AND r.checker_id=NEW.reviewer AND r.reviewed_at=NEW.reviewed_at AND r.checker_id<>a.made_by) THEN RAISE EXCEPTION 'Release cash proof requires exact independent allocation review'; END IF;
 IF NOT EXISTS(SELECT 1 FROM accounting_release_branch_sources b JOIN accounting_close_review c ON c.id=b.close_review_id CROSS JOIN LATERAL jsonb_array_elements(c.snapshot_json::jsonb#>'{cashFlowAllocations,versions}') p WHERE b.release_id=NEW.release_id AND b.role=NEW.role AND b.station_id=NEW.station_id AND p->>'id'=NEW.allocation_id::text AND p->>'journalId'=NEW.journal_id::text AND (p->>'version')::integer=NEW.allocation_version AND p->>'sourceChecksum'=NEW.source_checksum AND p->>'definitionChecksum'=NEW.definition_checksum AND p->>'checker'=NEW.reviewer::text AND (p->>'reviewedAt')::timestamptz=NEW.reviewed_at) THEN RAISE EXCEPTION 'Release cash proof must be frozen in the covered branch close'; END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER release_cash_source_guard BEFORE INSERT ON accounting_release_cash_sources FOR EACH ROW EXECUTE FUNCTION validate_accounting_release_cash_source();

CREATE FUNCTION require_accounting_release_source_proof() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE frozen jsonb; receipt jsonb; role_name text; source jsonb; expected_branches integer; expected_cash integer; actual_branches integer; actual_cash integer; dimension text;
BEGIN
 IF NEW.source_guard_version=1 THEN RETURN NULL; END IF;
 receipt=NEW.dependency_json::jsonb;
 SELECT result_json::jsonb INTO frozen FROM statement_output_sets WHERE id=NEW.statement_output_id AND sacco_id=NEW.sacco_id AND station_id=NEW.station_id;
 dimension=coalesce(frozen->>'dimension','BRANCH');
 IF dimension NOT IN('BRANCH','INSTITUTION') THEN RAISE EXCEPTION 'Release source dimension is unavailable'; END IF;
 IF receipt->>'schemaVersion' IS DISTINCT FROM '2' OR receipt->>'institution' IS DISTINCT FROM NEW.sacco_id OR receipt->>'branch' IS DISTINCT FROM NEW.station_id OR frozen->>'policyId' IS DISTINCT FROM NEW.policy_id::text OR (frozen->>'policyVersion')::integer IS DISTINCT FROM NEW.policy_version OR frozen->>'status' IS DISTINCT FROM 'FINAL' THEN RAISE EXCEPTION 'Fresh release requires a complete trusted source receipt'; END IF;
 FOREACH role_name IN ARRAY ARRAY['CURRENT','COMPARISON'] LOOP
  source=receipt->(CASE role_name WHEN 'CURRENT' THEN 'currentSource' ELSE 'comparisonSource' END);
  IF source IS NULL OR source='null'::jsonb THEN
   IF role_name='CURRENT' OR frozen->>(CASE dimension WHEN 'INSTITUTION' THEN 'comparisonInstitutionPeriodId' ELSE 'comparisonCloseId' END) IS NOT NULL THEN RAISE EXCEPTION 'Release comparative source cannot disappear'; END IF;
   expected_branches=0; expected_cash=0;
  ELSE
   IF jsonb_typeof(source) IS DISTINCT FROM 'object' OR jsonb_typeof(source->'branches') IS DISTINCT FROM 'array' OR jsonb_typeof(source->'cashVersions') IS DISTINCT FROM 'array' OR coalesce(source->>'checksum','') !~ '^[0-9a-f]{64}$' THEN RAISE EXCEPTION 'Release source provenance is incomplete'; END IF;
   IF source->>'dimension' IS DISTINCT FROM dimension OR source->>'role' IS DISTINCT FROM role_name OR source->>'checksum' IS DISTINCT FROM frozen->>(CASE WHEN dimension='INSTITUTION' AND role_name='CURRENT' THEN 'institutionSourceChecksum' WHEN dimension='INSTITUTION' THEN 'comparisonInstitutionSourceChecksum' WHEN role_name='CURRENT' THEN 'closeChecksum' ELSE 'comparisonCloseChecksum' END) THEN RAISE EXCEPTION 'Release financial source checksum or dimension differs'; END IF;
   IF source->>'from' IS DISTINCT FROM frozen->>(CASE role_name WHEN 'CURRENT' THEN 'from' ELSE 'comparisonFrom' END) OR source->>'through' IS DISTINCT FROM frozen->>(CASE role_name WHEN 'CURRENT' THEN 'through' ELSE 'comparisonThrough' END) THEN RAISE EXCEPTION 'Release dates must agree with frozen statement'; END IF;
   IF role_name='CURRENT' AND (source->>'period')::uuid IS DISTINCT FROM NEW.period_id THEN RAISE EXCEPTION 'Release period must match its financial source'; END IF;
   expected_branches=CASE dimension WHEN 'INSTITUTION' THEN jsonb_array_length(frozen->(CASE role_name WHEN 'CURRENT' THEN 'currentBranchSources' ELSE 'comparisonBranchSources' END)) ELSE 1 END;
   IF expected_branches IS NULL OR expected_branches NOT BETWEEN 1 AND 1000 OR jsonb_array_length(source->'branches')<>expected_branches THEN RAISE EXCEPTION 'Release branch coverage must be complete'; END IF;
   expected_cash=jsonb_array_length(source->'cashVersions'); IF expected_cash NOT BETWEEN 0 AND 1000 THEN RAISE EXCEPTION 'Release cash proof exceeds bounded coverage'; END IF;
  END IF;
  SELECT count(*) INTO actual_branches FROM accounting_release_branch_sources WHERE release_id=NEW.id AND role=role_name;
  SELECT count(*) INTO actual_cash FROM accounting_release_cash_sources WHERE release_id=NEW.id AND role=role_name;
  IF actual_branches<>expected_branches OR actual_cash<>expected_cash OR actual_cash<>(SELECT count(*) FROM accounting_release_branch_sources b JOIN accounting_close_review c ON c.id=b.close_review_id CROSS JOIN LATERAL jsonb_array_elements(c.snapshot_json::jsonb#>'{cashFlowAllocations,versions}') p WHERE b.release_id=NEW.id AND b.role=role_name) THEN RAISE EXCEPTION 'Release requires every frozen branch and cash allocation source'; END IF;
 END LOOP;
 RETURN NULL;
END; $$;
CREATE CONSTRAINT TRIGGER release_complete_source_proof AFTER INSERT ON accounting_release_requests DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION require_accounting_release_source_proof();
