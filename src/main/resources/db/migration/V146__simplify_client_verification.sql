-- Retain historical evidence and append-only protections while retiring four review inputs.
ALTER TABLE client_onboarding_decision DROP CONSTRAINT client_onboarding_decision_check;
ALTER TABLE client_onboarding_decision ADD CONSTRAINT client_onboarding_verification_checks
 CHECK(kind<>'VERIFY' OR (identity_checked AND address_checked AND activity_checked
  AND contact_checked AND duplicates_checked AND checked_on IS NOT NULL));

CREATE OR REPLACE FUNCTION client_kyc_error(client uuid,institution text,branch text,day date) RETURNS text LANGUAGE plpgsql AS $$
DECLARE c client_onboarding_current;
BEGIN
 SELECT * INTO c FROM client_onboarding_current WHERE client_id=client AND sacco_id=institution AND station_id=branch;
 IF NOT FOUND OR c.state<>'VERIFY' THEN RETURN 'kyc.error.required';END IF;
 IF NOT EXISTS(SELECT 1 FROM members m WHERE m.id=client AND m.sacco_id=institution AND m.station_id=branch AND m.status='ACTIVE'
   AND m.full_name IS NOT DISTINCT FROM c.profile->>'fullName' AND m.phone IS NOT DISTINCT FROM c.profile->>'phone' AND m.email IS NOT DISTINCT FROM c.profile->>'email') THEN RETURN 'kyc.error.contactChanged';END IF;
 IF NOT client_onboarding_complete(c.id,day) OR (NOT (c.profile->>'identityNoExpiry')::boolean AND (c.profile->>'identityExpiresOn')::date<day)
  OR (c.profile->>'retentionReviewOn')::date<day
  OR EXISTS(SELECT 1 FROM client_onboarding_document_link x JOIN client_onboarding_document d ON d.id=x.document_id WHERE x.revision_id=c.id AND d.expires_on<day)
 THEN RETURN 'kyc.error.expired';END IF;
 RETURN NULL;
END $$;
