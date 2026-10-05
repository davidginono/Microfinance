CREATE TABLE operational_report_templates (
    id uuid PRIMARY KEY,
    sacco_id varchar(255) NOT NULL,
    family_id uuid NOT NULL,
    report_version integer NOT NULL CHECK (report_version > 0),
    name varchar(120) NOT NULL,
    definition_json text NOT NULL CHECK (length(definition_json) <= 16384),
    state varchar(16) NOT NULL CHECK (state IN ('DRAFT','PUBLISHED','RETIRED')),
    visibility varchar(16) NOT NULL DEFAULT 'PRIVATE' CHECK (visibility IN ('PRIVATE','INSTITUTION')),
    dataset varchar(24) NOT NULL CHECK (dataset IN ('COLLECTIONS','DISBURSEMENTS','LOAN_PORTFOLIO')),
    creator_id uuid NOT NULL REFERENCES members(id),
    created_at timestamptz NOT NULL,
    publisher_id uuid REFERENCES members(id),
    published_at timestamptz,
    version bigint NOT NULL DEFAULT 0,
    UNIQUE(sacco_id, family_id, report_version),
    CHECK ((state='DRAFT' AND publisher_id IS NULL AND published_at IS NULL) OR
           (state IN ('PUBLISHED','RETIRED') AND publisher_id IS NOT NULL AND published_at IS NOT NULL))
);
CREATE INDEX ix_operational_template_tenant ON operational_report_templates(sacco_id, state, created_at DESC, id);

-- Publication records the complete approved definition in an append-only event.
CREATE TABLE operational_report_template_events (
    id uuid PRIMARY KEY,
    template_id uuid NOT NULL REFERENCES operational_report_templates(id),
    sacco_id varchar(255) NOT NULL,
    actor_id uuid NOT NULL REFERENCES members(id),
    action varchar(16) NOT NULL CHECK (action IN ('CREATED','UPDATED','PUBLISHED','CLONED','RETIRED','SHARED','UNSHARED')),
    definition_json text NOT NULL,
    recorded_at timestamptz NOT NULL
);
CREATE FUNCTION protect_operational_report_template() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Report templates preserve publication history'; END IF;
    IF ROW(NEW.id,NEW.sacco_id,NEW.family_id,NEW.report_version,NEW.creator_id,NEW.created_at)
       IS DISTINCT FROM ROW(OLD.id,OLD.sacco_id,OLD.family_id,OLD.report_version,OLD.creator_id,OLD.created_at) THEN
       RAISE EXCEPTION 'Report identity is immutable';
    END IF;
    IF OLD.state <> 'DRAFT' AND (NEW.state NOT IN (OLD.state,'RETIRED') OR
       ROW(NEW.name,NEW.definition_json,NEW.publisher_id,NEW.published_at,NEW.dataset)
       IS DISTINCT FROM ROW(OLD.name,OLD.definition_json,OLD.publisher_id,OLD.published_at,OLD.dataset)) THEN
       RAISE EXCEPTION 'Published report definitions are immutable';
    END IF;
    RETURN NEW;
END; $$;
CREATE TRIGGER operational_template_history BEFORE UPDATE OR DELETE ON operational_report_templates
    FOR EACH ROW EXECUTE FUNCTION protect_operational_report_template();
CREATE TRIGGER operational_template_events_immutable BEFORE UPDATE OR DELETE ON operational_report_template_events
    FOR EACH ROW EXECUTE FUNCTION protect_loan_financial_history();
-- Scoped date/order paths. Collections uses a posted-time cutoff in addition to the effective date.
CREATE INDEX ix_operational_collections_scope ON loan_repayment_transactions(sacco_id, station_id, payment_date, id);
CREATE INDEX ix_operational_disbursement_scope ON loan_ledgers(sacco_id, station_id, disbursement_date, loan_application_id);
