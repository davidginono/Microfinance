-- Optional institution/branch/product/model policy revisions. Historical agreements are not backfilled.
CREATE FUNCTION lending_group_release_rules_valid(r jsonb) RETURNS boolean LANGUAGE plpgsql IMMUTABLE AS $$
BEGIN
 IF r IS NULL THEN RETURN true; END IF;
 RETURN jsonb_typeof(r)='object' AND r->'approved'='true'::jsonb
  AND jsonb_typeof(r->'annualCostRequired')='boolean' AND jsonb_typeof(r->'datedFeesRequired')='boolean'
  AND jsonb_typeof(r->'languageChoiceRequired')='boolean'
  AND ((r->>'annualCostRequired')::boolean OR (r->>'datedFeesRequired')::boolean OR (r->>'languageChoiceRequired')::boolean)
  AND length(btrim(r->>'methodologyReference')) BETWEEN 1 AND 500
  AND (r->>'methodologyReference') !~ '[[:cntrl:]]' AND r->>'agreementLanguage' IN ('EN','SW','BOTH')
  AND (r->>'maximumAnnualCostPercent' IS NULL OR ((r->>'annualCostRequired')::boolean
   AND jsonb_typeof(r->'maximumAnnualCostPercent')='number' AND (r->>'maximumAnnualCostPercent')::numeric BETWEEN 0 AND 100000
   AND scale((r->>'maximumAnnualCostPercent')::numeric)<=6));
EXCEPTION WHEN invalid_text_representation OR numeric_value_out_of_range THEN RETURN false;
END $$;
ALTER TABLE lending_group_policy ADD COLUMN release_rules jsonb;
ALTER TABLE lending_group_policy ADD CONSTRAINT lending_group_release_rules_valid CHECK(coalesce(lending_group_release_rules_valid(release_rules),false));

CREATE FUNCTION lending_group_disclosure_valid(policy uuid,s jsonb) RETURNS boolean LANGUAGE plpgsql STABLE AS $$
DECLARE p lending_group_policy%ROWTYPE;r jsonb;
BEGIN
 SELECT * INTO STRICT p FROM lending_group_policy WHERE id=policy;r=p.release_rules;
 IF r IS NULL THEN RETURN s IS NULL; END IF;
 RETURN coalesce(jsonb_typeof(s)='object' AND s->>'policyId'=p.id::text AND (s->>'revision')::bigint=p.revision AND s->'rules'=r
  AND length(s->>'costEvidence')<=500 AND length(s->>'datedFeesEvidence')<=500
  AND (s->>'costEvidence') !~ '[[:cntrl:]]' AND (s->>'datedFeesEvidence') !~ '[[:cntrl:]]'
  AND jsonb_typeof(s->'costVerified')='boolean' AND jsonb_typeof(s->'datedFeesVerified')='boolean'
  AND (NOT (r->>'annualCostRequired')::boolean OR (s->>'annualCostPercent' IS NOT NULL AND s->'costVerified'='true'::jsonb AND length(btrim(s->>'costEvidence'))>0))
  AND (s->>'annualCostPercent' IS NULL OR (jsonb_typeof(s->'annualCostPercent')='number' AND (s->>'annualCostPercent')::numeric BETWEEN 0 AND 100000
   AND scale((s->>'annualCostPercent')::numeric)<=6 AND s->'costVerified'='true'::jsonb AND length(btrim(s->>'costEvidence'))>0))
  AND (r->>'maximumAnnualCostPercent' IS NULL OR (s->>'annualCostPercent')::numeric<=(r->>'maximumAnnualCostPercent')::numeric)
  AND (NOT (r->>'datedFeesRequired')::boolean OR (s->'datedFeesVerified'='true'::jsonb AND length(btrim(s->>'datedFeesEvidence'))>0)),false);
EXCEPTION WHEN invalid_text_representation OR numeric_value_out_of_range OR no_data_found THEN RETURN false;
END $$;
CREATE FUNCTION lending_group_disclosure_submission_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.state='SUBMITTED' AND OLD.state='DRAFT' AND EXISTS(
   SELECT 1 FROM (
    SELECT loan_id FROM lending_group_participant WHERE cycle_id=NEW.id AND loan_id IS NOT NULL
    UNION ALL SELECT loan_id FROM lending_group_facility WHERE cycle_id=NEW.id
    UNION ALL SELECT loan_id FROM lending_group_shared_facility WHERE cycle_id=NEW.id
   ) x JOIN loan_applications l ON l.id=x.loan_id
   WHERE NOT lending_group_disclosure_valid(NEW.policy_id,l.financial_snapshot->'groupDisclosure')
 ) THEN RAISE EXCEPTION 'Configured group disclosure evidence required'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER lending_group_disclosure_submission_guard BEFORE UPDATE ON lending_group_cycle
 FOR EACH ROW EXECUTE FUNCTION lending_group_disclosure_submission_guard();

ALTER TABLE lending_group_consent_evidence ADD COLUMN preferred_language varchar(2) CHECK(preferred_language IN ('EN','SW'));
ALTER TABLE lending_group_org_signature ADD COLUMN preferred_language varchar(2) CHECK(preferred_language IN ('EN','SW'));
ALTER TABLE lending_group_shared_payout ADD COLUMN preferred_language varchar(2) CHECK(preferred_language IN ('EN','SW'));
CREATE FUNCTION lending_group_disclosure_language_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE r jsonb;doc_language text;
BEGIN
 SELECT p.release_rules INTO r FROM lending_group_cycle c JOIN lending_group_policy p ON p.id=c.policy_id WHERE c.id=NEW.cycle_id;
 IF coalesce((r->>'languageChoiceRequired')::boolean,false) THEN
  IF NEW.preferred_language IS NULL OR (r->>'agreementLanguage'<>'BOTH' AND NEW.preferred_language<>r->>'agreementLanguage') THEN
   RAISE EXCEPTION 'Contract language choice required'; END IF;
  IF TG_TABLE_NAME='lending_group_org_signature' THEN
   SELECT d.language INTO doc_language FROM lending_group_org_terms d JOIN lending_group_org_agreement a ON a.snapshot->'document'->>'id'=d.id::text WHERE a.cycle_id=NEW.cycle_id;
  ELSIF TG_TABLE_NAME='lending_group_shared_payout' THEN
   SELECT metadata->>'documentLanguage' INTO doc_language FROM lending_group_shared_document WHERE id=NEW.document_id;
  END IF;
  IF doc_language IS NOT NULL AND doc_language<>'BOTH' AND doc_language<>NEW.preferred_language THEN RAISE EXCEPTION 'Agreement language differs from chosen language';END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER lending_group_consent_language_guard BEFORE INSERT ON lending_group_consent_evidence FOR EACH ROW EXECUTE FUNCTION lending_group_disclosure_language_guard();
CREATE TRIGGER lending_group_org_language_guard BEFORE INSERT ON lending_group_org_signature FOR EACH ROW EXECUTE FUNCTION lending_group_disclosure_language_guard();
CREATE TRIGGER lending_group_shared_language_guard BEFORE INSERT ON lending_group_shared_payout FOR EACH ROW EXECUTE FUNCTION lending_group_disclosure_language_guard();
