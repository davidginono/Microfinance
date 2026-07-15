ALTER TABLE public.audit_log
    ADD COLUMN IF NOT EXISTS event_status character varying(20),
    ADD COLUMN IF NOT EXISTS sacco_id character varying(255),
    ADD COLUMN IF NOT EXISTS station_id character varying(255),
    ADD COLUMN IF NOT EXISTS action_description character varying(255),
    ADD COLUMN IF NOT EXISTS reference_type character varying(80),
    ADD COLUMN IF NOT EXISTS reference_value character varying(255),
    ADD COLUMN IF NOT EXISTS request_metadata jsonb;

UPDATE public.audit_log
SET event_status = CASE
    WHEN lower(coalesce(after_state::text, '') || ' ' || coalesce(before_state::text, '')) LIKE '%"result": "error"%'
      OR lower(coalesce(after_state::text, '') || ' ' || coalesce(before_state::text, '')) LIKE '%"result":"error"%'
      OR lower(coalesce(after_state::text, '') || ' ' || coalesce(before_state::text, '')) LIKE '%"result": "fail"%'
      OR lower(coalesce(after_state::text, '') || ' ' || coalesce(before_state::text, '')) LIKE '%"result":"fail"%'
      OR lower(coalesce(after_state::text, '') || ' ' || coalesce(before_state::text, '')) LIKE '%"valid": false%'
      OR lower(coalesce(after_state::text, '') || ' ' || coalesce(before_state::text, '')) LIKE '%"valid":false%'
    THEN 'FAIL'
    ELSE 'SUCCESS'
END
WHERE event_status IS NULL;

UPDATE public.audit_log
SET sacco_id = coalesce(
        nullif(after_state ->> 'saccoId', ''),
        nullif(before_state ->> 'saccoId', '')
    ),
    station_id = coalesce(
        nullif(after_state ->> 'stationId', ''),
        nullif(before_state ->> 'stationId', '')
    )
WHERE sacco_id IS NULL OR station_id IS NULL;

UPDATE public.audit_log
SET action_description = CASE regexp_replace(upper(coalesce(action, '')), '[^A-Z0-9]', '', 'g')
    WHEN 'WEBCREATEDRAFT' THEN 'Loan application draft'
    WHEN 'WEBCREATDRAFT' THEN 'Loan application draft'
    WHEN 'WEBSUBMIT' THEN 'Loan application submitted'
    WHEN 'WEBCANCELSUBMISSION' THEN 'Loan submission moved back to draft'
    WHEN 'WEBDELETEAPPLICATION' THEN 'Loan application removed'
    WHEN 'WEBAPPROVEREQUEST' THEN 'Guarantor request approved'
    WHEN 'WEBREJECTREQUEST' THEN 'Guarantor request rejected'
    WHEN 'WEBREQUESTMEMBERLOGINOTP' THEN 'OTP request'
    WHEN 'WEBREQUESTSTAFFLOGINOTP' THEN 'OTP request'
    WHEN 'WEBVERIFYMEMBERLOGINOTP' THEN 'OTP verification'
    WHEN 'WEBVERIFYSTAFFLOGINOTP' THEN 'OTP verification'
    WHEN 'WEBREQUESTMEMBERREGISTRATIONOTP' THEN 'OTP request'
    WHEN 'WEBREGISTERMEMBERSUBMIT' THEN 'Registration'
    WHEN 'ADMINCREATESTAFFUSER' THEN 'Staff account created'
    WHEN 'ADMINCREATENONMEMBERUSER' THEN 'Staff account created'
    WHEN 'ADMINUPDATESTAFFUSER' THEN 'User access updated'
    WHEN 'ADMINUPDATENONMEMBERUSER' THEN 'User access updated'
    WHEN 'ADMINRETRYOUTBOX' THEN 'Outbox event retried'
    WHEN 'ADMINCREATELOANPRODUCT' THEN 'Loan product created'
    WHEN 'ADMINUPDATELOANPRODUCT' THEN 'Loan product updated'
    WHEN 'ADMINARCHIVELOANPRODUCT' THEN 'Loan product archived'
    WHEN 'PLATFORMSUSPENDSTATIONACCESS' THEN 'Station access suspended'
    WHEN 'PLATFORMRESTORESTATIONACCESS' THEN 'Station access restored'
    ELSE initcap(replace(regexp_replace(coalesce(action, 'System activity'), '([a-z0-9])([A-Z])', '\1 \2', 'g'), '_', ' '))
END
WHERE action_description IS NULL;

UPDATE public.audit_log
SET reference_type = CASE regexp_replace(upper(coalesce(entity_type, '')), '[^A-Z0-9]', '', 'g')
        WHEN 'MEMBER' THEN 'MEMBER'
        WHEN 'STAFFUSER' THEN 'STAFF'
        WHEN 'NONMEMBERUSER' THEN 'STAFF'
        WHEN 'LOANAPPLICATION' THEN 'LOAN_APPLICATION'
        WHEN 'LOANPRODUCT' THEN 'LOAN_PRODUCT'
        WHEN 'SACCOSTATION' THEN 'STATION'
        WHEN 'OUTBOX' THEN 'OUTBOX'
        ELSE coalesce(nullif(entity_type, ''), 'SYSTEM')
    END,
    reference_value = CASE
        WHEN coalesce(after_state ->> 'applicationNumber', before_state ->> 'applicationNumber') IS NOT NULL
            THEN 'Loan Application #' || coalesce(after_state ->> 'applicationNumber', before_state ->> 'applicationNumber')
        WHEN coalesce(after_state ->> 'loanId', before_state ->> 'loanId') IS NOT NULL
            THEN 'Loan ID ' || coalesce(after_state ->> 'loanId', before_state ->> 'loanId')
        WHEN coalesce(after_state ->> 'memberNo', before_state ->> 'memberNo') IS NOT NULL
            THEN CASE
                WHEN regexp_replace(upper(coalesce(entity_type, '')), '[^A-Z0-9]', '', 'g') IN ('STAFFUSER', 'NONMEMBERUSER')
                    THEN 'Staff ' || coalesce(after_state ->> 'memberNo', before_state ->> 'memberNo')
                ELSE 'Member ' || coalesce(after_state ->> 'memberNo', before_state ->> 'memberNo')
            END
        WHEN coalesce(after_state ->> 'stationId', before_state ->> 'stationId') IS NOT NULL
            THEN 'Station ' || coalesce(after_state ->> 'stationId', before_state ->> 'stationId')
        WHEN entity_id IS NOT NULL
            THEN '#' || left(entity_id::text, 8)
        ELSE '-'
    END
WHERE reference_value IS NULL;

CREATE INDEX IF NOT EXISTS idx_audit_log_status_created_at ON public.audit_log (event_status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_log_scope_created_at ON public.audit_log (sacco_id, station_id, created_at DESC);
