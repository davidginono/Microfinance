CREATE TABLE operational_report_templates (
    id uuid PRIMARY KEY,
    sacco_id varchar(255) NOT NULL,
    created_by uuid NOT NULL REFERENCES members(id),
    created_at timestamptz NOT NULL,
    UNIQUE (id, sacco_id)
);
CREATE INDEX ix_operational_templates_scope ON operational_report_templates(sacco_id,created_at DESC,id);
CREATE TABLE operational_report_template_versions (
    id uuid PRIMARY KEY,
    template_id uuid NOT NULL,
    sacco_id varchar(255) NOT NULL,
    version integer NOT NULL CHECK(version>0),
    dataset_version integer NOT NULL CHECK(dataset_version=1),
    definition text NOT NULL CHECK(length(definition)<=12000),
    title varchar(100) NOT NULL,
    visibility varchar(16) NOT NULL CHECK(visibility IN ('PRIVATE','INSTITUTION')),
    state varchar(16) NOT NULL CHECK(state IN ('DRAFT','PUBLISHED','RETIRED')),
    made_by uuid NOT NULL REFERENCES members(id),
    made_at timestamptz NOT NULL,
    checked_by uuid REFERENCES members(id),
    checked_at timestamptz,
    FOREIGN KEY(template_id,sacco_id) REFERENCES operational_report_templates(id,sacco_id),
    UNIQUE(template_id,version),
    CHECK((state='DRAFT' AND checked_by IS NULL AND checked_at IS NULL) OR
          (state IN ('PUBLISHED','RETIRED') AND checked_by IS NOT NULL AND checked_at IS NOT NULL AND checked_by<>made_by))
);
CREATE INDEX ix_operational_template_versions_scope ON operational_report_template_versions(sacco_id,state,made_at DESC,id);
CREATE FUNCTION protect_operational_report_definition() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Report definitions cannot be deleted'; END IF;
    IF ROW(NEW.id,NEW.template_id,NEW.sacco_id,NEW.version,NEW.dataset_version,NEW.definition,NEW.title,NEW.visibility,NEW.made_by,NEW.made_at)
       IS DISTINCT FROM ROW(OLD.id,OLD.template_id,OLD.sacco_id,OLD.version,OLD.dataset_version,OLD.definition,OLD.title,OLD.visibility,OLD.made_by,OLD.made_at)
       OR NOT ((OLD.state='DRAFT' AND NEW.state='PUBLISHED') OR (OLD.state='PUBLISHED' AND NEW.state='RETIRED'))
       OR (OLD.state='PUBLISHED' AND ROW(NEW.checked_by,NEW.checked_at) IS DISTINCT FROM ROW(OLD.checked_by,OLD.checked_at))
       THEN RAISE EXCEPTION 'Use a new immutable report version or a valid approval transition'; END IF;
    RETURN NEW;
END; $$;
CREATE TRIGGER operational_report_definition_immutable BEFORE UPDATE OR DELETE ON operational_report_template_versions
    FOR EACH ROW EXECUTE FUNCTION protect_operational_report_definition();
CREATE INDEX ix_repayment_report_scope ON loan_repayment_transactions(sacco_id,station_id,payment_date,posted_at,id);
CREATE INDEX ix_ledger_report_scope ON loan_ledgers(sacco_id,station_id,disbursement_date,created_at,loan_application_id);
