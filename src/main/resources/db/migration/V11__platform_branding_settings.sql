CREATE TABLE IF NOT EXISTS public.platform_branding_settings (
    id varchar(40) NOT NULL,
    logo_min_width_px integer NOT NULL,
    logo_min_height_px integer NOT NULL,
    logo_max_width_px integer NOT NULL,
    logo_max_height_px integer NOT NULL,
    logo_max_file_size_kb integer NOT NULL,
    updated_by_member_id uuid,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT platform_branding_settings_pkey PRIMARY KEY (id),
    CONSTRAINT ck_platform_branding_logo_min_width CHECK (logo_min_width_px BETWEEN 32 AND 4096),
    CONSTRAINT ck_platform_branding_logo_min_height CHECK (logo_min_height_px BETWEEN 32 AND 4096),
    CONSTRAINT ck_platform_branding_logo_max_width CHECK (logo_max_width_px BETWEEN 32 AND 4096),
    CONSTRAINT ck_platform_branding_logo_max_height CHECK (logo_max_height_px BETWEEN 32 AND 4096),
    CONSTRAINT ck_platform_branding_logo_file_size CHECK (logo_max_file_size_kb BETWEEN 64 AND 5120),
    CONSTRAINT ck_platform_branding_logo_width_order CHECK (logo_min_width_px <= logo_max_width_px),
    CONSTRAINT ck_platform_branding_logo_height_order CHECK (logo_min_height_px <= logo_max_height_px)
);

INSERT INTO public.platform_branding_settings (
    id,
    logo_min_width_px,
    logo_min_height_px,
    logo_max_width_px,
    logo_max_height_px,
    logo_max_file_size_kb,
    created_at,
    updated_at
)
VALUES ('DEFAULT', 64, 64, 1024, 1024, 1024, now(), now())
ON CONFLICT (id) DO NOTHING;
