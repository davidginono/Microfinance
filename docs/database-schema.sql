--
-- PostgreSQL database dump
--

\restrict aTG6i3WBETRjdOOPdB1XIMsrw9JfmXgtyl1Wg5rR4kGgsfR9dkuPnxXzRfbRwQm

-- Dumped from database version 18.3
-- Dumped by pg_dump version 18.3

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: public; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA IF NOT EXISTS public;


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: accounts_savings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.accounts_savings (
    id uuid NOT NULL,
    available_balance numeric(18,2) NOT NULL,
    deposits_balance numeric(18,2) NOT NULL,
    member_id uuid NOT NULL,
    shares_balance numeric(18,2) NOT NULL,
    summary_last_synced_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone NOT NULL
);


--
-- Name: admin_incidents; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.admin_incidents (
    id uuid NOT NULL,
    category character varying(255) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    details_json jsonb,
    message character varying(4000) NOT NULL,
    related_notification_id uuid,
    reported_by_member_id uuid,
    resolution_note character varying(4000),
    resolved_at timestamp(6) with time zone,
    resolved_by_member_id uuid,
    sacco_id character varying(255),
    severity character varying(255) NOT NULL,
    source character varying(255) NOT NULL,
    status character varying(255) NOT NULL,
    subject character varying(255) NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT admin_incidents_severity_check CHECK (((severity)::text = ANY (ARRAY[('LOW'::character varying)::text, ('MEDIUM'::character varying)::text, ('HIGH'::character varying)::text, ('CRITICAL'::character varying)::text]))),
    CONSTRAINT admin_incidents_status_check CHECK (((status)::text = ANY (ARRAY[('OPEN'::character varying)::text, ('IN_PROGRESS'::character varying)::text, ('RESOLVED'::character varying)::text])))
);


--
-- Name: app_usage_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.app_usage_events (
    id uuid NOT NULL,
    browser_family character varying(40),
    device_type character varying(40),
    display_name character varying(180),
    event_type character varying(32) NOT NULL,
    member_id uuid NOT NULL,
    occurred_at timestamp(6) with time zone NOT NULL,
    page_path character varying(240),
    roles character varying(240),
    sacco_id character varying(80),
    station_id character varying(80),
    username character varying(120),
    CONSTRAINT app_usage_events_event_type_check CHECK (((event_type)::text = ANY (ARRAY[('LOGIN'::character varying)::text, ('PAGE_VIEW'::character varying)::text, ('LOGOUT'::character varying)::text])))
);


--
-- Name: app_usage_page_metrics; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.app_usage_page_metrics (
    id uuid NOT NULL,
    browser_family character varying(40) NOT NULL,
    bucket_start timestamp(6) with time zone NOT NULL,
    device_type character varying(40) NOT NULL,
    hit_count bigint NOT NULL,
    last_recorded_at timestamp(6) with time zone NOT NULL,
    page_path character varying(240) NOT NULL,
    sacco_id character varying(80) NOT NULL,
    station_id character varying(80) NOT NULL
);


--
-- Name: audit_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.audit_log (
    id uuid NOT NULL,
    action character varying(255),
    action_description character varying(255),
    actor_member_id uuid,
    after_state jsonb,
    before_state jsonb,
    created_at timestamp(6) with time zone NOT NULL,
    entity_id uuid,
    entity_type character varying(255),
    event_status character varying(20),
    reference_type character varying(80),
    reference_value character varying(255),
    request_metadata jsonb,
    sacco_id character varying(255),
    station_id character varying(255)
);


--
-- Name: board_reviews; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.board_reviews (
    id uuid NOT NULL,
    board_member_id uuid NOT NULL,
    comment character varying(255),
    created_at timestamp(6) with time zone NOT NULL,
    decided_at timestamp(6) with time zone,
    decision character varying(255) NOT NULL,
    loan_application_id uuid NOT NULL,
    board_signature_text character varying(255),
    board_signature_verified_at timestamp(6) with time zone,
    review_stage character varying(255) NOT NULL,
    CONSTRAINT board_reviews_decision_check CHECK (((decision)::text = ANY (ARRAY[('PENDING'::character varying)::text, ('APPROVED'::character varying)::text, ('REJECTED'::character varying)::text])))
);


--
-- Name: email_otp_tokens; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.email_otp_tokens (
    id uuid NOT NULL,
    code_hash character varying(255) NOT NULL,
    consumed_at timestamp(6) with time zone,
    created_at timestamp(6) with time zone NOT NULL,
    email character varying(255) NOT NULL,
    expires_at timestamp(6) with time zone NOT NULL,
    member_id uuid,
    purpose character varying(255) NOT NULL,
    resend_count integer DEFAULT 0 NOT NULL,
    CONSTRAINT email_otp_tokens_purpose_check CHECK (((purpose)::text = ANY (ARRAY[('LOGIN'::character varying)::text, ('LOGIN_MFA'::character varying)::text, ('STAFF_LOGIN'::character varying)::text, ('STAFF_LOGIN_MFA'::character varying)::text, ('REGISTRATION'::character varying)::text, ('PASSWORD_RESET'::character varying)::text, ('APPLICANT_SIGNATURE'::character varying)::text, ('GUARANTOR_APPLICANT_CONFIRMATION'::character varying)::text, ('PAYMENT_DETAILS_CHANGE'::character varying)::text, ('GUARANTOR_SIGNATURE'::character varying)::text, ('BOARD_SIGNATURE'::character varying)::text, ('CLAIM_ACCOUNT'::character varying)::text, ('CLAIM_PHONE'::character varying)::text])))
);


--
-- Name: guarantor_requests; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.guarantor_requests (
    id uuid NOT NULL,
    committed_amount numeric(18,2),
    created_at timestamp(6) with time zone NOT NULL,
    decided_at timestamp(6) with time zone,
    decision_reason character varying(255),
    guarantor_member_id uuid NOT NULL,
    loan_application_id uuid NOT NULL,
    requested_amount numeric(18,2),
    status character varying(255) NOT NULL,
    version integer NOT NULL,
    guarantor_signature_text character varying(255),
    guarantor_signature_verified_at timestamp(6) with time zone,
    CONSTRAINT guarantor_requests_status_check CHECK (((status)::text = ANY (ARRAY[('PENDING'::character varying)::text, ('APPROVED'::character varying)::text, ('REJECTED'::character varying)::text, ('EXPIRED'::character varying)::text])))
);


--
-- Name: loan_applications; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.loan_applications (
    id uuid NOT NULL,
    amount numeric(18,2) NOT NULL,
    applicant_member_id uuid NOT NULL,
    attachments_json jsonb,
    created_at timestamp(6) with time zone NOT NULL,
    disbursement_date date,
    disbursement_notes character varying(4000),
    disbursement_reference character varying(255),
    final_due_date date,
    financial_snapshot jsonb,
    first_repayment_date date,
    form_data jsonb NOT NULL,
    installment_amount numeric(18,2),
    loan_type character varying(255) NOT NULL,
    policy_snapshot jsonb NOT NULL,
    repayment_frequency character varying(255),
    repayment_schedule_json jsonb,
    required_guarantors integer NOT NULL,
    sacco_id character varying(255) NOT NULL,
    selected_guarantors jsonb,
    status character varying(255) NOT NULL,
    submitted_at timestamp(6) with time zone,
    tenor_months integer NOT NULL,
    top_up_source_loan_id uuid,
    updated_at timestamp(6) with time zone NOT NULL,
    version integer NOT NULL,
    applicant_signature_text character varying(255),
    applicant_signature_verified_at timestamp(6) with time zone,
    paid_at timestamp(6) with time zone,
    paid_marked_by_manager_id uuid,
    loan_id character varying(20),
    application_number bigint NOT NULL,
    station_id character varying(255) NOT NULL,
    payment_details_snapshot jsonb,
    applicant_disbursement_acknowledged_at timestamp with time zone,
    applicant_rejection_acknowledged_at timestamp with time zone,
    deposit_amount numeric(18,2),
    loan_product_setting_id uuid,
    CONSTRAINT loan_applications_loan_type_check CHECK (((loan_type)::text = ANY (ARRAY['LOAN_ADVANCE'::text, 'EDUCATION_LOAN'::text, 'EMERGENCY_LOAN'::text, 'DEVELOPMENT_LOAN'::text, 'CUSTOMIZED_LOAN'::text]))),
    CONSTRAINT loan_applications_repayment_frequency_check CHECK (((repayment_frequency)::text = ANY (ARRAY[('WEEKLY'::character varying)::text, ('MONTHLY'::character varying)::text]))),
    CONSTRAINT loan_applications_status_check CHECK (((status)::text = ANY (ARRAY[('DRAFT'::character varying)::text, ('SUBMITTED'::character varying)::text, ('AWAITING_GUARANTORS'::character varying)::text, ('ALL_GUARANTORS_APPROVED'::character varying)::text, ('READY_FOR_MANAGER'::character varying)::text, ('MANAGER_REJECTED'::character varying)::text, ('MANAGER_ACCEPTED'::character varying)::text, ('AWAITING_LOAN_OFFICER'::character varying)::text, ('LOAN_OFFICER_REJECTED'::character varying)::text, ('LOAN_OFFICER_APPROVED'::character varying)::text, ('AWAITING_CHAIRPERSON'::character varying)::text, ('CHAIRPERSON_REJECTED'::character varying)::text, ('CHAIRPERSON_APPROVED'::character varying)::text, ('AWAITING_BOARD'::character varying)::text, ('AWAITING_CREDIT_COMMITTEE'::character varying)::text, ('BOARD_REJECTED'::character varying)::text, ('BOARD_APPROVED'::character varying)::text, ('CREDIT_COMMITTEE_REJECTED'::character varying)::text, ('CREDIT_COMMITTEE_APPROVED'::character varying)::text, ('AWAITING_ACCOUNTANT'::character varying)::text, ('ACCOUNTANT_REJECTED'::character varying)::text, ('ACCOUNTANT_APPROVED'::character varying)::text, ('READY_FOR_DISBURSEMENT'::character varying)::text, ('REJECTED'::character varying)::text, ('DISBURSED'::character varying)::text, ('DEFAULTED'::character varying)::text, ('PAID'::character varying)::text])))
);


-- Name: loan_product_board_reviewers; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.loan_product_board_reviewers (
    id uuid NOT NULL,
    board_member_id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    loan_product_setting_id uuid NOT NULL,
    sacco_id character varying(255) NOT NULL,
    review_stage character varying(32) NOT NULL
);


--
-- Name: loan_product_required_attachments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.loan_product_required_attachments (
    id uuid NOT NULL,
    active boolean NOT NULL,
    attachment_name character varying(120) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    display_order integer NOT NULL,
    loan_product_setting_id uuid CONSTRAINT loan_product_required_attachme_loan_product_setting_id_not_null NOT NULL,
    max_size_mb numeric(8,2),
    updated_at timestamp(6) with time zone NOT NULL
);


--
-- Name: loan_product_settings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.loan_product_settings (
    id uuid NOT NULL,
    active boolean NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    form_schema jsonb NOT NULL,
    guarantors_required integer NOT NULL,
    insurance_rate numeric(6,4),
    interest_rate numeric(6,4),
    loan_type character varying(255) NOT NULL,
    max_loan_savings_ratio numeric(6,4),
    max_repayment_months integer,
    sacco_id character varying(255) NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    product_name character varying(120),
    allow_application_with_active_loan boolean,
    display_order integer,
    interest_method character varying(32),
    maximum_amount numeric(18,2),
    min_repayment_months integer,
    minimum_amount numeric(18,2),
    product_code character varying(64),
    product_description character varying(500),
    committee_approval_threshold integer,
    committee_minimum_votes integer,
    committee_review_required boolean,
    manager_review_required boolean,
    product_status character varying(32),
    fresh_financial_data_required boolean,
    application_fee numeric(18,2),
    accountant_priority integer,
    accountant_review_required boolean,
    committee_priority integer,
    loan_officer_review_required boolean,
    workflow_start_stage character varying(32),
    disbursement_officer_required boolean,
    guarantor_commitment_required boolean,
    guarantor_commitment_stage character varying(32),
    loan_officer_priority integer,
    manager_priority integer,
    guarantor_min_savings_check_required boolean,
    guarantor_minimum_savings numeric(18,2),
    disbursement_proof_required boolean DEFAULT true NOT NULL,
    applicant_attachment_required boolean DEFAULT false NOT NULL,
    processing_fee_rate numeric(6,4),
    board_priority integer,
    board_review_required boolean,
    chairperson_priority integer,
    chairperson_review_required boolean,
    savings_limit_check_required boolean,
    CONSTRAINT loan_product_settings_guarantor_commitment_stage_check CHECK (((guarantor_commitment_stage)::text = ANY (ARRAY[('MANAGER'::character varying)::text, ('LOAN_OFFICER'::character varying)::text, ('BOARD'::character varying)::text, ('ACCOUNTANT'::character varying)::text, ('DISBURSEMENT_OFFICER'::character varying)::text]))),
    CONSTRAINT loan_product_settings_interest_method_check CHECK (((interest_method)::text = ANY (ARRAY[('FLAT_RATE'::character varying)::text, ('REDUCING_BALANCE'::character varying)::text]))),
    CONSTRAINT loan_product_settings_loan_type_check CHECK (((loan_type)::text = ANY (ARRAY['LOAN_ADVANCE'::text, 'EDUCATION_LOAN'::text, 'EMERGENCY_LOAN'::text, 'DEVELOPMENT_LOAN'::text, 'CUSTOMIZED_LOAN'::text]))),
    CONSTRAINT loan_product_settings_product_status_check CHECK (((product_status)::text = ANY (ARRAY[('DRAFT'::character varying)::text, ('ACTIVE'::character varying)::text, ('SUSPENDED'::character varying)::text, ('RETIRED'::character varying)::text]))),
    CONSTRAINT loan_product_settings_workflow_start_stage_check CHECK (((workflow_start_stage)::text = ANY (ARRAY[('MANAGER'::character varying)::text, ('LOAN_OFFICER'::character varying)::text, ('BOARD'::character varying)::text, ('ACCOUNTANT'::character varying)::text, ('DISBURSEMENT_OFFICER'::character varying)::text])))
);


--
-- Name: manager_reviews; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.manager_reviews (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    decision character varying(255) NOT NULL,
    loan_application_id uuid NOT NULL,
    manager_member_id uuid NOT NULL,
    manager_signature_text character varying(255),
    manager_signature_verified_at timestamp(6) with time zone,
    reasons character varying(255),
    review_stage character varying(255) NOT NULL,
    CONSTRAINT manager_reviews_decision_check CHECK (((decision)::text = ANY (ARRAY[('ACCEPT'::character varying)::text, ('REJECT'::character varying)::text])))
);


--
-- Name: member_payment_details; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.member_payment_details (
    member_id uuid NOT NULL,
    destination_type character varying(32) NOT NULL,
    provider character varying(120) NOT NULL,
    account_holder_name character varying(160) NOT NULL,
    account_identifier character varying(120) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT ck_member_payment_details_destination_type CHECK (((destination_type)::text = ANY (ARRAY[('BANK_ACCOUNT'::character varying)::text, ('MOBILE_MONEY'::character varying)::text, ('OTHER'::character varying)::text])))
);


--
-- Name: member_staff_roles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.member_staff_roles (
    member_id uuid NOT NULL,
    role_name character varying(255) NOT NULL,
    CONSTRAINT member_staff_roles_role_name_check CHECK (((role_name)::text = ANY (ARRAY[('MEMBER'::character varying)::text, ('MINOR_ADMIN'::character varying)::text, ('MANAGER'::character varying)::text, ('ACCOUNTANT'::character varying)::text, ('DISBURSEMENT_OFFICER'::character varying)::text, ('CHAIRPERSON'::character varying)::text, ('BOARD'::character varying)::text, ('CREDIT_COMMITTEE'::character varying)::text, ('LOAN_OFFICER'::character varying)::text, ('ADMIN'::character varying)::text])))
);


--
-- Name: members; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.members (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    email character varying(255),
    full_name character varying(255) NOT NULL,
    member_no character varying(255) NOT NULL,
    staff_no character varying(255),
    password_hash character varying(255) NOT NULL,
    phone character varying(255),
    "position" character varying(255) NOT NULL,
    profile_last_synced_at timestamp(6) with time zone,
    rank integer,
    sacco_id character varying(255),
    status character varying(255) NOT NULL,
    station_id character varying(255),
    is_member boolean,
    signature_registered_at timestamp(6) with time zone,
    signature_text character varying(255),
    phone_verified_at timestamp with time zone,
    staff_access_status character varying(64) DEFAULT 'NONE'::character varying NOT NULL,
    staff_access_assigned_at timestamp(6) with time zone,
    staff_access_activated_at timestamp(6) with time zone,
    CONSTRAINT members_phone_format_check CHECK (((phone IS NULL) OR ((phone)::text ~ '^255[0-9]{9}$'::text))),
    CONSTRAINT members_position_check CHECK ((("position")::text = ANY (ARRAY[('MEMBER'::character varying)::text, ('MINOR_ADMIN'::character varying)::text, ('MANAGER'::character varying)::text, ('ACCOUNTANT'::character varying)::text, ('DISBURSEMENT_OFFICER'::character varying)::text, ('CHAIRPERSON'::character varying)::text, ('BOARD'::character varying)::text, ('CREDIT_COMMITTEE'::character varying)::text, ('LOAN_OFFICER'::character varying)::text, ('ADMIN'::character varying)::text]))),
    CONSTRAINT members_status_check CHECK (((status)::text = ANY (ARRAY[('INVITED'::character varying)::text, ('ACTIVE'::character varying)::text, ('INACTIVE'::character varying)::text])))
);


--
-- Name: minor_admin_invitations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.minor_admin_invitations (
    id uuid NOT NULL,
    claimed_at timestamp(6) with time zone,
    expires_at timestamp(6) with time zone NOT NULL,
    invited_at timestamp(6) with time zone NOT NULL,
    invited_by uuid NOT NULL,
    member_id uuid NOT NULL,
    revoked_at timestamp(6) with time zone,
    revoked_by uuid,
    token_hash character varying(255) NOT NULL
);


--
-- Name: notifications; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.notifications (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    payload jsonb NOT NULL,
    read_at timestamp(6) with time zone,
    recipient_member_id uuid NOT NULL,
    sent_at timestamp(6) with time zone,
    status character varying(255) NOT NULL,
    type character varying(255) NOT NULL,
    CONSTRAINT notifications_status_check CHECK (((status)::text = ANY (ARRAY[('PENDING'::character varying)::text, ('SENT'::character varying)::text, ('FAILED'::character varying)::text])))
);


--
-- Name: outbox_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.outbox_events (
    id uuid NOT NULL,
    aggregate_id uuid NOT NULL,
    aggregate_type character varying(255) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    event_type character varying(255) NOT NULL,
    payload jsonb NOT NULL,
    published_at timestamp(6) with time zone,
    status character varying(255) NOT NULL,
    CONSTRAINT outbox_events_status_check CHECK (((status)::text = ANY (ARRAY[('NEW'::character varying)::text, ('PUBLISHED'::character varying)::text, ('FAILED'::character varying)::text])))
);


--
-- Name: platform_branding_settings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.platform_branding_settings (
    id character varying(40) NOT NULL,
    logo_min_width_px integer NOT NULL,
    logo_min_height_px integer NOT NULL,
    logo_max_width_px integer NOT NULL,
    logo_max_height_px integer NOT NULL,
    logo_max_file_size_kb integer NOT NULL,
    updated_by_member_id uuid,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT ck_platform_branding_logo_file_size CHECK (((logo_max_file_size_kb >= 64) AND (logo_max_file_size_kb <= 5120))),
    CONSTRAINT ck_platform_branding_logo_height_order CHECK ((logo_min_height_px <= logo_max_height_px)),
    CONSTRAINT ck_platform_branding_logo_max_height CHECK (((logo_max_height_px >= 32) AND (logo_max_height_px <= 4096))),
    CONSTRAINT ck_platform_branding_logo_max_width CHECK (((logo_max_width_px >= 32) AND (logo_max_width_px <= 4096))),
    CONSTRAINT ck_platform_branding_logo_min_height CHECK (((logo_min_height_px >= 32) AND (logo_min_height_px <= 4096))),
    CONSTRAINT ck_platform_branding_logo_min_width CHECK (((logo_min_width_px >= 32) AND (logo_min_width_px <= 4096))),
    CONSTRAINT ck_platform_branding_logo_width_order CHECK ((logo_min_width_px <= logo_max_width_px))
);


--
-- Name: platform_sms_settings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.platform_sms_settings (
    id character varying(255) NOT NULL,
    low_percent integer NOT NULL,
    critical_percent integer NOT NULL,
    updated_by_member_id uuid,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT ck_platform_sms_critical_percent CHECK (((critical_percent >= 1) AND (critical_percent <= 98))),
    CONSTRAINT ck_platform_sms_low_percent CHECK (((low_percent >= 1) AND (low_percent <= 99))),
    CONSTRAINT ck_platform_sms_threshold_order CHECK ((critical_percent < low_percent))
);


--
-- Name: registered_saccos; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.registered_saccos (
    sacco_id character varying(255) NOT NULL,
    active boolean NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    sacco_name character varying(255) NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL
);


--
-- Name: reversal_requests; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.reversal_requests (
    id uuid NOT NULL,
    approver_member_id uuid,
    approver_role character varying(255),
    created_at timestamp(6) with time zone NOT NULL,
    decided_at timestamp(6) with time zone,
    decided_by_member_id uuid,
    decision_reason character varying(255),
    guarantor_request_id uuid,
    loan_application_id uuid NOT NULL,
    request_reason character varying(255),
    requester_member_id uuid NOT NULL,
    sacco_id character varying(255) NOT NULL,
    status character varying(255) NOT NULL,
    type character varying(255) NOT NULL,
    version integer NOT NULL,
    CONSTRAINT reversal_requests_approver_role_check CHECK (((approver_role)::text = ANY (ARRAY[('MEMBER'::character varying)::text, ('MANAGER'::character varying)::text, ('BOARD'::character varying)::text, ('CHAIRPERSON'::character varying)::text, ('ADMIN'::character varying)::text]))),
    CONSTRAINT reversal_requests_status_check CHECK (((status)::text = ANY (ARRAY[('PENDING'::character varying)::text, ('APPROVED'::character varying)::text, ('REJECTED'::character varying)::text]))),
    CONSTRAINT reversal_requests_type_check CHECK (((type)::text = ANY (ARRAY[('GUARANTOR_DECISION_UNDO'::character varying)::text, ('MANAGER_STAGE_WITHDRAWAL'::character varying)::text])))
);


--
-- Name: sacco_loan_app_counter; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sacco_loan_app_counter (
    sacco_id character varying(64) NOT NULL,
    last_number bigint NOT NULL
);


--
-- Name: sacco_numeric_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.sacco_numeric_id_seq
    AS integer
    START WITH 1001
    INCREMENT BY 1
    MINVALUE 1001
    MAXVALUE 9999
    CACHE 1;


--
-- Name: sacco_settings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sacco_settings (
    sacco_id character varying(255) NOT NULL,
    board_quorum integer NOT NULL,
    board_size integer NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    default_language character varying(255) NOT NULL,
    external_sacco_name character varying(255),
    external_station_id character varying(255) NOT NULL,
    max_loan_savings_ratio numeric(6,4) NOT NULL,
    required_guarantors integer NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    application_fee numeric(18,2),
    board_review_required boolean,
    loan_officer_review_required boolean,
    access_restriction_reason character varying(500),
    access_suspended_at timestamp(6) with time zone,
    access_suspended_by_member_id uuid,
    payment_due_date date,
    access_status character varying(255),
    applicant_forfeited_lookback_days integer,
    applicant_max_active_loan_amount numeric(18,2),
    applicant_max_defaulted_loans integer,
    applicant_max_forfeited_loans integer,
    guarantor_max_active_loan_amount numeric(18,2),
    guarantor_max_defaulted_loans integer,
    guarantor_max_guaranteed_loan_amount numeric(18,2),
    guarantor_min_savings numeric(18,2),
    applicant_forfeited_wait_days integer,
    loan_fee_payment_account character varying(120),
    loan_fee_payment_instructions character varying(500),
    loan_fee_payment_method character varying(120),
    loan_fee_payment_payee character varying(160),
    guarantor_with_active_loan_allowed boolean,
    notification_delivery_prefs jsonb DEFAULT '{"LOAN_STATUS": {"sms": false, "email": true}, "GUARANTEE_REQUEST": {"sms": false, "email": true}, "REPAYMENT_REMINDER": {"sms": false, "email": true}}'::jsonb NOT NULL,
    CONSTRAINT sacco_settings_access_status_check CHECK (((access_status)::text = ANY (ARRAY[('ACTIVE'::character varying)::text, ('PAYMENT_DUE'::character varying)::text, ('SUSPENDED'::character varying)::text])))
);


--
-- Name: sacco_station_policies; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sacco_station_policies (
    id uuid NOT NULL,
    applicant_forfeited_lookback_days integer,
    applicant_max_active_loan_amount numeric(18,2),
    applicant_max_defaulted_loans integer,
    applicant_max_forfeited_loans integer,
    created_at timestamp(6) with time zone NOT NULL,
    guarantor_max_active_loan_amount numeric(18,2),
    guarantor_max_defaulted_loans integer,
    guarantor_max_guaranteed_loan_amount numeric(18,2),
    guarantor_min_savings numeric(18,2),
    sacco_id character varying(255) NOT NULL,
    station_id character varying(255) NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    applicant_forfeited_wait_days integer,
    guarantor_with_active_loan_allowed boolean
);


--
-- Name: sacco_stations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sacco_stations (
    id uuid NOT NULL,
    active boolean NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    sacco_id character varying(255) NOT NULL,
    station_id character varying(255) NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    address_location character varying(255),
    access_restriction_reason character varying(500),
    access_status character varying(255),
    access_suspended_at timestamp(6) with time zone,
    access_suspended_by_member_id uuid,
    payment_due_date date,
    otp_delivery_channel character varying(255) DEFAULT 'SMS_WITH_EMAIL_FALLBACK'::character varying NOT NULL,
    otp_requirement_mode character varying(255),
    CONSTRAINT ck_sacco_stations_otp_delivery_channel CHECK (((otp_delivery_channel)::text = ANY (ARRAY[('EMAIL'::character varying)::text, ('SMS'::character varying)::text, ('SMS_WITH_EMAIL_FALLBACK'::character varying)::text]))),
    CONSTRAINT sacco_stations_access_status_check CHECK (((access_status)::text = ANY (ARRAY[('ACTIVE'::character varying)::text, ('PAYMENT_DUE'::character varying)::text, ('SUSPENDED'::character varying)::text]))),
    CONSTRAINT sacco_stations_otp_requirement_mode_check CHECK (((otp_requirement_mode)::text = ANY (ARRAY[('LOGIN_MFA_ONLY'::character varying)::text, ('APPROVAL_ONLY'::character varying)::text, ('LOGIN_MFA_AND_APPROVAL'::character varying)::text])))
);


--
-- Name: sms_usage_ledger; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sms_usage_ledger (
    id uuid NOT NULL,
    account_id uuid,
    sacco_id character varying(255),
    station_id character varying(255),
    notification_id uuid,
    event_type character varying(255),
    unit_change bigint NOT NULL,
    outcome character varying(255) NOT NULL,
    provider_reference character varying(500),
    actor_member_id uuid,
    note character varying(500),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    event_count bigint DEFAULT 1 NOT NULL,
    last_occurred_at timestamp with time zone NOT NULL,
    CONSTRAINT ck_sms_usage_ledger_event_count_positive CHECK ((event_count > 0))
);


--
-- Name: station_sms_accounts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.station_sms_accounts (
    id uuid NOT NULL,
    sacco_id character varying(255) NOT NULL,
    station_id character varying(255) NOT NULL,
    available_units bigint DEFAULT 0 NOT NULL,
    warning_baseline bigint DEFAULT 0 NOT NULL,
    status character varying(255) DEFAULT 'DEPLETED'::character varying NOT NULL,
    low_alert_sent boolean DEFAULT false NOT NULL,
    critical_alert_sent boolean DEFAULT false NOT NULL,
    depleted_alert_sent boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    alert_reserved_units bigint DEFAULT 0 NOT NULL,
    depleted_alert_sms_sent_count bigint DEFAULT 0 NOT NULL,
    CONSTRAINT ck_station_sms_alert_reserved_units CHECK (((alert_reserved_units >= 0) AND (alert_reserved_units <= 3))),
    CONSTRAINT ck_station_sms_available_units CHECK ((available_units >= 0)),
    CONSTRAINT ck_station_sms_depleted_alert_sms_sent_count CHECK ((depleted_alert_sms_sent_count >= 0)),
    CONSTRAINT ck_station_sms_warning_baseline CHECK ((warning_baseline >= 0))
);


--
-- Name: stored_upload_migrations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.stored_upload_migrations (
    migration_key character varying(120) NOT NULL,
    completed_at timestamp with time zone NOT NULL,
    imported_count bigint NOT NULL,
    failed_count bigint NOT NULL
);


--
-- Name: stored_uploads; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.stored_uploads (
    id uuid NOT NULL,
    owner_type character varying(60) NOT NULL,
    owner_id character varying(255) NOT NULL,
    category character varying(80) NOT NULL,
    original_name character varying(500) NOT NULL,
    content_type character varying(255) NOT NULL,
    size_bytes bigint NOT NULL,
    sha256 character varying(64) NOT NULL,
    content bytea NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL
);


--
-- Name: user_settings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_settings (
    member_id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    language character varying(255) NOT NULL,
    notification_prefs jsonb NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL
);


--
-- Name: accounts_savings accounts_savings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.accounts_savings
    ADD CONSTRAINT accounts_savings_pkey PRIMARY KEY (id);


--
-- Name: admin_incidents admin_incidents_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admin_incidents
    ADD CONSTRAINT admin_incidents_pkey PRIMARY KEY (id);


--
-- Name: app_usage_events app_usage_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_usage_events
    ADD CONSTRAINT app_usage_events_pkey PRIMARY KEY (id);


--
-- Name: app_usage_page_metrics app_usage_page_metrics_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_usage_page_metrics
    ADD CONSTRAINT app_usage_page_metrics_pkey PRIMARY KEY (id);


--
-- Name: audit_log audit_log_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_log
    ADD CONSTRAINT audit_log_pkey PRIMARY KEY (id);


--
-- Name: board_reviews board_reviews_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.board_reviews
    ADD CONSTRAINT board_reviews_pkey PRIMARY KEY (id);


--
-- Name: email_otp_tokens email_otp_tokens_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.email_otp_tokens
    ADD CONSTRAINT email_otp_tokens_pkey PRIMARY KEY (id);


--
-- Name: guarantor_requests guarantor_requests_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.guarantor_requests
    ADD CONSTRAINT guarantor_requests_pkey PRIMARY KEY (id);


--
-- Name: loan_applications loan_applications_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.loan_applications
    ADD CONSTRAINT loan_applications_pkey PRIMARY KEY (id);


-- Name: loan_product_board_reviewers loan_product_board_reviewers_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.loan_product_board_reviewers
    ADD CONSTRAINT loan_product_board_reviewers_pkey PRIMARY KEY (id);


--
-- Name: loan_product_required_attachments loan_product_required_attachments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.loan_product_required_attachments
    ADD CONSTRAINT loan_product_required_attachments_pkey PRIMARY KEY (id);


--
-- Name: loan_product_settings loan_product_settings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.loan_product_settings
    ADD CONSTRAINT loan_product_settings_pkey PRIMARY KEY (id);


--
-- Name: manager_reviews manager_reviews_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.manager_reviews
    ADD CONSTRAINT manager_reviews_pkey PRIMARY KEY (id);


--
-- Name: member_payment_details member_payment_details_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.member_payment_details
    ADD CONSTRAINT member_payment_details_pkey PRIMARY KEY (member_id);


--
-- Name: member_staff_roles member_staff_roles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.member_staff_roles
    ADD CONSTRAINT member_staff_roles_pkey PRIMARY KEY (member_id, role_name);


--
-- Name: members members_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.members
    ADD CONSTRAINT members_pkey PRIMARY KEY (id);


--
-- Name: minor_admin_invitations minor_admin_invitations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.minor_admin_invitations
    ADD CONSTRAINT minor_admin_invitations_pkey PRIMARY KEY (id);


--
-- Name: notifications notifications_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notifications
    ADD CONSTRAINT notifications_pkey PRIMARY KEY (id);


--
-- Name: outbox_events outbox_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.outbox_events
    ADD CONSTRAINT outbox_events_pkey PRIMARY KEY (id);


--
-- Name: platform_branding_settings platform_branding_settings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.platform_branding_settings
    ADD CONSTRAINT platform_branding_settings_pkey PRIMARY KEY (id);


--
-- Name: platform_sms_settings platform_sms_settings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.platform_sms_settings
    ADD CONSTRAINT platform_sms_settings_pkey PRIMARY KEY (id);


--
-- Name: registered_saccos registered_saccos_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.registered_saccos
    ADD CONSTRAINT registered_saccos_pkey PRIMARY KEY (sacco_id);


--
-- Name: reversal_requests reversal_requests_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reversal_requests
    ADD CONSTRAINT reversal_requests_pkey PRIMARY KEY (id);


--
-- Name: sacco_loan_app_counter sacco_loan_app_counter_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sacco_loan_app_counter
    ADD CONSTRAINT sacco_loan_app_counter_pkey PRIMARY KEY (sacco_id);


--
-- Name: sacco_settings sacco_settings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sacco_settings
    ADD CONSTRAINT sacco_settings_pkey PRIMARY KEY (sacco_id);


--
-- Name: sacco_station_policies sacco_station_policies_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sacco_station_policies
    ADD CONSTRAINT sacco_station_policies_pkey PRIMARY KEY (id);


--
-- Name: sacco_stations sacco_stations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sacco_stations
    ADD CONSTRAINT sacco_stations_pkey PRIMARY KEY (id);


--
-- Name: sms_usage_ledger sms_usage_ledger_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sms_usage_ledger
    ADD CONSTRAINT sms_usage_ledger_pkey PRIMARY KEY (id);


--
-- Name: station_sms_accounts station_sms_accounts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.station_sms_accounts
    ADD CONSTRAINT station_sms_accounts_pkey PRIMARY KEY (id);


--
-- Name: stored_upload_migrations stored_upload_migrations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stored_upload_migrations
    ADD CONSTRAINT stored_upload_migrations_pkey PRIMARY KEY (migration_key);


--
-- Name: stored_uploads stored_uploads_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stored_uploads
    ADD CONSTRAINT stored_uploads_pkey PRIMARY KEY (id);


--
-- Name: guarantor_requests uk695pu1ejdrmydcxnx9vjjjkfv; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.guarantor_requests
    ADD CONSTRAINT uk695pu1ejdrmydcxnx9vjjjkfv UNIQUE (loan_application_id, guarantor_member_id);


--
-- Name: app_usage_page_metrics uk_app_usage_page_metrics_bucket_scope_page_device; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_usage_page_metrics
    ADD CONSTRAINT uk_app_usage_page_metrics_bucket_scope_page_device UNIQUE (bucket_start, sacco_id, station_id, page_path, device_type, browser_family);


--
-- Name: board_reviews uk_board_reviews_loan_member_stage; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.board_reviews
    ADD CONSTRAINT uk_board_reviews_loan_member_stage UNIQUE (loan_application_id, board_member_id, review_stage);


--
-- Name: loan_product_board_reviewers uk_loan_product_stage_reviewer; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.loan_product_board_reviewers
    ADD CONSTRAINT uk_loan_product_stage_reviewer UNIQUE (loan_product_setting_id, review_stage, board_member_id);


--
-- Name: sacco_stations uk_sacco_station; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sacco_stations
    ADD CONSTRAINT uk_sacco_station UNIQUE (sacco_id, station_id);


--
-- Name: sacco_station_policies uk_sacco_station_policy; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sacco_station_policies
    ADD CONSTRAINT uk_sacco_station_policy UNIQUE (sacco_id, station_id);


--
-- Name: station_sms_accounts uk_station_sms_account; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.station_sms_accounts
    ADD CONSTRAINT uk_station_sms_account UNIQUE (sacco_id, station_id);


--
-- Name: members ukfhh3rprrwq7dq8ol3ds43em3y; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.members
    ADD CONSTRAINT ukfhh3rprrwq7dq8ol3ds43em3y UNIQUE (member_no);


--
-- Name: loan_product_settings ukjcby5gkf608j66k34lkg5wn83; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.loan_product_settings
    ADD CONSTRAINT ukjcby5gkf608j66k34lkg5wn83 UNIQUE (sacco_id, product_code);


--
-- Name: user_settings user_settings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_settings
    ADD CONSTRAINT user_settings_pkey PRIMARY KEY (member_id);


--
-- Name: idx_app_usage_events_occurred_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_app_usage_events_occurred_at ON public.app_usage_events USING btree (occurred_at);


--
-- Name: idx_app_usage_events_scope_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_app_usage_events_scope_time ON public.app_usage_events USING btree (sacco_id, station_id, occurred_at);


--
-- Name: idx_app_usage_events_type_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_app_usage_events_type_time ON public.app_usage_events USING btree (event_type, occurred_at);


--
-- Name: idx_app_usage_page_metrics_bucket; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_app_usage_page_metrics_bucket ON public.app_usage_page_metrics USING btree (bucket_start);


--
-- Name: idx_app_usage_page_metrics_scope_bucket; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_app_usage_page_metrics_scope_bucket ON public.app_usage_page_metrics USING btree (sacco_id, station_id, bucket_start);


--
-- Name: idx_audit_log_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_log_created_at ON public.audit_log USING btree (created_at);


--
-- Name: idx_audit_log_scope_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_log_scope_created_at ON public.audit_log USING btree (sacco_id, station_id, created_at DESC);


--
-- Name: idx_audit_log_status_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_log_status_created_at ON public.audit_log USING btree (event_status, created_at DESC);


-- Name: idx_loan_product_board_reviewers_member; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_loan_product_board_reviewers_member ON public.loan_product_board_reviewers USING btree (board_member_id);


--
-- Name: idx_loan_product_board_reviewers_product; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_loan_product_board_reviewers_product ON public.loan_product_board_reviewers USING btree (loan_product_setting_id);


--
-- Name: idx_loan_product_board_reviewers_product_stage; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_loan_product_board_reviewers_product_stage ON public.loan_product_board_reviewers USING btree (loan_product_setting_id, review_stage);


--
-- Name: idx_outbox_events_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_outbox_events_created_at ON public.outbox_events USING btree (created_at);


--
-- Name: idx_outbox_events_status_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_outbox_events_status_created_at ON public.outbox_events USING btree (status, created_at);


--
-- Name: idx_sms_usage_ledger_account_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sms_usage_ledger_account_created ON public.sms_usage_ledger USING btree (account_id, created_at DESC);


--
-- Name: idx_sms_usage_ledger_depleted_block_bucket; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sms_usage_ledger_depleted_block_bucket ON public.sms_usage_ledger USING btree (account_id, event_type, outcome, note, created_at DESC) WHERE ((outcome)::text = 'BLOCKED'::text);


--
-- Name: idx_sms_usage_ledger_scope_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sms_usage_ledger_scope_created ON public.sms_usage_ledger USING btree (sacco_id, station_id, created_at DESC);


--
-- Name: idx_station_sms_account_scope; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_station_sms_account_scope ON public.station_sms_accounts USING btree (sacco_id, station_id);


--
-- Name: idx_station_sms_account_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_station_sms_account_status ON public.station_sms_accounts USING btree (status);


--
-- Name: idx_stored_uploads_owner; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_stored_uploads_owner ON public.stored_uploads USING btree (owner_type, owner_id);


--
-- Name: idx_stored_uploads_owner_category; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_stored_uploads_owner_category ON public.stored_uploads USING btree (owner_type, owner_id, category);


--
-- Name: ix_loan_applications_applicant_disbursement_ack; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_loan_applications_applicant_disbursement_ack ON public.loan_applications USING btree (applicant_member_id, status, applicant_disbursement_acknowledged_at, updated_at DESC);


--
-- Name: ix_loan_applications_applicant_rejection_ack; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_loan_applications_applicant_rejection_ack ON public.loan_applications USING btree (applicant_member_id, status, applicant_rejection_acknowledged_at, updated_at DESC);


--
-- Name: ix_loan_applications_product_setting; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_loan_applications_product_setting ON public.loan_applications USING btree (loan_product_setting_id);


--
-- Name: ix_loan_applications_sacco_station; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_loan_applications_sacco_station ON public.loan_applications USING btree (sacco_id, station_id);


--
-- Name: uk_members_staff_no; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_members_staff_no ON public.members USING btree (lower((staff_no)::text)) WHERE (staff_no IS NOT NULL);


--
-- Name: ix_stored_uploads_owner_category; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_stored_uploads_owner_category ON public.stored_uploads USING btree (owner_type, owner_id, category);


-- Name: ux_loan_applications_sacco_appnum; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_loan_applications_sacco_appnum ON public.loan_applications USING btree (sacco_id, application_number);


--
-- Name: ux_loan_applications_sacco_loanid; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_loan_applications_sacco_loanid ON public.loan_applications USING btree (sacco_id, loan_id) WHERE (loan_id IS NOT NULL);


--
-- Name: ux_stored_uploads_owner_category_id; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_stored_uploads_owner_category_id ON public.stored_uploads USING btree (owner_type, owner_id, category, id);


--
-- Name: loan_applications fk_loan_applications_product_setting; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.loan_applications
    ADD CONSTRAINT fk_loan_applications_product_setting FOREIGN KEY (loan_product_setting_id) REFERENCES public.loan_product_settings(id);


--
-- Name: sms_usage_ledger fk_sms_usage_ledger_account; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sms_usage_ledger
    ADD CONSTRAINT fk_sms_usage_ledger_account FOREIGN KEY (account_id) REFERENCES public.station_sms_accounts(id);


--
-- Name: member_staff_roles fka80i58tpiuin1t8c2wbm2jov9; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.member_staff_roles
    ADD CONSTRAINT fka80i58tpiuin1t8c2wbm2jov9 FOREIGN KEY (member_id) REFERENCES public.members(id);


--
-- Name: members fkp87rhn2r970fd2tbv7jayifsx; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.members
    ADD CONSTRAINT fkp87rhn2r970fd2tbv7jayifsx FOREIGN KEY (sacco_id) REFERENCES public.registered_saccos(sacco_id);


--
-- Name: member_payment_details member_payment_details_member_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.member_payment_details
    ADD CONSTRAINT member_payment_details_member_id_fkey FOREIGN KEY (member_id) REFERENCES public.members(id) ON DELETE CASCADE;


--
-- PostgreSQL database dump complete
--

\unrestrict aTG6i3WBETRjdOOPdB1XIMsrw9JfmXgtyl1Wg5rR4kGgsfR9dkuPnxXzRfbRwQm
