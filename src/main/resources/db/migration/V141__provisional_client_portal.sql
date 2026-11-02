-- Restricted client-number/OTP access does not create a full borrower login.
CREATE TABLE client_portal_rate_limit (
 key varchar(64) PRIMARY KEY, window_start timestamptz NOT NULL,last_requested timestamptz NOT NULL,requests integer NOT NULL CHECK(requests>0)
);
CREATE TABLE client_portal_challenge (
 id uuid PRIMARY KEY,client_id uuid REFERENCES members(id),sacco_id text NOT NULL,station_id text NOT NULL,
 intent text NOT NULL CHECK(intent IN ('REGISTER','PROFILE','LOAN')),contact_hash varchar(64) NOT NULL,payload jsonb NOT NULL,
 code_hash varchar(255) NOT NULL,created_at timestamptz NOT NULL,expires_at timestamptz NOT NULL,
 delivered_at timestamptz,consumed_at timestamptz,attempts integer NOT NULL DEFAULT 0 CHECK(attempts BETWEEN 0 AND 5),
 CHECK((intent='REGISTER' AND client_id IS NULL) OR (intent<>'REGISTER' AND client_id IS NOT NULL)),CHECK(expires_at>created_at)
);
CREATE INDEX ix_client_portal_challenge_expiry ON client_portal_challenge(expires_at,id);
