-- Loan archive starts with the reviewer's immutable decision evidence.
CREATE INDEX ix_loan_group_decision_actor_request ON loan_group_decision(actor_id,request_id);

-- Keep the client identity and every historical reference, and retire competing staff access.
CREATE TEMPORARY TABLE dual_staff_client_cutover ON COMMIT DROP AS
SELECT m.id,m.sacco_id,m.station_id,m.position,m.status,m.staff_access_status,
       m.status='ACTIVE' AND (m.staff_access_status='ACTIVE' OR (m.staff_access_status='NONE'
           AND coalesce(m.staff_no,'')='' AND m.staff_access_assigned_at IS NULL AND m.staff_access_activated_at IS NULL)) was_active_staff,
       coalesce((SELECT jsonb_agg(r.role_name ORDER BY r.role_name) FROM member_staff_roles r WHERE r.member_id=m.id),'[]') roles,
       coalesce((SELECT jsonb_agg(c.claim_name ORDER BY c.claim_name) FROM member_access_claims c WHERE c.member_id=m.id),'[]') claims
FROM members m
WHERE (m.is_member IS TRUE OR (m.is_member IS NULL AND m.position='MEMBER'))
  AND (m.position IS DISTINCT FROM 'MEMBER' OR m.staff_access_status<>'NONE'
       OR EXISTS(SELECT 1 FROM member_staff_roles r WHERE r.member_id=m.id AND r.role_name<>'MEMBER'));

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM dual_staff_client_cutover old
        WHERE old.was_active_staff
          AND (old.position IN ('MINOR_ADMIN','ICT_MANAGER') OR old.roles ?| ARRAY['MINOR_ADMIN','ICT_MANAGER'])
          AND EXISTS(SELECT 1 FROM sacco_stations b WHERE b.sacco_id=old.sacco_id AND b.station_id=old.station_id AND b.active)
          AND NOT EXISTS (
              SELECT 1 FROM members m
              WHERE m.sacco_id=old.sacco_id AND m.station_id=old.station_id AND m.status='ACTIVE'
                AND (m.staff_access_status='ACTIVE' OR (m.staff_access_status='NONE'
                    AND coalesce(m.staff_no,'')='' AND m.staff_access_assigned_at IS NULL AND m.staff_access_activated_at IS NULL))
                AND NOT EXISTS(SELECT 1 FROM dual_staff_client_cutover retiring WHERE retiring.id=m.id)
                AND (m.position IN ('MINOR_ADMIN','ICT_MANAGER') OR EXISTS (
                    SELECT 1 FROM member_staff_roles r WHERE r.member_id=m.id AND r.role_name IN ('MINOR_ADMIN','ICT_MANAGER')))
          )
    ) THEN RAISE EXCEPTION 'Appoint an active staff-only branch admin before retiring dual staff/client access'; END IF;
    IF EXISTS (
        SELECT 1 FROM dual_staff_client_cutover old WHERE old.was_active_staff
          AND (old.position='ADMIN' OR old.roles ? 'ADMIN')
    ) AND NOT EXISTS (
        SELECT 1 FROM members m WHERE m.status='ACTIVE' AND (m.position='ADMIN'
            OR EXISTS(SELECT 1 FROM member_staff_roles r WHERE r.member_id=m.id AND r.role_name='ADMIN'))
          AND NOT EXISTS(SELECT 1 FROM dual_staff_client_cutover old WHERE old.id=m.id)
    ) THEN RAISE EXCEPTION 'Appoint an active staff-only platform admin before retiring dual staff/client access'; END IF;
END $$;

INSERT INTO audit_log(id,entity_type,entity_id,action,action_description,event_status,created_at,sacco_id,station_id,before_state,after_state)
SELECT gen_random_uuid(),'STAFF_USER',id,'STAFF_CLIENT_ACCESS_RETIRED','Retired staff access on a client account',
       'SUCCESS',now(),sacco_id,station_id,
       jsonb_build_object('position',position,'status',status,'staffAccess',staff_access_status,'roles',roles,'claims',claims),
       jsonb_build_object('position','MEMBER','staffAccess','NONE')
FROM dual_staff_client_cutover;

UPDATE minor_admin_invitations i SET revoked_at=now()
WHERE i.claimed_at IS NULL AND i.revoked_at IS NULL AND EXISTS(SELECT 1 FROM dual_staff_client_cutover old WHERE old.id=i.member_id);
DELETE FROM member_access_claims c USING dual_staff_client_cutover old WHERE c.member_id=old.id;
DELETE FROM member_staff_roles r USING dual_staff_client_cutover old WHERE r.member_id=old.id;
UPDATE user_settings u SET notification_prefs=(u.notification_prefs-'claims')
FROM dual_staff_client_cutover old WHERE u.member_id=old.id;
UPDATE members m
SET position='MEMBER',staff_access_status='NONE',password_hash='!NO_PORTAL_ACCESS'
FROM dual_staff_client_cutover old WHERE m.id=old.id;

ALTER TABLE members ADD CONSTRAINT client_has_no_staff_identity CHECK (
    NOT (is_member IS TRUE OR (is_member IS NULL AND position='MEMBER'))
    OR (position IS NOT DISTINCT FROM 'MEMBER' AND staff_access_status='NONE')
);

CREATE FUNCTION reject_staff_role_for_client() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.role_name<>'MEMBER' THEN
        PERFORM 1 FROM members WHERE id=NEW.member_id FOR UPDATE;
        IF EXISTS (
            SELECT 1 FROM members m WHERE m.id=NEW.member_id
              AND (m.is_member IS TRUE OR (m.is_member IS NULL AND m.position='MEMBER'))
        ) THEN RAISE EXCEPTION 'Client accounts cannot hold staff roles'; END IF;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER client_has_no_staff_roles BEFORE INSERT OR UPDATE ON member_staff_roles
    FOR EACH ROW EXECUTE FUNCTION reject_staff_role_for_client();

CREATE FUNCTION reject_client_with_staff_roles() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF (NEW.is_member IS TRUE OR (NEW.is_member IS NULL AND NEW.position='MEMBER')) AND EXISTS (
        SELECT 1 FROM member_staff_roles r WHERE r.member_id=NEW.id AND r.role_name<>'MEMBER'
    ) THEN RAISE EXCEPTION 'Staff accounts cannot become client accounts'; END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER staff_has_no_client_status BEFORE UPDATE OF is_member,position ON members
    FOR EACH ROW EXECUTE FUNCTION reject_client_with_staff_roles();
