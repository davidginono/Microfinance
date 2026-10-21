-- Rename only the original institution; keep compatibility IDs and historical snapshots.
UPDATE registered_saccos
SET sacco_name = 'Degra Microfinance', updated_at = CURRENT_TIMESTAMP
WHERE sacco_id = 'TAHA-SACCOS' AND sacco_name = 'TAHA SACCOS';
