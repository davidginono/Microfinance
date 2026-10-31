-- Optional client/spouse photos use the existing private, append-only evidence store.
ALTER TABLE client_onboarding_document DROP CONSTRAINT client_onboarding_document_category_check;
ALTER TABLE client_onboarding_document ADD CONSTRAINT client_onboarding_document_category_check
 CHECK(category IN ('IDENTITY','ADDRESS','ACTIVITY','CONSENT','PROFILE_PHOTO','SPOUSE_PHOTO'));
ALTER TABLE client_onboarding_document ADD CONSTRAINT ck_onboarding_photo_no_expiry
 CHECK(category NOT IN ('PROFILE_PHOTO','SPOUSE_PHOTO') OR expires_on IS NULL);

-- All previous profile snapshots and decisions are preserved. Photos are optional, never substitute for evidence.
CREATE OR REPLACE FUNCTION client_onboarding_complete(revision uuid,day date) RETURNS boolean LANGUAGE sql STABLE AS $$
 SELECT coalesce((SELECT mode='SUBMITTED' AND length(identity_key)=64
  AND profile->>'identityType' IN ('NATIONAL_ID','PASSPORT','VOTER_ID','DRIVING_LICENCE','OTHER')
  AND length(trim(profile->>'identityNumber'))>=4 AND length(profile->>'issuingCountry')=2
  AND (profile->>'phone') ~ '^255[0-9]{9}$'
  AND (profile->>'birthDate')::date BETWEEN '1901-01-01'::date AND day
  AND (((profile->>'identityNoExpiry')::boolean AND profile->>'identityExpiresOn' IS NULL)
       OR (NOT (profile->>'identityNoExpiry')::boolean AND (profile->>'identityExpiresOn')::date>=day))
  AND profile->>'lawfulBasis' IN ('CONSENT','CONTRACT','LEGAL_OBLIGATION')
  AND (profile->>'consentOn')::date BETWEEN '1901-01-01'::date AND day AND (profile->>'retentionReviewOn')::date>=day
  AND NOT EXISTS(SELECT 1 FROM unnest(ARRAY['fullName','address','activity','activityAddress','sourceOfFunds','noticeVersion','consentReference','retentionReference']) k WHERE coalesce(length(trim(profile->>k)),0)=0)
  AND (SELECT count(DISTINCT d.category) FROM client_onboarding_document_link x JOIN client_onboarding_document d ON d.id=x.document_id WHERE x.revision_id=r.id AND (d.expires_on IS NULL OR d.expires_on>=day) AND d.category IN ('IDENTITY','ADDRESS','ACTIVITY','CONSENT') AND (NOT coalesce((r.profile->>'selfService')::boolean,false) OR d.category<>'CONSENT'))=CASE WHEN coalesce((r.profile->>'selfService')::boolean,false) THEN 3 ELSE 4 END
  AND (NOT coalesce((r.profile->>'selfService')::boolean,false) OR (
    coalesce((r.profile->>'noticeAcknowledged')::boolean,false) AND coalesce((r.profile->>'declarationAccepted')::boolean,false)
    AND (r.profile->>'acknowledgedAt')::timestamptz IS NOT NULL
    AND EXISTS(SELECT 1 FROM client_onboarding_notice n WHERE n.id=(r.profile->>'noticeId')::uuid
      AND n.sacco_id=r.sacco_id AND n.station_id=r.station_id AND n.enabled
      AND n.version=r.profile->>'noticeVersion' AND n.english=r.profile->>'noticeEnglish' AND n.kiswahili=r.profile->>'noticeKiswahili'
      AND n.lawful_basis=r.profile->>'lawfulBasis' AND n.retention_reference=r.profile->>'retentionReference'
      AND n.retention_review_on=(r.profile->>'retentionReviewOn')::date)))
  FROM client_onboarding_revision r WHERE id=revision),false)
$$;

CREATE FUNCTION client_onboarding_profile_group_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.profile->>'groupId' IS NOT NULL AND NOT EXISTS(
  SELECT 1 FROM lending_group WHERE id=(NEW.profile->>'groupId')::uuid
    AND sacco_id=NEW.sacco_id AND station_id=NEW.station_id AND status='ACTIVE')
 THEN RAISE EXCEPTION 'kyc.error.groupId'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER onboarding_profile_group_scope BEFORE INSERT ON client_onboarding_revision
 FOR EACH ROW EXECUTE FUNCTION client_onboarding_profile_group_guard();
