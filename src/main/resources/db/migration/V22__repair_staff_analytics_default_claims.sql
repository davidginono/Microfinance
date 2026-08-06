-- Restore the analytics claims that are part of the default review-workspace
-- role contract but were skipped for legacy staff who already had other claims.

WITH role_analytics_claims(role_name, claim_name) AS (
    VALUES
        ('LOAN_OFFICER', 'STAFF_ANALYTICS_VIEW'),
        ('LOAN_OFFICER', 'STAFF_ANALYTICS_EXPORT'),
        ('MANAGER', 'STAFF_ANALYTICS_VIEW'),
        ('MANAGER', 'STAFF_ANALYTICS_EXPORT'),
        ('ACCOUNTANT', 'STAFF_ANALYTICS_VIEW'),
        ('ACCOUNTANT', 'STAFF_ANALYTICS_EXPORT'),
        ('DISBURSEMENT_OFFICER', 'STAFF_ANALYTICS_VIEW'),
        ('CHAIRPERSON', 'STAFF_ANALYTICS_VIEW'),
        ('CHAIRPERSON', 'STAFF_ANALYTICS_EXPORT'),
        ('BOARD', 'STAFF_ANALYTICS_VIEW'),
        ('BOARD', 'STAFF_ANALYTICS_EXPORT'),
        ('CREDIT_COMMITTEE', 'STAFF_ANALYTICS_VIEW'),
        ('CREDIT_COMMITTEE', 'STAFF_ANALYTICS_EXPORT')
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
    SELECT DISTINCT mr.member_id, rac.claim_name
    FROM member_roles mr
    JOIN role_analytics_claims rac ON rac.role_name = mr.role_name
)
INSERT INTO public.member_access_claims (member_id, claim_name)
SELECT member_id, claim_name
FROM required_claims
ON CONFLICT DO NOTHING;
