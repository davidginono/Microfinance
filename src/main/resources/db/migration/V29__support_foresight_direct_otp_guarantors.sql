ALTER TABLE public.guarantor_requests
    DROP CONSTRAINT IF EXISTS uk695pu1ejdrmydcxnx9vjjjkfv;

ALTER TABLE public.guarantor_requests
    ALTER COLUMN guarantor_member_id DROP NOT NULL;

ALTER TABLE public.guarantor_requests
    ADD COLUMN IF NOT EXISTS guarantor_source character varying(20) NOT NULL DEFAULT 'LMS',
    ADD COLUMN IF NOT EXISTS external_member_no character varying(80),
    ADD COLUMN IF NOT EXISTS external_station_id character varying(80),
    ADD COLUMN IF NOT EXISTS external_full_name character varying(255),
    ADD COLUMN IF NOT EXISTS external_email character varying(255),
    ADD COLUMN IF NOT EXISTS external_phone character varying(40),
    ADD COLUMN IF NOT EXISTS external_financial_snapshot jsonb;

UPDATE public.guarantor_requests
SET guarantor_source = 'LMS'
WHERE guarantor_source IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_guarantor_requests_loan_local_member
    ON public.guarantor_requests (loan_application_id, guarantor_member_id)
    WHERE guarantor_member_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_guarantor_requests_loan_external_member
    ON public.guarantor_requests (loan_application_id, lower(external_station_id), lower(external_member_no))
    WHERE guarantor_member_id IS NULL
      AND external_station_id IS NOT NULL
      AND external_member_no IS NOT NULL;
