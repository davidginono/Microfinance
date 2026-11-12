-- Reporting metadata only. No account, journal, balance, permission or old snapshot is changed.
CREATE TABLE statement_setup_version (
 sacco_id varchar(255) NOT NULL REFERENCES registered_saccos(sacco_id),
 version integer NOT NULL CHECK(version>0), payload text NOT NULL CHECK(length(payload)<=262144),
 request_key uuid NOT NULL, request_hash varchar(64) NOT NULL,
 actor_id uuid NOT NULL REFERENCES members(id), reason varchar(500) NOT NULL CHECK(length(trim(reason))>0),
 recorded_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY(sacco_id,version), UNIQUE(sacco_id,request_key)
);
CREATE TABLE statement_equity_classification (
 sacco_id varchar(255) NOT NULL REFERENCES registered_saccos(sacco_id),
 station_id varchar(255) NOT NULL, journal_id uuid NOT NULL REFERENCES gl_journal(id),
 version integer NOT NULL CHECK(version>0),
 category varchar(32) NOT NULL CHECK(category IN ('CONTRIBUTION','DISTRIBUTION','TRANSFER','EARNINGS_TRANSFER','OCI','ADJUSTMENT','UNCLASSIFIED')),
 actor_id uuid NOT NULL REFERENCES members(id), reason varchar(500) NOT NULL CHECK(length(trim(reason))>0),
 recorded_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY(sacco_id,station_id,journal_id,version)
);
CREATE FUNCTION protect_statement_reporting_history() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Statement reporting history is immutable'; END $$;
CREATE TRIGGER statement_setup_immutable BEFORE UPDATE OR DELETE ON statement_setup_version
 FOR EACH ROW EXECUTE FUNCTION protect_statement_reporting_history();
CREATE TRIGGER statement_classification_immutable BEFORE UPDATE OR DELETE ON statement_equity_classification
 FOR EACH ROW EXECUTE FUNCTION protect_statement_reporting_history();
CREATE FUNCTION check_statement_classification_scope() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM gl_journal j WHERE j.id=NEW.journal_id AND j.sacco_id=NEW.sacco_id
   AND j.station_id=NEW.station_id AND j.state='POSTED') THEN
  RAISE EXCEPTION 'Posted journal is outside statement scope';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER statement_classification_scope BEFORE INSERT ON statement_equity_classification
 FOR EACH ROW EXECUTE FUNCTION check_statement_classification_scope();
