ALTER TABLE public.sacco_settings
    ADD COLUMN IF NOT EXISTS loan_top_up_enabled boolean NOT NULL DEFAULT true;
