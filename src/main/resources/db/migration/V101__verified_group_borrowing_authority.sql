-- Evidence of organization authority is separate from membership, loan consent and personal guarantees.
CREATE TABLE lending_group_authority (
 id uuid PRIMARY KEY, group_id uuid NOT NULL, sacco_id varchar(255) NOT NULL, station_id varchar(255) NOT NULL,
 revision integer NOT NULL CHECK(revision>0), previous_id uuid,
 request_key uuid NOT NULL, payload_hash varchar(64) NOT NULL,
 group_name varchar(160) NOT NULL, group_code varchar(32) NOT NULL, legal_form varchar(32) NOT NULL,
 valid_from date NOT NULL, valid_through date NOT NULL CHECK(valid_through>=valid_from),
 max_principal numeric(18,2) NOT NULL CHECK(max_principal>0),
 capacity_evidence varchar(500) NOT NULL CHECK(length(trim(capacity_evidence))>0),
 resolution_evidence varchar(500) NOT NULL CHECK(length(trim(resolution_evidence))>0),
 signature_evidence varchar(500) NOT NULL CHECK(length(trim(signature_evidence))>0),
 payout_type varchar(32) NOT NULL CHECK(payout_type IN ('BANK_ACCOUNT','MOBILE_MONEY','OTHER')),
 payout_provider varchar(120) NOT NULL CHECK(length(trim(payout_provider))>0),
 payout_holder varchar(160) NOT NULL CHECK(length(trim(payout_holder))>0),
 payout_identifier varchar(160) NOT NULL CHECK(length(trim(payout_identifier))>0),
 payout_evidence varchar(500) NOT NULL CHECK(length(trim(payout_evidence))>0),
 signer_count integer NOT NULL CHECK(signer_count BETWEEN 1 AND 50),
 required_signatures integer NOT NULL CHECK(required_signatures BETWEEN 1 AND signer_count),
 recorded_by uuid NOT NULL REFERENCES members(id), recorded_by_name varchar(255) NOT NULL, recorded_at timestamptz NOT NULL,
 UNIQUE(id,group_id,sacco_id,station_id), UNIQUE(group_id,revision), UNIQUE(sacco_id,station_id,request_key),
 FOREIGN KEY(group_id,sacco_id,station_id) REFERENCES lending_group(id,sacco_id,station_id),
 FOREIGN KEY(previous_id,group_id,sacco_id,station_id) REFERENCES lending_group_authority(id,group_id,sacco_id,station_id)
);
CREATE UNIQUE INDEX ux_group_authority_successor ON lending_group_authority(previous_id) WHERE previous_id IS NOT NULL;
CREATE INDEX ix_group_authority_history ON lending_group_authority(sacco_id,station_id,group_id,revision DESC);
CREATE TABLE lending_group_authority_signer (
 authority_id uuid NOT NULL REFERENCES lending_group_authority(id), ordinal integer NOT NULL CHECK(ordinal BETWEEN 1 AND 50),
 client_id uuid NOT NULL REFERENCES members(id), client_number varchar(255) NOT NULL, name varchar(255) NOT NULL,
 may_apply boolean NOT NULL, may_sign boolean NOT NULL, may_receive boolean NOT NULL,
 CHECK(may_apply OR may_sign OR may_receive), PRIMARY KEY(authority_id,client_id), UNIQUE(authority_id,ordinal)
);
CREATE TABLE lending_group_authority_revocation (
 authority_id uuid PRIMARY KEY, group_id uuid NOT NULL,sacco_id varchar(255) NOT NULL,station_id varchar(255) NOT NULL,
 actor_id uuid NOT NULL REFERENCES members(id), actor_name varchar(255) NOT NULL, recorded_at timestamptz NOT NULL,
 reason varchar(500) NOT NULL CHECK(length(trim(reason))>0),
 FOREIGN KEY(authority_id,group_id,sacco_id,station_id) REFERENCES lending_group_authority(id,group_id,sacco_id,station_id)
);
CREATE FUNCTION lending_group_authority_scope() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE latest lending_group_authority; source lending_group; person members; authority lending_group_authority;
BEGIN
 IF TG_TABLE_NAME='lending_group_authority_signer' THEN
  SELECT * INTO authority FROM lending_group_authority WHERE id=NEW.authority_id FOR UPDATE;
  SELECT * INTO person FROM members WHERE id=NEW.client_id AND sacco_id=authority.sacco_id AND station_id=authority.station_id AND is_member AND status='ACTIVE';
  IF NOT FOUND OR NEW.name IS DISTINCT FROM person.full_name OR NEW.client_number IS DISTINCT FROM person.member_no
    OR (SELECT count(*) FROM lending_group_authority_signer WHERE authority_id=authority.id)>=authority.signer_count
  THEN RAISE EXCEPTION 'Authority signer identity or scope is invalid'; END IF;
  RETURN NEW;
 END IF;
 SELECT * INTO source FROM lending_group WHERE id=NEW.group_id AND sacco_id=NEW.sacco_id AND station_id=NEW.station_id FOR UPDATE;
 IF NOT FOUND THEN RAISE EXCEPTION 'Authority group scope is invalid'; END IF;
 IF TG_TABLE_NAME='lending_group_authority' THEN
  IF source.status<>'ACTIVE' OR ROW(NEW.group_name,NEW.group_code,NEW.legal_form) IS DISTINCT FROM ROW(source.name,source.code,source.legal_form)
   OR NOT EXISTS(SELECT 1 FROM members WHERE id=NEW.recorded_by AND sacco_id=NEW.sacco_id AND station_id=NEW.station_id AND full_name=NEW.recorded_by_name)
  THEN RAISE EXCEPTION 'Authority verification scope is invalid'; END IF;
  SELECT * INTO latest FROM lending_group_authority WHERE group_id=NEW.group_id ORDER BY revision DESC LIMIT 1;
  IF NEW.previous_id IS DISTINCT FROM latest.id OR NEW.revision<>coalesce(latest.revision,0)+1
  THEN RAISE EXCEPTION 'Authority revision has changed'; END IF;
 ELSE
  IF NOT EXISTS(SELECT 1 FROM members WHERE id=NEW.actor_id AND sacco_id=NEW.sacco_id AND station_id=NEW.station_id AND full_name=NEW.actor_name)
  THEN RAISE EXCEPTION 'Authority revocation scope is invalid'; END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER group_authority_scope BEFORE INSERT ON lending_group_authority FOR EACH ROW EXECUTE FUNCTION lending_group_authority_scope();
CREATE TRIGGER group_authority_signer_scope BEFORE INSERT ON lending_group_authority_signer FOR EACH ROW EXECUTE FUNCTION lending_group_authority_scope();
CREATE TRIGGER group_authority_revocation_scope BEFORE INSERT ON lending_group_authority_revocation FOR EACH ROW EXECUTE FUNCTION lending_group_authority_scope();
CREATE FUNCTION lending_group_authority_complete() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF NOT EXISTS(SELECT 1 FROM lending_group_authority_signer WHERE authority_id=NEW.id
   HAVING count(*)=NEW.signer_count AND count(*) FILTER(WHERE may_sign)>=NEW.required_signatures
    AND count(*) FILTER(WHERE may_apply)>0 AND count(*) FILTER(WHERE may_receive)>0)
 THEN RAISE EXCEPTION 'Authority must name all required signatories and powers'; END IF;
 RETURN NEW;
END $$;
CREATE CONSTRAINT TRIGGER group_authority_complete AFTER INSERT ON lending_group_authority DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION lending_group_authority_complete();
CREATE FUNCTION lending_group_authority_immutable() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 RAISE EXCEPTION 'Verified authority and revocation history is immutable';
END $$;
CREATE TRIGGER group_authority_immutable BEFORE UPDATE OR DELETE ON lending_group_authority FOR EACH ROW EXECUTE FUNCTION lending_group_authority_immutable();
CREATE TRIGGER group_authority_signer_immutable BEFORE UPDATE OR DELETE ON lending_group_authority_signer FOR EACH ROW EXECUTE FUNCTION lending_group_authority_immutable();
CREATE TRIGGER group_authority_revocation_immutable BEFORE UPDATE OR DELETE ON lending_group_authority_revocation FOR EACH ROW EXECUTE FUNCTION lending_group_authority_immutable();
