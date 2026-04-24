-- Split "loan ID" into:
--   application_number: per-SACCO sequential BIGINT assigned at draft creation
--   loan_id:            VARCHAR(20) numeric string entered by the manager at disbursement
--
-- The UUID primary key on loan_applications is unchanged; only displayed
-- identifiers change. All existing FKs and URLs continue to work.

ALTER TABLE loan_applications
    ADD COLUMN IF NOT EXISTS application_number BIGINT;

ALTER TABLE loan_applications
    ADD COLUMN IF NOT EXISTS loan_id VARCHAR(20);

-- Backfill application_number per SACCO, starting at 100001, ordered by creation.
WITH numbered AS (
    SELECT id,
           100000 + ROW_NUMBER() OVER (
               PARTITION BY sacco_id
               ORDER BY created_at, id
           ) AS new_number
    FROM loan_applications
)
UPDATE loan_applications la
SET application_number = n.new_number
FROM numbered n
WHERE la.id = n.id
  AND la.application_number IS NULL;

-- Per-SACCO counter table used by ApplicationNumberService to issue new numbers.
CREATE TABLE IF NOT EXISTS sacco_loan_app_counter (
    sacco_id    VARCHAR(64) PRIMARY KEY,
    last_number BIGINT      NOT NULL
);

-- Seed the counter from existing data so the next issued number continues the sequence.
INSERT INTO sacco_loan_app_counter (sacco_id, last_number)
SELECT sacco_id, COALESCE(MAX(application_number), 100000)
FROM loan_applications
GROUP BY sacco_id
ON CONFLICT (sacco_id) DO UPDATE
    SET last_number = GREATEST(sacco_loan_app_counter.last_number, EXCLUDED.last_number);

ALTER TABLE loan_applications
    ALTER COLUMN application_number SET NOT NULL;

-- Uniqueness: application_number is unique per SACCO; loan_id is unique per SACCO
-- when present (applications that never disburse keep it NULL).
CREATE UNIQUE INDEX IF NOT EXISTS ux_loan_applications_sacco_appnum
    ON loan_applications (sacco_id, application_number);

CREATE UNIQUE INDEX IF NOT EXISTS ux_loan_applications_sacco_loanid
    ON loan_applications (sacco_id, loan_id)
    WHERE loan_id IS NOT NULL;
