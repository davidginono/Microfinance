-- No accounting policy or approval is seeded. Institution decisions must be supplied by people.
CREATE TABLE accounting_policy (
    id uuid PRIMARY KEY,
    sacco_id varchar(255) NOT NULL REFERENCES registered_saccos(sacco_id),
    station_id varchar(255) NOT NULL,
    policy_version integer NOT NULL CHECK (policy_version > 0),
    state varchar(16) NOT NULL CHECK (state IN ('DRAFT','APPROVED','REJECTED')),
    authority varchar(16) NOT NULL CHECK (authority IN ('LOCAL','EXTERNAL')),
    effective_from date NOT NULL,
    content_json text NOT NULL CHECK (octet_length(content_json) <= 131072),
    content_hash varchar(64) NOT NULL CHECK (content_hash ~ '^[a-f0-9]{64}$'),
    created_by uuid NOT NULL REFERENCES members(id),
    created_at timestamptz NOT NULL,
    checked_by uuid REFERENCES members(id),
    checked_at timestamptz,
    review_evidence varchar(500),
    review_reason varchar(2000),
    request_key uuid NOT NULL,
    UNIQUE (sacco_id, policy_version),
    UNIQUE (sacco_id, request_key),
    UNIQUE (sacco_id, id),
    CHECK ((state = 'DRAFT' AND checked_by IS NULL AND checked_at IS NULL AND review_evidence IS NULL AND review_reason IS NULL)
       OR (state IN ('APPROVED','REJECTED') AND checked_by IS NOT NULL AND checked_at IS NOT NULL
           AND checked_by <> created_by AND length(btrim(review_evidence)) > 0 AND length(btrim(review_reason)) > 0))
);
CREATE UNIQUE INDEX ux_accounting_policy_effective ON accounting_policy(sacco_id, effective_from) WHERE state = 'APPROVED';
CREATE INDEX ix_accounting_policy_listing ON accounting_policy(sacco_id, policy_version DESC);
CREATE INDEX ix_accounting_policy_applicable ON accounting_policy(sacco_id, effective_from DESC) WHERE state = 'APPROVED';

CREATE TABLE accounting_policy_audit (
    id uuid PRIMARY KEY,
    sacco_id varchar(255) NOT NULL,
    policy_id uuid NOT NULL,
    station_id varchar(255) NOT NULL,
    event varchar(16) NOT NULL CHECK (event IN ('CREATED','APPROVED','REJECTED')),
    actor_id uuid NOT NULL REFERENCES members(id),
    recorded_at timestamptz NOT NULL,
    evidence_reference varchar(500) NOT NULL,
    reason varchar(2000) NOT NULL,
    content_hash varchar(64) NOT NULL,
    FOREIGN KEY (sacco_id, policy_id) REFERENCES accounting_policy(sacco_id, id)
);
CREATE INDEX ix_accounting_policy_audit ON accounting_policy_audit(sacco_id, policy_id, recorded_at, id);

CREATE FUNCTION protect_accounting_policy() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE previous accounting_policy%ROWTYPE;
BEGIN
    IF TG_OP = 'INSERT' THEN
        IF NEW.state <> 'DRAFT' THEN RAISE EXCEPTION 'Accounting policy must start as a draft'; END IF;
        RETURN NEW;
    END IF;
    IF TG_OP = 'DELETE' THEN RAISE EXCEPTION 'Accounting policies cannot be deleted'; END IF;
    IF ROW(NEW.id, NEW.sacco_id, NEW.station_id, NEW.policy_version, NEW.authority, NEW.effective_from,
           NEW.content_json, NEW.content_hash, NEW.created_by, NEW.created_at, NEW.request_key)
       IS DISTINCT FROM
       ROW(OLD.id, OLD.sacco_id, OLD.station_id, OLD.policy_version, OLD.authority, OLD.effective_from,
           OLD.content_json, OLD.content_hash, OLD.created_by, OLD.created_at, OLD.request_key) THEN
        RAISE EXCEPTION 'Accounting policy content is immutable; create a new version';
    END IF;
    IF OLD.state <> 'DRAFT' OR NEW.state NOT IN ('APPROVED','REJECTED') THEN
        RAISE EXCEPTION 'Invalid accounting policy transition';
    END IF;
    IF NEW.state = 'APPROVED' THEN
        -- Narrow governance lock, never acquired by ordinary posting commands.
        PERFORM pg_advisory_xact_lock(hashtextextended(NEW.sacco_id, 4201));
        SELECT * INTO previous FROM accounting_policy WHERE sacco_id=NEW.sacco_id AND state='APPROVED'
            ORDER BY effective_from DESC LIMIT 1;
        IF FOUND AND (previous.authority <> NEW.authority OR NEW.effective_from <= previous.effective_from
            OR NEW.effective_from <= (CURRENT_TIMESTAMP AT TIME ZONE 'Africa/Nairobi')::date) THEN
            RAISE EXCEPTION 'Authority switching or retrospective policy replacement is prohibited';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER accounting_policy_immutable BEFORE INSERT OR UPDATE OR DELETE ON accounting_policy
    FOR EACH ROW EXECUTE FUNCTION protect_accounting_policy();

CREATE FUNCTION protect_accounting_policy_audit() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Accounting policy audit is append-only'; END;
$$;
CREATE TRIGGER accounting_policy_audit_immutable BEFORE UPDATE OR DELETE ON accounting_policy_audit
    FOR EACH ROW EXECUTE FUNCTION protect_accounting_policy_audit();
