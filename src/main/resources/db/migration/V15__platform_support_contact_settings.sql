CREATE TABLE IF NOT EXISTS public.platform_support_contact_settings (
    id varchar(40) NOT NULL,
    display_name varchar(120) NOT NULL,
    display_role varchar(120) NOT NULL,
    phone varchar(40) NOT NULL,
    email varchar(160) NOT NULL,
    office_hours varchar(120) NOT NULL,
    support_note varchar(280) NOT NULL,
    updated_by_member_id uuid,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT platform_support_contact_settings_pkey PRIMARY KEY (id)
);

INSERT INTO public.platform_support_contact_settings (
    id,
    display_name,
    display_role,
    phone,
    email,
    office_hours,
    support_note,
    created_at,
    updated_at
)
VALUES ('DEFAULT', 'Platform Support', 'Super Admin Support', '', '', '', '', now(), now())
ON CONFLICT (id) DO NOTHING;
