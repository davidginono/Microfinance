-- Manual, evidenced individual-client KYC. No identity-provider result or claim is seeded.
ALTER TABLE lending_credit_policy ADD COLUMN kyc_required boolean NOT NULL DEFAULT false;
CREATE TABLE client_onboarding_creation (
 id uuid PRIMARY KEY, client_id uuid NOT NULL REFERENCES members(id), sacco_id text NOT NULL, station_id text NOT NULL,
 request_key uuid NOT NULL, payload_hash varchar(64) NOT NULL, actor_id uuid NOT NULL REFERENCES members(id), recorded_at timestamptz NOT NULL,
 UNIQUE(sacco_id,station_id,request_key)
);
CREATE TABLE client_onboarding_revision (
 id uuid PRIMARY KEY, client_id uuid NOT NULL REFERENCES members(id), sacco_id text NOT NULL, station_id text NOT NULL,
 sequence integer NOT NULL CHECK(sequence>0), mode text NOT NULL CHECK(mode IN ('DRAFT','SUBMITTED')), profile jsonb NOT NULL,
 identity_key varchar(64), request_key uuid NOT NULL, payload_hash varchar(64) NOT NULL,
 actor_id uuid NOT NULL REFERENCES members(id), recorded_at timestamptz NOT NULL,
 UNIQUE(client_id,sequence), UNIQUE(id,client_id,sacco_id,station_id), UNIQUE(sacco_id,station_id,request_key)
);
CREATE INDEX ix_onboarding_scope ON client_onboarding_revision(sacco_id,station_id,client_id,sequence DESC);
CREATE INDEX ix_onboarding_identity ON client_onboarding_revision(sacco_id,identity_key,client_id,sequence DESC) WHERE identity_key IS NOT NULL;
CREATE TABLE client_onboarding_document (
 id uuid PRIMARY KEY, client_id uuid NOT NULL REFERENCES members(id), sacco_id text NOT NULL, station_id text NOT NULL,
 upload_id uuid NOT NULL UNIQUE REFERENCES stored_uploads(id), category text NOT NULL CHECK(category IN ('IDENTITY','ADDRESS','ACTIVITY','CONSENT')),
 expires_on date, request_key uuid NOT NULL, payload_hash varchar(64) NOT NULL,
 actor_id uuid NOT NULL REFERENCES members(id), recorded_at timestamptz NOT NULL, sequence integer NOT NULL CHECK(sequence>0),
 UNIQUE(id,client_id,sacco_id,station_id), UNIQUE(sacco_id,station_id,request_key), UNIQUE(client_id,sequence)
);
CREATE TABLE client_onboarding_document_link (
 revision_id uuid NOT NULL, document_id uuid NOT NULL, client_id uuid NOT NULL, sacco_id text NOT NULL, station_id text NOT NULL,
 PRIMARY KEY(revision_id,document_id),
 FOREIGN KEY(revision_id,client_id,sacco_id,station_id) REFERENCES client_onboarding_revision(id,client_id,sacco_id,station_id),
 FOREIGN KEY(document_id,client_id,sacco_id,station_id) REFERENCES client_onboarding_document(id,client_id,sacco_id,station_id)
);
CREATE TABLE client_onboarding_decision (
 id uuid PRIMARY KEY, revision_id uuid NOT NULL, client_id uuid NOT NULL, sacco_id text NOT NULL, station_id text NOT NULL,
 kind text NOT NULL CHECK(kind IN ('VERIFY','RETURN','REJECT','REVOKE')), reason varchar(1000) NOT NULL CHECK(length(trim(reason))>0),
 identity_checked boolean NOT NULL, address_checked boolean NOT NULL, activity_checked boolean NOT NULL, contact_checked boolean NOT NULL, duplicates_checked boolean NOT NULL,
 screening text NOT NULL CHECK(screening IN ('CLEARED','ESCALATED','UNAVAILABLE')), screening_reference varchar(1000) NOT NULL,
 checked_on date, review_due date, policy_reference varchar(500) NOT NULL,
 request_key uuid NOT NULL, payload_hash varchar(64) NOT NULL, actor_id uuid NOT NULL REFERENCES members(id), recorded_at timestamptz NOT NULL,
 FOREIGN KEY(revision_id,client_id,sacco_id,station_id) REFERENCES client_onboarding_revision(id,client_id,sacco_id,station_id),
 CHECK(kind<>'VERIFY' OR (identity_checked AND address_checked AND activity_checked AND contact_checked AND duplicates_checked AND screening='CLEARED'
   AND length(trim(screening_reference))>0 AND length(trim(policy_reference))>0 AND checked_on IS NOT NULL AND review_due IS NOT NULL AND review_due>=checked_on)),
 UNIQUE(sacco_id,station_id,request_key)
);
CREATE UNIQUE INDEX uq_onboarding_decision ON client_onboarding_decision(revision_id) WHERE kind<>'REVOKE';
CREATE UNIQUE INDEX uq_onboarding_revocation ON client_onboarding_decision(revision_id) WHERE kind='REVOKE';
CREATE INDEX ix_onboarding_decision_history ON client_onboarding_decision(revision_id,recorded_at DESC);
CREATE INDEX ix_onboarding_client_register ON members(sacco_id,station_id,member_no,id)
 WHERE is_member OR (is_member IS NULL AND position='MEMBER');
CREATE TABLE client_onboarding_identity (
 sacco_id text NOT NULL, identity_key varchar(64) NOT NULL, client_id uuid NOT NULL REFERENCES members(id),
 decision_id uuid NOT NULL UNIQUE REFERENCES client_onboarding_decision(id), PRIMARY KEY(sacco_id,identity_key)
);
CREATE VIEW client_onboarding_current AS
 SELECT r.*,coalesce(d.kind,r.mode) state,d.review_due,d.checked_on
 FROM (SELECT DISTINCT ON(client_id) * FROM client_onboarding_revision ORDER BY client_id,sequence DESC) r
 LEFT JOIN LATERAL(SELECT kind,review_due,checked_on FROM client_onboarding_decision WHERE revision_id=r.id ORDER BY recorded_at DESC,(kind='REVOKE') DESC LIMIT 1) d ON true;

CREATE FUNCTION client_onboarding_complete(revision uuid,day date) RETURNS boolean LANGUAGE sql STABLE AS $$
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
  AND (SELECT count(DISTINCT d.category) FROM client_onboarding_document_link x JOIN client_onboarding_document d ON d.id=x.document_id WHERE x.revision_id=r.id AND (d.expires_on IS NULL OR d.expires_on>=day))=4
  FROM client_onboarding_revision r WHERE id=revision),false)
$$;

CREATE FUNCTION client_kyc_error(client uuid,institution text,branch text,day date) RETURNS text LANGUAGE plpgsql AS $$
DECLARE c client_onboarding_current;
BEGIN
 SELECT * INTO c FROM client_onboarding_current WHERE client_id=client AND sacco_id=institution AND station_id=branch;
 IF NOT FOUND OR c.state<>'VERIFY' THEN RETURN 'kyc.error.required';END IF;
 IF NOT EXISTS(SELECT 1 FROM members m WHERE m.id=client AND m.sacco_id=institution AND m.station_id=branch AND m.status='ACTIVE'
   AND m.full_name IS NOT DISTINCT FROM c.profile->>'fullName' AND m.phone IS NOT DISTINCT FROM c.profile->>'phone' AND m.email IS NOT DISTINCT FROM c.profile->>'email') THEN RETURN 'kyc.error.contactChanged';END IF;
 IF c.review_due IS NULL OR NOT client_onboarding_complete(c.id,day) OR c.review_due<day OR (NOT (c.profile->>'identityNoExpiry')::boolean AND (c.profile->>'identityExpiresOn')::date<day)
  OR (c.profile->>'retentionReviewOn')::date<day
  OR EXISTS(SELECT 1 FROM client_onboarding_document_link x JOIN client_onboarding_document d ON d.id=x.document_id WHERE x.revision_id=c.id AND d.expires_on<day)
 THEN RETURN 'kyc.error.expired';END IF;
 RETURN NULL;
END $$;
CREATE FUNCTION client_kyc_loan_check(application uuid,institution text,branch text,day date) RETURNS text LANGUAGE plpgsql AS $$
DECLARE l loan_applications; enabled boolean;
BEGIN
 SELECT * INTO l FROM loan_applications WHERE id=application AND sacco_id=institution AND station_id=branch FOR UPDATE;
 IF NOT FOUND THEN RETURN 'kyc.error.scope';END IF;
 PERFORM pg_advisory_xact_lock(hashtextextended('CREDIT_POLICY/'||institution||'/'||branch,0));
 SELECT kyc_required INTO enabled FROM lending_credit_policy WHERE sacco_id=institution AND station_id=branch ORDER BY revision DESC LIMIT 1;
 -- Organization/pooled borrowers keep their specialized onboarding requirements.
 IF NOT coalesce(enabled,false) OR l.applicant_member_id IS NULL THEN RETURN NULL;END IF;
 PERFORM pg_advisory_xact_lock(hashtextextended('CLIENT_KYC/'||l.applicant_member_id,0));
 RETURN client_kyc_error(l.applicant_member_id,institution,branch,day);
END $$;
CREATE FUNCTION client_kyc_loan_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE failure text;
BEGIN
 IF (TG_OP='INSERT' AND NEW.status NOT IN ('DRAFT','RECORDED','WITHDRAWN')) OR
  (TG_OP='UPDATE' AND ((OLD.status IN ('DRAFT','RECORDED') AND NEW.status NOT IN ('DRAFT','RECORDED','WITHDRAWN')) OR (NEW.status='DISBURSED' AND OLD.status<>'DISBURSED'))) THEN
  PERFORM pg_advisory_xact_lock(hashtextextended('CREDIT_POLICY/'||NEW.sacco_id||'/'||NEW.station_id,0));
  IF NEW.applicant_member_id IS NOT NULL AND coalesce((SELECT kyc_required FROM lending_credit_policy WHERE sacco_id=NEW.sacco_id AND station_id=NEW.station_id ORDER BY revision DESC LIMIT 1),false) THEN
   PERFORM pg_advisory_xact_lock(hashtextextended('CLIENT_KYC/'||NEW.applicant_member_id,0));
   failure=client_kyc_error(NEW.applicant_member_id,NEW.sacco_id,NEW.station_id,(current_timestamp AT TIME ZONE 'Africa/Dar_es_Salaam')::date);
   IF failure IS NOT NULL THEN RAISE EXCEPTION '%',failure;END IF;
  END IF;
 END IF;RETURN NEW;
END $$;
CREATE TRIGGER client_kyc_lending BEFORE INSERT OR UPDATE ON loan_applications FOR EACH ROW EXECUTE FUNCTION client_kyc_loan_guard();

CREATE FUNCTION client_onboarding_scope_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM members m WHERE m.id=NEW.client_id AND m.sacco_id=NEW.sacco_id AND m.station_id=NEW.station_id AND (m.is_member OR (m.is_member IS NULL AND m.position='MEMBER')))
  OR NOT EXISTS(SELECT 1 FROM members a WHERE a.id=NEW.actor_id AND a.sacco_id=NEW.sacco_id AND a.station_id=NEW.station_id AND a.status='ACTIVE') THEN RAISE EXCEPTION 'kyc.error.scope';END IF;
 IF TG_TABLE_NAME='client_onboarding_revision' THEN
  PERFORM pg_advisory_xact_lock(hashtextextended('CLIENT_KYC/'||NEW.client_id,0));
  IF NEW.sequence<>(SELECT coalesce(max(sequence),0)+1 FROM client_onboarding_revision WHERE client_id=NEW.client_id) THEN RAISE EXCEPTION 'kyc.error.changed';END IF;
 ELSIF TG_TABLE_NAME='client_onboarding_decision' THEN
  PERFORM pg_advisory_xact_lock(hashtextextended('CLIENT_KYC/'||NEW.client_id,0));
  IF NOT EXISTS(SELECT 1 FROM client_onboarding_current WHERE id=NEW.revision_id AND mode='SUBMITTED' AND ((NEW.kind='REVOKE' AND state='VERIFY') OR (NEW.kind<>'REVOKE' AND state='SUBMITTED'))) THEN RAISE EXCEPTION 'kyc.error.state';END IF;
  IF NEW.kind='VERIFY' THEN
   IF NEW.actor_id=NEW.client_id OR NEW.actor_id=(SELECT actor_id FROM client_onboarding_revision WHERE id=NEW.revision_id)
    OR EXISTS(SELECT 1 FROM client_onboarding_document_link x JOIN client_onboarding_document d ON d.id=x.document_id WHERE x.revision_id=NEW.revision_id AND d.actor_id=NEW.actor_id) THEN RAISE EXCEPTION 'kyc.error.independent';END IF;
   IF NOT client_onboarding_complete(NEW.revision_id,NEW.checked_on) THEN RAISE EXCEPTION 'kyc.error.documents';END IF;
  END IF;
 END IF;RETURN NEW;
END $$;
DO $$ DECLARE t text;BEGIN
 FOREACH t IN ARRAY ARRAY['client_onboarding_creation','client_onboarding_revision','client_onboarding_document','client_onboarding_decision'] LOOP
  EXECUTE format('CREATE TRIGGER onboarding_scope BEFORE INSERT ON %I FOR EACH ROW EXECUTE FUNCTION client_onboarding_scope_guard()',t);
 END LOOP;
 FOREACH t IN ARRAY ARRAY['client_onboarding_creation','client_onboarding_revision','client_onboarding_document','client_onboarding_document_link','client_onboarding_decision','client_onboarding_identity'] LOOP
  EXECUTE format('CREATE TRIGGER onboarding_immutable BEFORE UPDATE OR DELETE ON %I FOR EACH ROW EXECUTE FUNCTION gl_protect_evidence()',t);
  EXECUTE format('CREATE TRIGGER onboarding_no_truncate BEFORE TRUNCATE ON %I FOR EACH STATEMENT EXECUTE FUNCTION gl_protect_evidence()',t);
 END LOOP;
END $$;
CREATE FUNCTION client_onboarding_upload_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF EXISTS(SELECT 1 FROM client_onboarding_document WHERE upload_id=OLD.id) AND
  (TG_OP='DELETE' OR NEW.sha256 IS DISTINCT FROM OLD.sha256 OR NEW.size_bytes IS DISTINCT FROM OLD.size_bytes
   OR NEW.owner_type IS DISTINCT FROM OLD.owner_type OR NEW.owner_id IS DISTINCT FROM OLD.owner_id OR NEW.category IS DISTINCT FROM OLD.category) THEN RAISE EXCEPTION 'KYC evidence is permanent';END IF;
 IF TG_OP='DELETE' THEN RETURN OLD;END IF;RETURN NEW;
END $$;
CREATE TRIGGER onboarding_upload_preserve BEFORE UPDATE OR DELETE ON stored_uploads FOR EACH ROW EXECUTE FUNCTION client_onboarding_upload_guard();

CREATE FUNCTION client_onboarding_reserve_identity() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE identity text;
BEGIN
 IF NEW.kind<>'VERIFY' THEN RETURN NEW;END IF;
 SELECT identity_key INTO identity FROM client_onboarding_revision WHERE id=NEW.revision_id;
 PERFORM pg_advisory_xact_lock(hashtextextended('CLIENT_IDENTITY/'||NEW.sacco_id||'/'||identity,0));
 IF EXISTS(SELECT 1 FROM client_onboarding_revision r WHERE r.sacco_id=NEW.sacco_id AND r.identity_key=identity AND r.client_id<>NEW.client_id AND r.mode='SUBMITTED'
   AND NOT EXISTS(SELECT 1 FROM client_onboarding_revision newer WHERE newer.client_id=r.client_id AND newer.sequence>r.sequence)
   AND NOT EXISTS(SELECT 1 FROM client_onboarding_decision d WHERE d.revision_id=r.id AND d.kind IN ('RETURN','REJECT','REVOKE'))) THEN RAISE EXCEPTION 'kyc.error.duplicate';END IF;
 INSERT INTO client_onboarding_identity VALUES(NEW.sacco_id,identity,NEW.client_id,NEW.id) ON CONFLICT(sacco_id,identity_key) DO NOTHING;
 IF EXISTS(SELECT 1 FROM client_onboarding_identity WHERE sacco_id=NEW.sacco_id AND identity_key=identity AND client_id<>NEW.client_id) THEN RAISE EXCEPTION 'kyc.error.duplicate';END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER onboarding_identity_reserve AFTER INSERT ON client_onboarding_decision FOR EACH ROW EXECUTE FUNCTION client_onboarding_reserve_identity();
