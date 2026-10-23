-- Group identity and effective-dated membership do not create loans or move money.
CREATE TABLE lending_group (
 id uuid PRIMARY KEY, sacco_id varchar(255) NOT NULL, station_id varchar(255) NOT NULL,
 request_key uuid NOT NULL, payload_hash varchar(64) NOT NULL,
 code varchar(32) NOT NULL, name varchar(160) NOT NULL CHECK(length(trim(name))>0),
 legal_form varchar(32) NOT NULL CHECK(legal_form IN ('INFORMAL','REGISTERED_GROUP','ORGANIZATION')),
 identity_evidence varchar(500) NOT NULL CHECK(length(trim(identity_evidence))>0),
 status varchar(16) NOT NULL DEFAULT 'ACTIVE' CHECK(status IN ('ACTIVE','INACTIVE')),
 created_by uuid NOT NULL REFERENCES members(id), created_at timestamptz NOT NULL,
 version integer NOT NULL DEFAULT 0,
 UNIQUE(id,sacco_id,station_id), UNIQUE(sacco_id,code), UNIQUE(sacco_id,station_id,request_key),
 FOREIGN KEY(sacco_id,station_id) REFERENCES sacco_stations(sacco_id,station_id)
);
CREATE INDEX ix_lending_group_register ON lending_group(sacco_id,station_id,name,id);

CREATE TABLE lending_group_member (
 id uuid PRIMARY KEY, group_id uuid NOT NULL, sacco_id varchar(255) NOT NULL, station_id varchar(255) NOT NULL,
 client_id uuid NOT NULL REFERENCES members(id),
 office varchar(16) NOT NULL CHECK(office IN ('MEMBER','CHAIRPERSON','SECRETARY','TREASURER')),
 starts_on date NOT NULL, ends_on date, evidence varchar(500) NOT NULL CHECK(length(trim(evidence))>0),
 created_by uuid NOT NULL REFERENCES members(id), created_at timestamptz NOT NULL,
 ended_by uuid REFERENCES members(id), end_reason varchar(500),
 request_key uuid NOT NULL, payload_hash varchar(64) NOT NULL,
 CHECK(ends_on IS NULL OR ends_on>=starts_on),
 CHECK((ends_on IS NULL AND ended_by IS NULL AND end_reason IS NULL) OR
       (ends_on IS NOT NULL AND ended_by IS NOT NULL AND length(trim(end_reason))>0)),
 UNIQUE(id,sacco_id,station_id), UNIQUE(sacco_id,station_id,request_key),
 FOREIGN KEY(group_id,sacco_id,station_id) REFERENCES lending_group(id,sacco_id,station_id)
);
CREATE UNIQUE INDEX ux_lending_group_active_member ON lending_group_member(group_id,client_id) WHERE ends_on IS NULL;
CREATE UNIQUE INDEX ux_lending_group_active_office ON lending_group_member(group_id,office) WHERE ends_on IS NULL AND office<>'MEMBER';
CREATE INDEX ix_lending_group_members ON lending_group_member(group_id,starts_on,id);
CREATE INDEX ix_lending_group_client ON lending_group_member(sacco_id,station_id,client_id,group_id);

CREATE FUNCTION lending_group_scope() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF NOT EXISTS(SELECT 1 FROM members m WHERE m.id=NEW.created_by AND m.sacco_id=NEW.sacco_id AND m.station_id=NEW.station_id)
 THEN RAISE EXCEPTION 'Group actor scope must agree'; END IF;
 IF TG_TABLE_NAME='lending_group_member' THEN
  PERFORM 1 FROM lending_group WHERE id=NEW.group_id AND sacco_id=NEW.sacco_id AND station_id=NEW.station_id AND status='ACTIVE' FOR UPDATE;
  IF NOT FOUND OR NOT EXISTS(SELECT 1 FROM members m WHERE m.id=NEW.client_id
    AND m.sacco_id=NEW.sacco_id AND m.station_id=NEW.station_id AND m.is_member AND m.status='ACTIVE')
  THEN RAISE EXCEPTION 'Group client scope must agree'; END IF;
  IF EXISTS(SELECT 1 FROM lending_group_member m WHERE m.group_id=NEW.group_id AND m.client_id=NEW.client_id
    AND (m.ends_on IS NULL OR m.ends_on>=NEW.starts_on))
  THEN RAISE EXCEPTION 'Membership dates cannot overlap'; END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER lending_group_scope BEFORE INSERT ON lending_group FOR EACH ROW EXECUTE FUNCTION lending_group_scope();
CREATE TRIGGER lending_group_member_scope BEFORE INSERT ON lending_group_member FOR EACH ROW EXECUTE FUNCTION lending_group_scope();

CREATE FUNCTION lending_group_protect() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Group history cannot be deleted'; END IF;
 IF TG_TABLE_NAME='lending_group' THEN
  IF (to_jsonb(NEW)-ARRAY['status','version']) IS DISTINCT FROM (to_jsonb(OLD)-ARRAY['status','version'])
    OR NEW.version<>OLD.version+1 THEN RAISE EXCEPTION 'Group identity is immutable'; END IF;
 ELSE
  IF OLD.ends_on IS NOT NULL OR NEW.ends_on IS NULL OR
    (to_jsonb(NEW)-ARRAY['ends_on','ended_by','end_reason']) IS DISTINCT FROM
    (to_jsonb(OLD)-ARRAY['ends_on','ended_by','end_reason']) OR NOT EXISTS(
      SELECT 1 FROM members m WHERE m.id=NEW.ended_by AND m.sacco_id=NEW.sacco_id AND m.station_id=NEW.station_id)
  THEN RAISE EXCEPTION 'Membership history must be retained'; END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER lending_group_protect BEFORE UPDATE OR DELETE ON lending_group FOR EACH ROW EXECUTE FUNCTION lending_group_protect();
CREATE TRIGGER lending_group_member_protect BEFORE UPDATE OR DELETE ON lending_group_member FOR EACH ROW EXECUTE FUNCTION lending_group_protect();
