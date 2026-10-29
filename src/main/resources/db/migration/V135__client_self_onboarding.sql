-- Client self-service uses the same immutable KYC evidence and independent review.
ALTER TABLE client_onboarding_decision ADD COLUMN client_message varchar(1000) NOT NULL DEFAULT '';
CREATE INDEX ix_onboarding_client_feedback ON client_onboarding_decision(sacco_id,station_id,client_id,revision_id,recorded_at DESC);
CREATE TABLE client_onboarding_notice (
 id uuid PRIMARY KEY, sacco_id text NOT NULL, station_id text NOT NULL,
 sequence integer NOT NULL CHECK(sequence>0), enabled boolean NOT NULL,
 version varchar(100) NOT NULL CHECK(length(trim(version))>0),
 english varchar(12000) NOT NULL CHECK(length(trim(english))>0),
 kiswahili varchar(12000) NOT NULL CHECK(length(trim(kiswahili))>0),
 lawful_basis text NOT NULL CHECK(lawful_basis IN ('CONSENT','CONTRACT','LEGAL_OBLIGATION')),
 retention_reference varchar(500) NOT NULL CHECK(length(trim(retention_reference))>0), retention_review_on date NOT NULL,
 request_key uuid NOT NULL, payload_hash varchar(64) NOT NULL, actor_id uuid NOT NULL REFERENCES members(id), recorded_at timestamptz NOT NULL,
 UNIQUE(sacco_id,station_id,sequence), UNIQUE(sacco_id,station_id,request_key)
);
CREATE TRIGGER onboarding_immutable BEFORE UPDATE OR DELETE ON client_onboarding_notice FOR EACH ROW EXECUTE FUNCTION gl_protect_evidence();
CREATE TRIGGER onboarding_no_truncate BEFORE TRUNCATE ON client_onboarding_notice FOR EACH STATEMENT EXECUTE FUNCTION gl_protect_evidence();
CREATE FUNCTION client_onboarding_notice_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 PERFORM pg_advisory_xact_lock(hashtextextended('CLIENT_KYC_NOTICE/'||NEW.sacco_id||'/'||NEW.station_id,0));
 IF NOT EXISTS(SELECT 1 FROM members WHERE id=NEW.actor_id AND sacco_id=NEW.sacco_id AND station_id=NEW.station_id AND status='ACTIVE' AND staff_access_status='ACTIVE') THEN RAISE EXCEPTION 'kyc.error.scope';END IF;
 IF NEW.sequence<>(SELECT coalesce(max(sequence),0)+1 FROM client_onboarding_notice WHERE sacco_id=NEW.sacco_id AND station_id=NEW.station_id) THEN RAISE EXCEPTION 'kyc.error.changed';END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER onboarding_notice_scope BEFORE INSERT ON client_onboarding_notice FOR EACH ROW EXECUTE FUNCTION client_onboarding_notice_guard();

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
  AND (SELECT count(DISTINCT d.category) FROM client_onboarding_document_link x JOIN client_onboarding_document d ON d.id=x.document_id WHERE x.revision_id=r.id AND (d.expires_on IS NULL OR d.expires_on>=day) AND (NOT coalesce((r.profile->>'selfService')::boolean,false) OR d.category<>'CONSENT'))=CASE WHEN coalesce((r.profile->>'selfService')::boolean,false) THEN 3 ELSE 4 END
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


-- Existing decisions retain their original reasons and are never exposed as client messages.
CREATE FUNCTION client_onboarding_self_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 PERFORM pg_advisory_xact_lock(hashtextextended('CLIENT_KYC/'||NEW.client_id,0));
 IF TG_TABLE_NAME='client_onboarding_revision' THEN
  IF EXISTS(SELECT 1 FROM client_onboarding_current WHERE client_id=NEW.client_id AND state='SUBMITTED') THEN RAISE EXCEPTION 'kyc.error.state';END IF;
  IF coalesce((NEW.profile->>'selfService')::boolean,false) AND NEW.actor_id<>NEW.client_id THEN RAISE EXCEPTION 'kyc.error.scope';END IF;
 ELSIF TG_TABLE_NAME='client_onboarding_document' THEN
  IF NOT EXISTS(SELECT 1 FROM client_onboarding_current WHERE client_id=NEW.client_id AND sacco_id=NEW.sacco_id AND station_id=NEW.station_id AND state='DRAFT') THEN RAISE EXCEPTION 'kyc.error.state';END IF;
 ELSIF TG_TABLE_NAME='client_onboarding_decision' THEN
  IF NEW.kind IN ('RETURN','REJECT','REVOKE') AND length(trim(NEW.client_message))=0 THEN RAISE EXCEPTION 'kyc.error.clientMessage';END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER onboarding_self_revision BEFORE INSERT ON client_onboarding_revision FOR EACH ROW EXECUTE FUNCTION client_onboarding_self_guard();
CREATE TRIGGER onboarding_self_document BEFORE INSERT ON client_onboarding_document FOR EACH ROW EXECUTE FUNCTION client_onboarding_self_guard();
CREATE TRIGGER onboarding_client_feedback BEFORE INSERT ON client_onboarding_decision FOR EACH ROW EXECUTE FUNCTION client_onboarding_self_guard();

-- A submitted document selection can be built only in its creation transaction.
CREATE FUNCTION client_onboarding_link_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 PERFORM pg_advisory_xact_lock(hashtextextended('CLIENT_KYC/'||NEW.client_id,0));
 IF NOT EXISTS(SELECT 1 FROM client_onboarding_revision r WHERE r.id=NEW.revision_id
   AND r.client_id=NEW.client_id AND r.sacco_id=NEW.sacco_id AND r.station_id=NEW.station_id
   AND NOT EXISTS(SELECT 1 FROM client_onboarding_revision newer WHERE newer.client_id=r.client_id AND newer.sequence>r.sequence)
   AND (r.mode='DRAFT' OR r.xmin=pg_current_xact_id()::text::xid)) THEN RAISE EXCEPTION 'kyc.error.state';END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER onboarding_frozen_links BEFORE INSERT ON client_onboarding_document_link FOR EACH ROW EXECUTE FUNCTION client_onboarding_link_guard();
