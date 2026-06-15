create index if not exists ix_loan_applications_sacco_station_status_created
    on loan_applications (sacco_id, station_id, status, created_at desc);

create index if not exists ix_loan_applications_sacco_station_updated
    on loan_applications (sacco_id, station_id, updated_at desc);

create index if not exists ix_loan_applications_applicant_status_created
    on loan_applications (applicant_member_id, status, created_at desc);

create index if not exists ix_members_sacco_station_status_name
    on members (sacco_id, station_id, status, full_name);

create index if not exists ix_members_sacco_status_member_no
    on members (sacco_id, status, member_no);

create index if not exists ix_members_member_no_digits
    on members ((regexp_replace(coalesce(member_no, ''), '[^0-9]', '', 'g')));

create index if not exists ix_guarantor_requests_member_status_created
    on guarantor_requests (guarantor_member_id, status, created_at desc);

create index if not exists ix_guarantor_requests_loan_status
    on guarantor_requests (loan_application_id, status);

create index if not exists ix_manager_reviews_member_stage_created
    on manager_reviews (manager_member_id, review_stage, created_at desc);

create index if not exists ix_manager_reviews_member_stage_loan_created
    on manager_reviews (manager_member_id, review_stage, loan_application_id, created_at desc);

create index if not exists ix_board_reviews_member_stage_created
    on board_reviews (board_member_id, review_stage, created_at desc);

create index if not exists ix_notifications_recipient_read_created
    on notifications (recipient_member_id, read_at, created_at desc);

create index if not exists ix_notifications_recipient_type_read_created
    on notifications (recipient_member_id, type, read_at, created_at desc);

create index if not exists ix_notifications_support_incident_read
    on notifications (((payload -> 'details' ->> 'incidentId')), read_at)
    where type = 'SUPPORT_MESSAGE';

create index if not exists ix_admin_incidents_sacco_created
    on admin_incidents (sacco_id, created_at desc);

create index if not exists ix_admin_incidents_reporter_created
    on admin_incidents (reported_by_member_id, created_at desc);

create index if not exists ix_outbox_events_status_created
    on outbox_events (status, created_at);
