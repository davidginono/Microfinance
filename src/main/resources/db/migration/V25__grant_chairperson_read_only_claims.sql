INSERT INTO member_access_claims (member_id, claim_name)
SELECT member_id, 'PROCESSED_LOANS_VIEW'
FROM member_access_claims
WHERE claim_name = 'CHAIRPERSON_QUEUE_VIEW'
ON CONFLICT (member_id, claim_name) DO NOTHING;

INSERT INTO member_access_claims (member_id, claim_name)
SELECT member_id, 'SACCO_CONFIGURATIONS_VIEW'
FROM member_access_claims
WHERE claim_name = 'CHAIRPERSON_QUEUE_VIEW'
ON CONFLICT (member_id, claim_name) DO NOTHING;
