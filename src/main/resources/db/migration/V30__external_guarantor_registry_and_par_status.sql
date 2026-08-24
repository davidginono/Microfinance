ALTER TABLE public.loan_applications
    DROP CONSTRAINT IF EXISTS loan_applications_status_check;

ALTER TABLE public.loan_applications
    ADD CONSTRAINT loan_applications_status_check
    CHECK (((status)::text = ANY ((ARRAY[
        'DRAFT'::character varying,
        'SUBMITTED'::character varying,
        'AWAITING_GUARANTORS'::character varying,
        'ALL_GUARANTORS_APPROVED'::character varying,
        'READY_FOR_MANAGER'::character varying,
        'MANAGER_REJECTED'::character varying,
        'MANAGER_ACCEPTED'::character varying,
        'AWAITING_LOAN_OFFICER'::character varying,
        'LOAN_OFFICER_REJECTED'::character varying,
        'LOAN_OFFICER_APPROVED'::character varying,
        'AWAITING_CHAIRPERSON'::character varying,
        'CHAIRPERSON_REJECTED'::character varying,
        'CHAIRPERSON_APPROVED'::character varying,
        'AWAITING_BOARD'::character varying,
        'AWAITING_CREDIT_COMMITTEE'::character varying,
        'BOARD_REJECTED'::character varying,
        'BOARD_APPROVED'::character varying,
        'CREDIT_COMMITTEE_REJECTED'::character varying,
        'CREDIT_COMMITTEE_APPROVED'::character varying,
        'AWAITING_ACCOUNTANT'::character varying,
        'ACCOUNTANT_REJECTED'::character varying,
        'ACCOUNTANT_APPROVED'::character varying,
        'READY_FOR_DISBURSEMENT'::character varying,
        'REJECTED'::character varying,
        'DISBURSED'::character varying,
        'PAR'::character varying,
        'DEFAULTED'::character varying,
        'PAID'::character varying
    ])::text[])));

ALTER TABLE public.sacco_settings
    ADD COLUMN IF NOT EXISTS portfolio_at_risk_days integer NOT NULL DEFAULT 30;

ALTER TABLE public.sacco_settings
    DROP CONSTRAINT IF EXISTS sacco_settings_portfolio_at_risk_days_check;

ALTER TABLE public.sacco_settings
    ADD CONSTRAINT sacco_settings_portfolio_at_risk_days_check
    CHECK (portfolio_at_risk_days BETWEEN 1 AND 365);

ALTER TABLE public.sacco_station_policies
    ADD COLUMN IF NOT EXISTS portfolio_at_risk_days integer;

ALTER TABLE public.sacco_station_policies
    DROP CONSTRAINT IF EXISTS sacco_station_policies_portfolio_at_risk_days_check;

ALTER TABLE public.sacco_station_policies
    ADD CONSTRAINT sacco_station_policies_portfolio_at_risk_days_check
    CHECK (portfolio_at_risk_days IS NULL OR portfolio_at_risk_days BETWEEN 1 AND 365);

CREATE TABLE IF NOT EXISTS public.external_guarantor_registry (
    id uuid NOT NULL,
    sacco_id character varying(255) NOT NULL,
    station_id character varying(255),
    external_station_id character varying(80) NOT NULL,
    external_member_no character varying(80) NOT NULL,
    full_name character varying(255),
    email character varying(255),
    phone character varying(40),
    latest_financial_snapshot jsonb,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    last_approved_at timestamp with time zone,
    version integer NOT NULL DEFAULT 0,
    CONSTRAINT external_guarantor_registry_pkey PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_external_guarantor_registry_identity
    ON public.external_guarantor_registry (sacco_id, lower(external_station_id), lower(external_member_no));

CREATE INDEX IF NOT EXISTS idx_external_guarantor_registry_scope
    ON public.external_guarantor_registry (sacco_id, station_id, lower(external_station_id), lower(external_member_no));

ALTER TABLE public.guarantor_requests
    ADD COLUMN IF NOT EXISTS external_guarantor_registry_id uuid;

CREATE INDEX IF NOT EXISTS idx_guarantor_requests_external_registry
    ON public.guarantor_requests (external_guarantor_registry_id)
    WHERE external_guarantor_registry_id IS NOT NULL;

ALTER TABLE public.guarantor_requests
    DROP CONSTRAINT IF EXISTS fk_guarantor_requests_external_registry;

ALTER TABLE public.guarantor_requests
    ADD CONSTRAINT fk_guarantor_requests_external_registry
    FOREIGN KEY (external_guarantor_registry_id)
    REFERENCES public.external_guarantor_registry(id);
