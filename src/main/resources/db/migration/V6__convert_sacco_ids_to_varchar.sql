-- Manual migration script for PostgreSQL.
-- This project currently relies on Hibernate ddl-auto=update, so run this script manually
-- against the database when you want to convert existing UUID-based SACCO IDs into
-- readable string IDs.

BEGIN;

ALTER TABLE sacco_settings
    ALTER COLUMN sacco_id TYPE VARCHAR(64) USING sacco_id::text;

ALTER TABLE members
    ALTER COLUMN sacco_id TYPE VARCHAR(64) USING sacco_id::text;

ALTER TABLE loan_product_settings
    ALTER COLUMN sacco_id TYPE VARCHAR(64) USING sacco_id::text;

ALTER TABLE loan_applications
    ALTER COLUMN sacco_id TYPE VARCHAR(64) USING sacco_id::text;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = 'public'
          AND table_name = 'admin_incidents'
    ) THEN
        ALTER TABLE admin_incidents
            ALTER COLUMN sacco_id TYPE VARCHAR(64) USING sacco_id::text;
    END IF;
END $$;

CREATE TEMP TABLE tmp_sacco_id_map AS
SELECT
    sacco_id AS old_sacco_id,
    CONCAT('SACCO-', 1000 + ROW_NUMBER() OVER (ORDER BY sacco_id)) AS new_sacco_id
FROM sacco_settings;

UPDATE sacco_settings s
SET sacco_id = m.new_sacco_id,
    external_station_id = COALESCE(NULLIF(TRIM(external_station_id), ''), m.new_sacco_id)
FROM tmp_sacco_id_map m
WHERE s.sacco_id = m.old_sacco_id;

UPDATE members x
SET sacco_id = m.new_sacco_id
FROM tmp_sacco_id_map m
WHERE x.sacco_id = m.old_sacco_id;

UPDATE loan_product_settings x
SET sacco_id = m.new_sacco_id
FROM tmp_sacco_id_map m
WHERE x.sacco_id = m.old_sacco_id;

UPDATE loan_applications x
SET sacco_id = m.new_sacco_id
FROM tmp_sacco_id_map m
WHERE x.sacco_id = m.old_sacco_id;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = 'public'
          AND table_name = 'admin_incidents'
    ) THEN
        UPDATE admin_incidents x
        SET sacco_id = m.new_sacco_id
        FROM tmp_sacco_id_map m
        WHERE x.sacco_id = m.old_sacco_id;
    END IF;
END $$;

ALTER TABLE sacco_settings
    DROP COLUMN IF EXISTS public_id;

ALTER TABLE loan_product_settings
    DROP COLUMN IF EXISTS sacco_public_id;

ALTER TABLE loan_applications
    DROP COLUMN IF EXISTS sacco_public_id;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = 'public'
          AND table_name = 'admin_incidents'
    ) THEN
        ALTER TABLE admin_incidents
            DROP COLUMN IF EXISTS sacco_public_id;
    END IF;
END $$;

DROP TABLE IF EXISTS tmp_sacco_id_map;

COMMIT;
