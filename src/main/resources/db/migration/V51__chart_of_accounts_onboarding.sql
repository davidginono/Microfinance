-- Account metadata only: no amounts, policy decisions, or account balances are seeded.
ALTER TABLE gl_account ADD COLUMN name_sw varchar(160);
ALTER TABLE gl_account ADD COLUMN description varchar(500);
CREATE INDEX ix_gl_account_active_parent ON gl_account(sacco_id,parent_id,code) WHERE active;
CREATE INDEX ix_gl_account_active_heading ON gl_account(sacco_id,code) WHERE active AND kind='HEADING';

-- User-authorized accountant COA access. Preserve all existing claims; subsequent revocation remains effective.
-- Members with no materialized claims inherit the Java role bundle; do not replace that bundle with three rows.
INSERT INTO member_access_claims(member_id,claim_name)
SELECT m.id,c.claim_name FROM members m
CROSS JOIN (VALUES ('ACCOUNTING_ACCOUNTS_VIEW'),('ACCOUNTING_ACCOUNTS_CREATE'),('ACCOUNTING_ACCOUNTS_UPDATE')) c(claim_name)
WHERE m.status='ACTIVE' AND m.sacco_id IS NOT NULL AND m.station_id IS NOT NULL
 AND (m.staff_access_status='ACTIVE' OR (m.staff_access_status='NONE'
      AND coalesce(m.staff_no,'')='' AND m.staff_access_assigned_at IS NULL AND m.staff_access_activated_at IS NULL))
 AND (m.position='ACCOUNTANT' OR EXISTS(SELECT 1 FROM member_staff_roles r WHERE r.member_id=m.id AND r.role_name='ACCOUNTANT'))
 AND m.position IS DISTINCT FROM 'ADMIN' AND NOT EXISTS(SELECT 1 FROM member_staff_roles r WHERE r.member_id=m.id AND r.role_name='ADMIN')
 AND EXISTS(SELECT 1 FROM member_access_claims old WHERE old.member_id=m.id)
ON CONFLICT DO NOTHING;
