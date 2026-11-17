-- User-authorized admin defaults: grant only charge setup and account metadata update.
-- Preserve custom permissions and their storage source; a later explicit revocation still wins.
CREATE TEMP TABLE v166_admin_charge_grants ON COMMIT DROP AS
WITH admins AS (
    SELECT m.id,m.sacco_id,m.station_id,
        CASE WHEN EXISTS(SELECT 1 FROM member_access_claims c WHERE c.member_id=m.id) THEN 'MATRIX'
             WHEN jsonb_typeof(s.notification_prefs->'claims')='array' THEN 'JSON'
             ELSE 'DEFAULT' END AS storage,
        CASE WHEN EXISTS(SELECT 1 FROM member_access_claims c WHERE c.member_id=m.id)
             THEN (SELECT jsonb_agg(c.claim_name ORDER BY c.claim_name) FROM member_access_claims c WHERE c.member_id=m.id)
             ELSE s.notification_prefs->'claims' END AS before_claims
    FROM members m LEFT JOIN user_settings s ON s.member_id=m.id
    WHERE m.status='ACTIVE'
      AND (m.staff_access_status='ACTIVE' OR (m.staff_access_status='NONE'
           AND coalesce(m.staff_no,'')='' AND m.staff_access_assigned_at IS NULL AND m.staff_access_activated_at IS NULL))
      AND (m.position IN ('ADMIN','MINOR_ADMIN','ICT_MANAGER') OR EXISTS(
          SELECT 1 FROM member_staff_roles r WHERE r.member_id=m.id AND r.role_name IN ('ADMIN','MINOR_ADMIN','ICT_MANAGER')))
), changes AS (
    SELECT a.*,(SELECT jsonb_agg(required.claim_name ORDER BY required.claim_name)
        FROM (VALUES ('LOAN_PRODUCTS_CONFIGURE'),('ACCOUNTING_ACCOUNTS_UPDATE')) required(claim_name)
        WHERE NOT a.before_claims @> jsonb_build_array(required.claim_name)) AS added_claims
    FROM admins a WHERE a.storage<>'DEFAULT'
)
SELECT * FROM changes WHERE added_claims IS NOT NULL;

INSERT INTO member_access_claims(member_id,claim_name)
SELECT g.id,c.claim_name FROM v166_admin_charge_grants g
CROSS JOIN LATERAL jsonb_array_elements_text(g.added_claims) c(claim_name)
WHERE g.storage='MATRIX'
ON CONFLICT DO NOTHING;

UPDATE user_settings s SET notification_prefs=jsonb_set(s.notification_prefs,'{claims}',g.before_claims||g.added_claims),updated_at=now()
FROM v166_admin_charge_grants g WHERE s.member_id=g.id AND g.storage='JSON';

INSERT INTO audit_log(id,entity_type,entity_id,action,actor_member_id,event_status,sacco_id,station_id,
    action_description,reference_type,reference_value,before_state,after_state,request_metadata,created_at)
SELECT gen_random_uuid(),'MEMBER',g.id,'SYSTEM_ADMIN_CHARGE_DEFAULTS',NULL,'SUCCESS',g.sacco_id,g.station_id,
    'User-authorized default charge-account permissions for administrators','MIGRATION','V166',
    jsonb_build_object('claims',g.before_claims,'storage',g.storage),
    jsonb_build_object('claims',g.before_claims||g.added_claims,'addedClaims',g.added_claims,'storage',g.storage),
    jsonb_build_object('source','V166__admin_charge_account_permissions.sql'),now()
FROM v166_admin_charge_grants g;

DROP TABLE v166_admin_charge_grants;
