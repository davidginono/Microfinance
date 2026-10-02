-- No policy, date, opening amount or approval is seeded. Assignment of claims is explicit.
CREATE TABLE accounting_policies (
    id uuid PRIMARY KEY,
    sacco_id varchar(255) NOT NULL REFERENCES registered_saccos(sacco_id),
    policy_version integer NOT NULL CHECK (policy_version > 0),
    effective_from date NOT NULL,
    opening_date date NOT NULL CHECK (opening_date <= effective_from),
    authoritative_ledger varchar(20) NOT NULL CHECK (authoritative_ledger IN ('LOCAL_GL', 'EXTERNAL_GL')),
    decisions_json text NOT NULL CHECK (jsonb_typeof(decisions_json::jsonb) = 'object'),
    posting_matrix_json text NOT NULL CHECK (jsonb_typeof(posting_matrix_json::jsonb) = 'object'),
    account_mappings_json text NOT NULL CHECK (jsonb_typeof(account_mappings_json::jsonb) = 'object'),
    evidence_reference text NOT NULL CHECK (length(trim(evidence_reference)) BETWEEN 1 AND 1000),
    maker_id uuid NOT NULL REFERENCES members(id),
    request_key uuid NOT NULL,
    created_at timestamptz NOT NULL,
    UNIQUE (sacco_id, policy_version),
    UNIQUE (sacco_id, request_key),
    UNIQUE (id, sacco_id, policy_version, effective_from)
);
CREATE TABLE accounting_policy_approvals (
    policy_id uuid PRIMARY KEY,
    sacco_id varchar(255) NOT NULL,
    policy_version integer NOT NULL,
    effective_from date NOT NULL,
    checker_id uuid NOT NULL REFERENCES members(id),
    decision varchar(10) NOT NULL CHECK (decision IN ('APPROVED', 'REJECTED')),
    evidence_reference text NOT NULL CHECK (length(trim(evidence_reference)) BETWEEN 1 AND 1000),
    reason text NOT NULL CHECK (length(trim(reason)) BETWEEN 1 AND 2000),
    decided_at timestamptz NOT NULL,
    FOREIGN KEY (policy_id, sacco_id, policy_version, effective_from)
        REFERENCES accounting_policies(id, sacco_id, policy_version, effective_from)
);
-- Latest applicable approved policy; scope precedes date/order, no portfolio loading.
CREATE INDEX ix_accounting_policy_effective ON accounting_policies(sacco_id, effective_from DESC, policy_version DESC);
CREATE INDEX ix_accounting_policy_approved ON accounting_policy_approvals(sacco_id, effective_from DESC)
    WHERE decision = 'APPROVED';

CREATE FUNCTION protect_accounting_policy_history() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Accounting policies and decisions are immutable; create a new version';
END;
$$;
CREATE TRIGGER accounting_policy_immutable BEFORE UPDATE OR DELETE ON accounting_policies
    FOR EACH ROW EXECUTE FUNCTION protect_accounting_policy_history();
CREATE TRIGGER accounting_policy_approval_immutable BEFORE UPDATE OR DELETE ON accounting_policy_approvals
    FOR EACH ROW EXECUTE FUNCTION protect_accounting_policy_history();

CREATE FUNCTION validate_accounting_policy() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE item text; rule jsonb;
BEGIN
    FOREACH item IN ARRAY ARRAY['REPORTING_FRAMEWORK','FINANCIAL_YEAR','CHART_OF_ACCOUNTS','ACCOUNT_MAPPINGS',
        'ROUNDING','INTEREST_RECOGNITION','CONTRACTUAL_INTEREST','FEES_AND_TAXES','IMPAIRMENT',
        'NON_PERFORMING_INTEREST','EARLY_SETTLEMENT','CLOSING_AND_REOPENING','AUTHORIZATION_MATRIX',
        'CUTOVER_AND_BACKOUT','AUDIT_RETENTION_AND_RECOVERY','EXTERNAL_INTEGRATION_BOUNDARY'] LOOP
        IF jsonb_typeof(NEW.decisions_json::jsonb->item) IS DISTINCT FROM 'string' OR
            length(trim(NEW.decisions_json::jsonb->>item)) NOT BETWEEN 1 AND 6000 THEN
            RAISE EXCEPTION 'Accounting decision % is missing or invalid', item;
        END IF;
    END LOOP;
    FOREACH item IN ARRAY ARRAY['OPENING_BALANCE','DISBURSEMENT','REPAYMENT','INTEREST_ACCRUAL','FEE','REFUND',
        'ADVANCE','SETTLEMENT','TOP_UP','EXPENSE','FUNDING','CAPITAL','PROVISION','WRITE_OFF','RECOVERY','REVERSAL','MANUAL_JOURNAL','OPERATIONAL_BRIDGE'] LOOP
        rule := NEW.posting_matrix_json::jsonb->item;
        IF rule->>'permission' IS NULL OR rule->>'permission' NOT IN ('ALLOWED', 'DISABLED') OR
            jsonb_typeof(rule->'treatment') IS DISTINCT FROM 'string' OR
            length(trim(rule->>'treatment')) NOT BETWEEN 1 AND 6000 THEN
            RAISE EXCEPTION 'Posting rule % is missing or invalid', item;
        END IF;
    END LOOP;
    IF NOT EXISTS (SELECT 1 FROM members WHERE id = NEW.maker_id AND sacco_id = NEW.sacco_id) THEN
        RAISE EXCEPTION 'Policy maker must belong to the institution';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER accounting_policy_complete BEFORE INSERT ON accounting_policies
    FOR EACH ROW EXECUTE FUNCTION validate_accounting_policy();

CREATE FUNCTION validate_accounting_policy_approval() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE p accounting_policies; previous accounting_policies;
BEGIN
    -- Policy approvals are rare administrative operations; serialize only this institution's boundary.
    PERFORM pg_advisory_xact_lock(hashtextextended('accounting-policy:' || NEW.sacco_id, 0));
    SELECT * INTO p FROM accounting_policies WHERE id = NEW.policy_id;
    IF p.maker_id = NEW.checker_id OR NOT EXISTS
        (SELECT 1 FROM members WHERE id = NEW.checker_id AND sacco_id = NEW.sacco_id) THEN
        RAISE EXCEPTION 'An independent institution checker is required';
    END IF;
    IF NEW.decision = 'APPROVED' THEN
        SELECT policy.* INTO previous FROM accounting_policies policy
            JOIN accounting_policy_approvals approval ON approval.policy_id = policy.id
            WHERE policy.sacco_id = NEW.sacco_id AND approval.decision = 'APPROVED'
            ORDER BY policy.effective_from DESC, policy.policy_version DESC LIMIT 1;
        IF previous.id IS NOT NULL AND (p.policy_version <= previous.policy_version OR
            p.effective_from <= previous.effective_from OR p.opening_date <> previous.opening_date OR
            p.authoritative_ledger <> previous.authoritative_ledger) THEN
            RAISE EXCEPTION 'A new policy must preserve the official books and move the version boundary forward';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER accounting_policy_independent_approval BEFORE INSERT ON accounting_policy_approvals
    FOR EACH ROW EXECUTE FUNCTION validate_accounting_policy_approval();
