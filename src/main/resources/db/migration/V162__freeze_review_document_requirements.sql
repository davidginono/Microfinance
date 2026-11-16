-- The existing immutable review request retains the document rules checked at submission.
-- Empty historical snapshots keep their original guarantor-consent gate; no pending review is repriced.
ALTER TABLE loan_group_request ADD COLUMN document_policy jsonb NOT NULL DEFAULT '{}'::jsonb;
ALTER TABLE loan_group_request ADD CONSTRAINT loan_review_document_policy_shape CHECK (
 document_policy='{}'::jsonb OR (
  jsonb_typeof(document_policy->'required')='boolean'
  AND jsonb_typeof(document_policy->'guarantorConsent')='boolean'
  AND jsonb_typeof(document_policy->'documents')='array'
  AND jsonb_array_length(document_policy->'documents')<=10
  AND ((document_policy->>'required')::boolean OR document_policy->'documents'='[]'::jsonb)
 ));
