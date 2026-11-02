ALTER TABLE public.platform_session_settings
    ADD COLUMN warning_enabled boolean NOT NULL DEFAULT false;
