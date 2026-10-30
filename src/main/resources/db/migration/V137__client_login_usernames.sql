-- Client numbers remain business identifiers. Login usernames have their own global namespace.
ALTER TABLE members ADD COLUMN username varchar(32);
ALTER TABLE members ADD CONSTRAINT ck_members_username_format
    CHECK (username IS NULL OR username ~ '^[a-z][a-z0-9._-]{2,31}$');
CREATE UNIQUE INDEX uq_members_username ON members (username);
CREATE INDEX ix_members_login_email ON members (upper(email)) WHERE email IS NOT NULL;

-- Preserve every existing identity, password and access flag. Choose readable email-based
-- usernames in a stable order, resolving both repeated email prefixes and suffix collisions.
DO $$
DECLARE
    account record;
    base_name text;
    candidate text;
    suffix text;
    attempt integer;
BEGIN
    FOR account IN SELECT id, email FROM members WHERE username IS NULL ORDER BY created_at, id LOOP
        base_name := regexp_replace(lower(split_part(coalesce(account.email, ''), '@', 1)), '[^a-z0-9._-]', '', 'g');
        IF base_name = '' THEN base_name := 'user';
        ELSIF base_name !~ '^[a-z]' THEN base_name := 'user_' || base_name; END IF;
        IF length(base_name) < 3 THEN base_name := 'user_' || base_name; END IF;
        base_name := left(base_name, 32);
        candidate := base_name;
        attempt := 1;
        WHILE EXISTS (SELECT 1 FROM members WHERE username = candidate) LOOP
            attempt := attempt + 1;
            suffix := '_' || attempt::text;
            candidate := left(base_name, 32 - length(suffix)) || suffix;
        END LOOP;
        UPDATE members SET username = candidate WHERE id = account.id;
    END LOOP;
END $$;
