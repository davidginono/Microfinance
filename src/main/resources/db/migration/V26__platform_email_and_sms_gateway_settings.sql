CREATE TABLE IF NOT EXISTS public.platform_email_settings (
    id varchar(40) NOT NULL,
    enabled boolean NOT NULL DEFAULT false,
    host varchar(255) NOT NULL DEFAULT '',
    port integer NOT NULL DEFAULT 465,
    username varchar(160) NOT NULL DEFAULT '',
    password_encrypted varchar(512) NOT NULL DEFAULT '',
    from_address varchar(160) NOT NULL DEFAULT '',
    override_recipient varchar(160) NOT NULL DEFAULT '',
    ssl_enabled boolean NOT NULL DEFAULT true,
    starttls_enabled boolean NOT NULL DEFAULT false,
    connection_timeout_ms integer NOT NULL DEFAULT 10000,
    read_timeout_ms integer NOT NULL DEFAULT 10000,
    write_timeout_ms integer NOT NULL DEFAULT 10000,
    updated_by_member_id uuid,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT platform_email_settings_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS public.platform_sms_gateway_settings (
    id varchar(40) NOT NULL,
    enabled boolean NOT NULL DEFAULT false,
    base_url varchar(255) NOT NULL DEFAULT '',
    send_path varchar(255) NOT NULL DEFAULT '',
    client_id varchar(120) NOT NULL DEFAULT '',
    api_key_encrypted varchar(512) NOT NULL DEFAULT '',
    sender_id varchar(40) NOT NULL DEFAULT '',
    connect_timeout_seconds integer NOT NULL DEFAULT 3,
    read_timeout_seconds integer NOT NULL DEFAULT 8,
    updated_by_member_id uuid,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT platform_sms_gateway_settings_pkey PRIMARY KEY (id)
);

INSERT INTO public.platform_email_settings (
    id,
    enabled,
    host,
    port,
    username,
    password_encrypted,
    from_address,
    override_recipient,
    ssl_enabled,
    starttls_enabled,
    connection_timeout_ms,
    read_timeout_ms,
    write_timeout_ms,
    created_at,
    updated_at
)
VALUES (
    'DEFAULT',
    false,
    '',
    465,
    '',
    '',
    '',
    '',
    true,
    false,
    10000,
    10000,
    10000,
    now(),
    now()
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.platform_sms_gateway_settings (
    id,
    enabled,
    base_url,
    send_path,
    client_id,
    api_key_encrypted,
    sender_id,
    connect_timeout_seconds,
    read_timeout_seconds,
    created_at,
    updated_at
)
VALUES (
    'DEFAULT',
    false,
    '',
    '',
    '',
    '',
    '',
    3,
    8,
    now(),
    now()
)
ON CONFLICT (id) DO NOTHING;
