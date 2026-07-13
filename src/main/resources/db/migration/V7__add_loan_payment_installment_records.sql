CREATE TABLE public.loan_payment_installment_records (
    id uuid NOT NULL,
    loan_application_id uuid NOT NULL,
    sacco_id character varying(64) NOT NULL,
    station_id character varying(64),
    installment_number integer NOT NULL,
    due_date date,
    scheduled_principal numeric(18,2) NOT NULL,
    scheduled_interest numeric(18,2) NOT NULL,
    principal_paid numeric(18,2) NOT NULL,
    interest_paid numeric(18,2) NOT NULL,
    total_paid numeric(18,2) NOT NULL,
    payment_date date,
    outstanding_balance numeric(18,2) NOT NULL,
    fetched_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT loan_payment_installment_records_pkey PRIMARY KEY (id),
    CONSTRAINT uk_payment_installment_record UNIQUE (loan_application_id, installment_number)
);

CREATE INDEX idx_payment_installment_record_scope_date
    ON public.loan_payment_installment_records (sacco_id, station_id, payment_date);

CREATE INDEX idx_payment_installment_record_loan
    ON public.loan_payment_installment_records (loan_application_id, installment_number);
