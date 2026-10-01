CREATE TABLE loan_ledgers (
    loan_application_id uuid PRIMARY KEY REFERENCES loan_applications(id),
    sacco_id varchar(255) NOT NULL,
    station_id varchar(255) NOT NULL,
    loan_id varchar(20) NOT NULL,
    applicant_member_id uuid NOT NULL REFERENCES members(id),
    disbursement_date date NOT NULL,
    principal numeric(18,2) NOT NULL CHECK (principal > 0),
    principal_paid numeric(18,2) NOT NULL DEFAULT 0 CHECK (principal_paid >= 0 AND principal_paid <= principal),
    interest_paid numeric(18,2) NOT NULL DEFAULT 0 CHECK (interest_paid >= 0),
    last_payment_date date,
    next_sequence bigint NOT NULL DEFAULT 1 CHECK (next_sequence > 0),
    created_at timestamptz NOT NULL,
    version integer NOT NULL DEFAULT 0
);
CREATE INDEX ix_loan_ledgers_branch_list ON loan_ledgers(sacco_id, station_id, created_at DESC, loan_application_id);
CREATE INDEX ix_loan_ledgers_client_list ON loan_ledgers(applicant_member_id, created_at DESC, loan_application_id);

CREATE TABLE loan_ledger_installments (
    id uuid PRIMARY KEY,
    loan_application_id uuid NOT NULL REFERENCES loan_ledgers(loan_application_id),
    installment_number integer NOT NULL CHECK (installment_number > 0),
    due_date date NOT NULL,
    principal numeric(18,2) NOT NULL CHECK (principal >= 0),
    interest numeric(18,2) NOT NULL CHECK (interest >= 0),
    principal_paid numeric(18,2) NOT NULL DEFAULT 0 CHECK (principal_paid >= 0 AND principal_paid <= principal),
    interest_paid numeric(18,2) NOT NULL DEFAULT 0 CHECK (interest_paid >= 0 AND interest_paid <= interest),
    UNIQUE(loan_application_id, installment_number)
);

CREATE TABLE loan_repayment_transactions (
    id uuid PRIMARY KEY,
    loan_application_id uuid NOT NULL REFERENCES loan_ledgers(loan_application_id),
    sacco_id varchar(255) NOT NULL,
    station_id varchar(255) NOT NULL,
    sequence bigint NOT NULL CHECK (sequence > 0),
    receipt_reference varchar(64) NOT NULL UNIQUE,
    request_key uuid NOT NULL,
    kind varchar(16) NOT NULL CHECK (kind IN ('PAYMENT', 'REVERSAL')),
    channel varchar(24) NOT NULL CHECK (channel IN ('CASH', 'BANK', 'MOBILE_MONEY')),
    channel_reference varchar(100) NOT NULL,
    payment_date date NOT NULL,
    amount numeric(18,2) NOT NULL CHECK (amount > 0),
    principal_amount numeric(18,2) NOT NULL CHECK (principal_amount >= 0),
    interest_amount numeric(18,2) NOT NULL CHECK (interest_amount >= 0),
    actor_member_id uuid NOT NULL REFERENCES members(id),
    reverses_transaction_id uuid UNIQUE REFERENCES loan_repayment_transactions(id),
    reason varchar(500),
    loan_status_before varchar(40) NOT NULL,
    posted_at timestamptz NOT NULL,
    CHECK (amount = principal_amount + interest_amount),
    CHECK ((kind = 'PAYMENT' AND reverses_transaction_id IS NULL) OR
           (kind = 'REVERSAL' AND reverses_transaction_id IS NOT NULL AND reason IS NOT NULL)),
    UNIQUE(sacco_id, station_id, request_key),
    UNIQUE(loan_application_id, sequence)
);
CREATE UNIQUE INDEX ux_repayment_channel_reference ON loan_repayment_transactions(sacco_id, station_id, channel, channel_reference)
    WHERE kind = 'PAYMENT';
CREATE INDEX ix_repayments_loan_history ON loan_repayment_transactions(loan_application_id, sequence DESC);

CREATE TABLE loan_repayment_allocations (
    id uuid PRIMARY KEY,
    transaction_id uuid NOT NULL REFERENCES loan_repayment_transactions(id),
    installment_id uuid NOT NULL REFERENCES loan_ledger_installments(id),
    principal_amount numeric(18,2) NOT NULL CHECK (principal_amount >= 0),
    interest_amount numeric(18,2) NOT NULL CHECK (interest_amount >= 0),
    CHECK (principal_amount + interest_amount > 0),
    UNIQUE(transaction_id, installment_id)
);

CREATE TABLE loan_journal_entries (
    id uuid PRIMARY KEY,
    loan_application_id uuid NOT NULL REFERENCES loan_ledgers(loan_application_id),
    transaction_id uuid REFERENCES loan_repayment_transactions(id),
    voucher_id uuid NOT NULL,
    account_code varchar(40) NOT NULL,
    debit numeric(18,2) NOT NULL CHECK (debit >= 0),
    credit numeric(18,2) NOT NULL CHECK (credit >= 0),
    effective_date date NOT NULL,
    posted_at timestamptz NOT NULL,
    CHECK ((debit > 0 AND credit = 0) OR (credit > 0 AND debit = 0))
);
CREATE INDEX ix_loan_journal_voucher ON loan_journal_entries(voucher_id);
CREATE INDEX ix_loan_journal_history ON loan_journal_entries(loan_application_id, posted_at, id);

-- Posted contract terms stay fixed; allocations may only update the paid counters.
CREATE FUNCTION protect_loan_installment_terms() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Posted loan installments cannot be deleted';
    END IF;
    IF ROW(NEW.id, NEW.loan_application_id, NEW.installment_number, NEW.due_date, NEW.principal, NEW.interest)
       IS DISTINCT FROM ROW(OLD.id, OLD.loan_application_id, OLD.installment_number, OLD.due_date, OLD.principal, OLD.interest) THEN
        RAISE EXCEPTION 'Posted loan installment terms cannot be modified';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER loan_installment_terms_immutable BEFORE UPDATE OR DELETE ON loan_ledger_installments
    FOR EACH ROW EXECUTE FUNCTION protect_loan_installment_terms();

-- Financial history is append-only. Corrections must be linked reversal entries.
CREATE FUNCTION protect_loan_financial_history() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Posted loan financial history cannot be modified or deleted';
END;
$$;
CREATE TRIGGER repayment_history_immutable BEFORE UPDATE OR DELETE ON loan_repayment_transactions
    FOR EACH ROW EXECUTE FUNCTION protect_loan_financial_history();
CREATE TRIGGER repayment_allocation_immutable BEFORE UPDATE OR DELETE ON loan_repayment_allocations
    FOR EACH ROW EXECUTE FUNCTION protect_loan_financial_history();
CREATE TRIGGER loan_journal_immutable BEFORE UPDATE OR DELETE ON loan_journal_entries
    FOR EACH ROW EXECUTE FUNCTION protect_loan_financial_history();

CREATE FUNCTION validate_loan_journal_balance() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF (SELECT COALESCE(SUM(debit - credit), 0) FROM loan_journal_entries WHERE voucher_id = NEW.voucher_id) <> 0 THEN
        RAISE EXCEPTION 'Loan journal voucher must balance';
    END IF;
    RETURN NEW;
END;
$$;
CREATE CONSTRAINT TRIGGER loan_journal_balanced AFTER INSERT ON loan_journal_entries
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION validate_loan_journal_balance();
