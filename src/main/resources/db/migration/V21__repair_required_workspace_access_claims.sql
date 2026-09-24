-- Repair only the essential claims needed to enter an assigned staff workspace
-- and to recover administrator access. Existing custom permissions are preserved.

WITH role_required_claims(role_name, claim_name) AS (
    VALUES
        ('LOAN_OFFICER', 'LOAN_OFFICER_QUEUE_VIEW'),
        ('MANAGER', 'MANAGER_QUEUE_VIEW'),
        ('ACCOUNTANT', 'ACCOUNTANT_QUEUE_VIEW'),
        ('DISBURSEMENT_OFFICER', 'DISBURSEMENT_QUEUE_VIEW'),
        ('CHAIRPERSON', 'CHAIRPERSON_QUEUE_VIEW'),
        ('BOARD', 'BOARD_QUEUE_VIEW'),
        ('CREDIT_COMMITTEE', 'CREDIT_COMMITTEE_QUEUE_VIEW'),
        ('MINOR_ADMIN', 'ADMIN_DASHBOARD_VIEW'),
        ('MINOR_ADMIN', 'ACCESS_MATRIX_VIEW'),
        ('MINOR_ADMIN', 'ACCESS_MATRIX_UPDATE'),
        ('MINOR_ADMIN', 'USER_ACCESS_VIEW'),
        ('MINOR_ADMIN', 'USER_ACCESS_UPDATE'),
        ('ADMIN', 'ADMIN_DASHBOARD_VIEW'),
        ('ADMIN', 'ACCESS_MATRIX_VIEW'),
        ('ADMIN', 'ACCESS_MATRIX_UPDATE'),
        ('ADMIN', 'USER_ACCESS_VIEW'),
        ('ADMIN', 'USER_ACCESS_UPDATE')
),
member_roles AS (
    SELECT m.id AS member_id, m.position AS role_name
    FROM public.members m
    WHERE m.position IS NOT NULL

    UNION

    SELECT msr.member_id, msr.role_name
    FROM public.member_staff_roles msr
),
required_claims AS (
    SELECT DISTINCT mr.member_id, rrc.claim_name
    FROM member_roles mr
    JOIN role_required_claims rrc ON rrc.role_name = mr.role_name
)
INSERT INTO public.member_access_claims (member_id, claim_name)
SELECT member_id, claim_name
FROM required_claims
ON CONFLICT DO NOTHING;
