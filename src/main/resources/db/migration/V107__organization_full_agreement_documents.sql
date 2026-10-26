-- Approved terms are retained as exact PDF bytes, separately from mutable operational uploads.
CREATE TABLE lending_group_org_terms (
 id uuid PRIMARY KEY,cycle_id uuid NOT NULL,sacco_id varchar(255) NOT NULL,station_id varchar(255) NOT NULL,
 revision integer NOT NULL CHECK(revision>0),previous_id uuid,request_key uuid NOT NULL,payload_hash varchar(64) NOT NULL,
 filename varchar(200) NOT NULL,language varchar(8) NOT NULL CHECK(language IN ('EN','SW','BOTH')),body bytea NOT NULL CHECK(octet_length(body) BETWEEN 1 AND 5242880),
 sha256 varchar(64) NOT NULL CHECK(sha256=encode(sha256(body),'hex')),page_count integer NOT NULL CHECK(page_count BETWEEN 1 AND 50),
 reference varchar(500) NOT NULL CHECK(length(trim(reference))>0),
 institution_name varchar(255) NOT NULL CHECK(length(trim(institution_name))>0),institution_address varchar(500) NOT NULL CHECK(length(trim(institution_address))>0),
 contact_phone varchar(80) NOT NULL,contact_email varchar(160) NOT NULL,
 complaint_phone varchar(80) NOT NULL,complaint_email varchar(160) NOT NULL,complaint_address varchar(500) NOT NULL,
 recorded_by uuid NOT NULL REFERENCES members(id),recorded_at timestamptz NOT NULL,
 CHECK(contact_phone<>'' OR contact_email<>''),CHECK(complaint_phone<>'' OR complaint_email<>''),
 CHECK(substring(body FROM 1 FOR 5)=convert_to('%PDF-','UTF8')),
 UNIQUE(id,cycle_id,sacco_id,station_id),UNIQUE(cycle_id,revision),UNIQUE(sacco_id,station_id,request_key),
 FOREIGN KEY(cycle_id,sacco_id,station_id) REFERENCES lending_group_cycle(id,sacco_id,station_id),
 FOREIGN KEY(previous_id,cycle_id,sacco_id,station_id) REFERENCES lending_group_org_terms(id,cycle_id,sacco_id,station_id)
);
CREATE UNIQUE INDEX ux_group_org_terms_successor ON lending_group_org_terms(previous_id) WHERE previous_id IS NOT NULL;
CREATE INDEX ix_group_org_terms_latest ON lending_group_org_terms(cycle_id,revision DESC);
CREATE TRIGGER group_org_terms_immutable BEFORE UPDATE OR DELETE ON lending_group_org_terms FOR EACH ROW EXECUTE FUNCTION gl_protect_evidence();
CREATE FUNCTION lending_group_org_terms_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE c lending_group_cycle; prior lending_group_org_terms;
BEGIN
 SELECT * INTO c FROM lending_group_cycle WHERE id=NEW.cycle_id FOR UPDATE;
 SELECT * INTO prior FROM lending_group_org_terms WHERE cycle_id=NEW.cycle_id ORDER BY revision DESC LIMIT 1;
 IF c.id IS NULL OR c.model<>'ORGANIZATION' OR c.state<>'DRAFT' OR c.sacco_id<>NEW.sacco_id OR c.station_id<>NEW.station_id
  OR NEW.previous_id IS DISTINCT FROM prior.id OR NEW.revision<>coalesce(prior.revision,0)+1
  OR EXISTS(SELECT 1 FROM lending_group_facility f WHERE f.cycle_id=c.id)
  OR NOT EXISTS(SELECT 1 FROM members m WHERE m.id=NEW.recorded_by AND m.sacco_id=NEW.sacco_id AND m.station_id=NEW.station_id AND m.status='ACTIVE')
  OR NEW.institution_name IS DISTINCT FROM (SELECT sacco_name FROM registered_saccos WHERE sacco_id=NEW.sacco_id AND active)
 THEN RAISE EXCEPTION 'Organization terms require the current scoped draft and institution'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER group_org_terms_guard BEFORE INSERT ON lending_group_org_terms FOR EACH ROW EXECUTE FUNCTION lending_group_org_terms_guard();

-- Existing packets and signatures stay unchanged. Only newly prepared contracts include the selected document.
DO $$ DECLARE definition text; BEGIN
 SELECT pg_get_functiondef('lending_group_org_document(uuid)'::regprocedure) INTO definition;
 IF strpos(definition,'''financial'',l.financial_snapshot')=0 THEN RAISE EXCEPTION 'Organization document source unavailable'; END IF;
 definition=replace(definition,'''financial'',l.financial_snapshot', $replacement$
 'document',(SELECT jsonb_build_object('id',d.id,'revision',d.revision,'filename',d.filename,'sha256',d.sha256,
   'language',d.language,'sizeBytes',octet_length(d.body),'pageCount',d.page_count,'reference',d.reference,'institutionName',d.institution_name,
   'branch',d.station_id,'institutionAddress',d.institution_address,'contactPhone',d.contact_phone,'contactEmail',d.contact_email,
   'complaintPhone',d.complaint_phone,'complaintEmail',d.complaint_email,'complaintAddress',d.complaint_address,'recordedAt',d.recorded_at)
  FROM lending_group_org_terms d WHERE d.cycle_id=f.cycle_id ORDER BY revision DESC LIMIT 1),
 'financial',l.financial_snapshot$replacement$);
 EXECUTE definition;
END $$;

CREATE FUNCTION lending_group_org_facility_document_guard() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF NOT EXISTS(SELECT 1 FROM lending_group_org_agreement p JOIN lending_group_org_terms d ON d.cycle_id=p.cycle_id
   WHERE p.cycle_id=NEW.cycle_id AND p.snapshot->'document'->>'id'=d.id::text
   AND d.sacco_id=NEW.sacco_id AND d.station_id=NEW.station_id AND d.recorded_at<=NEW.created_at)
 THEN RAISE EXCEPTION 'Organization origination requires its frozen full agreement document'; END IF;RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER group_org_facility_document_guard AFTER INSERT ON lending_group_facility DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION lending_group_org_facility_document_guard();

CREATE TABLE lending_group_org_document_access (
 cycle_id uuid NOT NULL,client_id uuid NOT NULL REFERENCES members(id),document_id uuid NOT NULL,
 sacco_id varchar(255) NOT NULL,station_id varchar(255) NOT NULL,packet_hash varchar(64) NOT NULL,requested_at timestamptz NOT NULL,
 PRIMARY KEY(cycle_id,client_id,document_id),
 FOREIGN KEY(cycle_id) REFERENCES lending_group_org_agreement(cycle_id),
 FOREIGN KEY(document_id,cycle_id,sacco_id,station_id) REFERENCES lending_group_org_terms(id,cycle_id,sacco_id,station_id)
);
CREATE TRIGGER group_org_document_access_immutable BEFORE UPDATE OR DELETE ON lending_group_org_document_access FOR EACH ROW EXECUTE FUNCTION gl_protect_evidence();
CREATE FUNCTION lending_group_org_document_access_guard() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF NOT EXISTS(SELECT 1 FROM lending_group_org_agreement p JOIN lending_group_authority_signer s ON s.authority_id=p.authority_id
   WHERE p.cycle_id=NEW.cycle_id AND p.sacco_id=NEW.sacco_id AND p.station_id=NEW.station_id AND s.client_id=NEW.client_id AND s.may_sign
   AND p.packet_hash=NEW.packet_hash AND p.snapshot->'document'->>'id'=NEW.document_id::text AND NEW.requested_at>=p.prepared_at)
 THEN RAISE EXCEPTION 'Document access must identify the exact scoped agreement and signatory'; END IF;RETURN NEW;
END $$;
CREATE TRIGGER group_org_document_access_guard BEFORE INSERT ON lending_group_org_document_access FOR EACH ROW EXECUTE FUNCTION lending_group_org_document_access_guard();
CREATE FUNCTION lending_group_org_full_document_signature_guard() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF NOT EXISTS(SELECT 1 FROM lending_group_org_agreement p JOIN lending_group_org_document_access d ON d.cycle_id=p.cycle_id
   WHERE p.cycle_id=NEW.cycle_id AND d.client_id=NEW.client_id AND d.packet_hash=NEW.packet_hash
   AND p.snapshot->'document'->>'id'=d.document_id::text AND d.requested_at<=NEW.signed_at)
 THEN RAISE EXCEPTION 'Open the complete frozen organization agreement before signing'; END IF;RETURN NEW;
END $$;
CREATE TRIGGER group_org_full_document_signature_guard BEFORE INSERT ON lending_group_org_signature FOR EACH ROW EXECUTE FUNCTION lending_group_org_full_document_signature_guard();
