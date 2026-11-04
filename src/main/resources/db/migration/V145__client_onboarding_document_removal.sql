CREATE TABLE client_onboarding_document_removal (
    document_id uuid PRIMARY KEY,
    revision_id uuid NOT NULL,
    client_id uuid NOT NULL,
    sacco_id text NOT NULL,
    station_id text NOT NULL,
    recorded_at timestamptz NOT NULL,
    FOREIGN KEY (document_id,client_id,sacco_id,station_id)
        REFERENCES client_onboarding_document(id,client_id,sacco_id,station_id),
    FOREIGN KEY (revision_id,client_id,sacco_id,station_id)
        REFERENCES client_onboarding_revision(id,client_id,sacco_id,station_id)
);

CREATE TRIGGER onboarding_immutable BEFORE UPDATE OR DELETE ON client_onboarding_document_removal
    FOR EACH ROW EXECUTE FUNCTION gl_protect_evidence();
CREATE TRIGGER onboarding_no_truncate BEFORE TRUNCATE ON client_onboarding_document_removal
    FOR EACH STATEMENT EXECUTE FUNCTION gl_protect_evidence();

CREATE FUNCTION client_onboarding_removal_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    PERFORM pg_advisory_xact_lock(hashtextextended('CLIENT_KYC/'||NEW.client_id,0));
    IF NOT EXISTS (
        SELECT 1 FROM client_onboarding_current
        WHERE id=NEW.revision_id AND client_id=NEW.client_id AND sacco_id=NEW.sacco_id
          AND station_id=NEW.station_id AND state='DRAFT'
    ) THEN RAISE EXCEPTION 'kyc.error.changed'; END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER onboarding_current_draft_removal BEFORE INSERT ON client_onboarding_document_removal
    FOR EACH ROW EXECUTE FUNCTION client_onboarding_removal_guard();
