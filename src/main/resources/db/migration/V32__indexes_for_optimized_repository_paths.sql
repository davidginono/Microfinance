CREATE INDEX IF NOT EXISTS ix_members_sacco_status_role_name
    ON members (sacco_id, status, position, full_name);

CREATE INDEX IF NOT EXISTS ix_member_staff_roles_role_member
    ON member_staff_roles (role_name, member_id);

CREATE INDEX IF NOT EXISTS ix_accounts_savings_member
    ON accounts_savings (member_id);

CREATE INDEX IF NOT EXISTS ix_notifications_recipient_type_read_created
    ON notifications (recipient_member_id, type, read_at, created_at DESC);

CREATE INDEX IF NOT EXISTS ix_admin_incidents_created
    ON admin_incidents (created_at DESC);

CREATE INDEX IF NOT EXISTS ix_admin_incidents_sacco_status_severity_created
    ON admin_incidents (sacco_id, status, severity, created_at DESC);

CREATE INDEX IF NOT EXISTS ix_admin_incidents_reporter_created
    ON admin_incidents (reported_by_member_id, created_at DESC);

CREATE INDEX IF NOT EXISTS ix_manager_reviews_member_stage_created
    ON manager_reviews (manager_member_id, review_stage, created_at DESC);
