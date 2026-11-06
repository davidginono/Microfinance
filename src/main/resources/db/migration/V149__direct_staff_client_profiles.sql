-- A profile recorded by staff is neither a draft nor evidence of independent verification.
ALTER TABLE client_onboarding_revision DROP CONSTRAINT client_onboarding_revision_mode_check;
ALTER TABLE client_onboarding_revision ADD CONSTRAINT client_onboarding_revision_mode_check
 CHECK (mode IN ('DRAFT','RECORDED','SUBMITTED'));

CREATE OR REPLACE FUNCTION client_onboarding_self_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE staff_record boolean;
BEGIN
 PERFORM pg_advisory_xact_lock(hashtextextended('CLIENT_KYC/'||NEW.client_id,0));
 IF TG_TABLE_NAME IN ('client_onboarding_revision','client_onboarding_document') THEN
  staff_record := NEW.actor_id<>NEW.client_id AND EXISTS(
   SELECT 1 FROM members WHERE id=NEW.client_id AND sacco_id=NEW.sacco_id AND station_id=NEW.station_id
    AND NOT client_portal_enabled);
 END IF;
 IF TG_TABLE_NAME='client_onboarding_revision' THEN
  IF NEW.mode='RECORDED' AND (NOT staff_record OR coalesce((NEW.profile->>'selfService')::boolean,false))
   THEN RAISE EXCEPTION 'kyc.error.scope';END IF;
  IF EXISTS(SELECT 1 FROM client_onboarding_current c WHERE c.client_id=NEW.client_id AND c.state='SUBMITTED'
    AND NOT (staff_record AND NEW.mode IN ('DRAFT','RECORDED') AND c.actor_id<>c.client_id
             AND NOT coalesce((c.profile->>'selfService')::boolean,false)))
   THEN RAISE EXCEPTION 'kyc.error.state';END IF;
  IF coalesce((NEW.profile->>'selfService')::boolean,false) AND NEW.actor_id<>NEW.client_id THEN RAISE EXCEPTION 'kyc.error.scope';END IF;
 ELSIF TG_TABLE_NAME='client_onboarding_document' THEN
  IF NOT EXISTS(SELECT 1 FROM client_onboarding_current WHERE client_id=NEW.client_id AND sacco_id=NEW.sacco_id AND station_id=NEW.station_id
    AND (state='DRAFT' OR (state='RECORDED' AND staff_record))) THEN RAISE EXCEPTION 'kyc.error.state';END IF;
 ELSIF TG_TABLE_NAME='client_onboarding_decision' THEN
  IF NEW.kind IN ('RETURN','REJECT','REVOKE') AND length(trim(NEW.client_message))=0 THEN RAISE EXCEPTION 'kyc.error.clientMessage';END IF;
 END IF;
 RETURN NEW;
END $$;

CREATE OR REPLACE FUNCTION client_onboarding_link_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 PERFORM pg_advisory_xact_lock(hashtextextended('CLIENT_KYC/'||NEW.client_id,0));
 IF NOT EXISTS(SELECT 1 FROM client_onboarding_revision r WHERE r.id=NEW.revision_id
   AND r.client_id=NEW.client_id AND r.sacco_id=NEW.sacco_id AND r.station_id=NEW.station_id
   AND NOT EXISTS(SELECT 1 FROM client_onboarding_revision newer WHERE newer.client_id=r.client_id AND newer.sequence>r.sequence)
   AND (r.mode='DRAFT' OR r.xmin=pg_current_xact_id()::text::xid
        OR (r.mode='RECORDED' AND EXISTS(SELECT 1 FROM members WHERE id=r.client_id AND NOT client_portal_enabled))))
  THEN RAISE EXCEPTION 'kyc.error.state';END IF;
 RETURN NEW;
END $$;
