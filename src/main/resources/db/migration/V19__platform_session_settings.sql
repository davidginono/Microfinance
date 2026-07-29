CREATE TABLE IF NOT EXISTS public.platform_session_settings (
    id varchar(40) NOT NULL,
    timeout_minutes integer NOT NULL,
    updated_by_member_id uuid,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT platform_session_settings_pkey PRIMARY KEY (id),
    CONSTRAINT ck_platform_session_timeout_minutes CHECK (timeout_minutes BETWEEN 2 AND 480)
);

INSERT INTO public.platform_session_settings (
    id,
    timeout_minutes,
    created_at,
    updated_at
)
VALUES ('DEFAULT', 30, now(), now())
ON CONFLICT (id) DO NOTHING;
