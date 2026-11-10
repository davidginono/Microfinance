-- New mandates omit these references. Keep the values on earlier immutable revisions.
ALTER TABLE lending_group_authority
 ALTER COLUMN capacity_evidence DROP NOT NULL,
 ALTER COLUMN resolution_evidence DROP NOT NULL,
 ALTER COLUMN signature_evidence DROP NOT NULL;
