CREATE INDEX IF NOT EXISTS ix_loan_applications_sacco_station_status_created
    ON loan_applications (sacco_id, station_id, status, created_at);

CREATE INDEX IF NOT EXISTS ix_loan_applications_applicant_status_created
    ON loan_applications (applicant_member_id, status, created_at DESC);

CREATE INDEX IF NOT EXISTS ix_loan_applications_sacco_station_updated
    ON loan_applications (sacco_id, station_id, updated_at DESC);

CREATE INDEX IF NOT EXISTS ix_guarantor_requests_member_status_created
    ON guarantor_requests (guarantor_member_id, status, created_at DESC);

CREATE INDEX IF NOT EXISTS ix_guarantor_requests_loan_status
    ON guarantor_requests (loan_application_id, status);

CREATE INDEX IF NOT EXISTS ix_board_reviews_member_stage_decision_created
    ON board_reviews (board_member_id, review_stage, decision, created_at DESC);

CREATE INDEX IF NOT EXISTS ix_notifications_recipient_read_created
    ON notifications (recipient_member_id, read_at, created_at DESC);

CREATE INDEX IF NOT EXISTS ix_notifications_recipient_type_created
    ON notifications (recipient_member_id, type, created_at DESC);

CREATE INDEX IF NOT EXISTS ix_outbox_events_status_created
    ON outbox_events (status, created_at);

CREATE INDEX IF NOT EXISTS ix_members_sacco_station_status_name
    ON members (sacco_id, station_id, status, full_name);

CREATE INDEX IF NOT EXISTS ix_admin_incidents_sacco_created
    ON admin_incidents (sacco_id, created_at DESC);
