-- Planning only: a budget never creates a journal, a payment or approval evidence.
CREATE TABLE account_budget (
 id uuid PRIMARY KEY, sacco_id varchar(255) NOT NULL, station_id varchar(255) NOT NULL,
 account_id uuid NOT NULL, budget_year integer NOT NULL CHECK(budget_year BETWEEN 1900 AND 2200),
 budget_date date NOT NULL, revision integer NOT NULL CHECK(revision>0),
 request_key uuid NOT NULL, payload_hash varchar(64) NOT NULL,
 amounts numeric(18,2)[] NOT NULL CHECK(cardinality(amounts)=12 AND array_ndims(amounts)=1 AND array_lower(amounts,1)=1),
 recorded_by uuid NOT NULL REFERENCES members(id), recorded_at timestamptz NOT NULL,
 replaces_id uuid UNIQUE,
 UNIQUE(id,sacco_id,station_id), UNIQUE(sacco_id,station_id,account_id,budget_year,revision),
 UNIQUE(sacco_id,station_id,request_key),
 FOREIGN KEY(account_id,sacco_id) REFERENCES gl_account(id,sacco_id),
 FOREIGN KEY(sacco_id,station_id) REFERENCES sacco_stations(sacco_id,station_id),
 FOREIGN KEY(replaces_id,sacco_id,station_id) REFERENCES account_budget(id,sacco_id,station_id)
);
CREATE INDEX ix_account_budget_register ON account_budget(sacco_id,station_id,budget_year DESC,account_id,revision DESC);
CREATE FUNCTION account_budget_validate() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF EXISTS(SELECT 1 FROM unnest(NEW.amounts) n WHERE n IS NULL OR n<0 OR n='NaN'::numeric)
 OR (SELECT sum(n) FROM unnest(NEW.amounts) n)<=0 THEN RAISE EXCEPTION 'Twelve nonnegative monthly amounts are required'; END IF;
 IF NEW.replaces_id IS NULL AND NEW.revision<>1 OR NEW.replaces_id IS NOT NULL AND NOT EXISTS
 (SELECT 1 FROM account_budget b WHERE b.id=NEW.replaces_id AND b.sacco_id=NEW.sacco_id AND b.station_id=NEW.station_id
 AND b.account_id=NEW.account_id AND b.budget_year=NEW.budget_year AND b.revision+1=NEW.revision)
 THEN RAISE EXCEPTION 'Budget revision must preserve its account and year'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER account_budget_validate BEFORE INSERT ON account_budget FOR EACH ROW EXECUTE FUNCTION account_budget_validate();
CREATE TRIGGER account_budget_immutable BEFORE UPDATE OR DELETE ON account_budget FOR EACH ROW EXECUTE FUNCTION gl_protect_evidence();
