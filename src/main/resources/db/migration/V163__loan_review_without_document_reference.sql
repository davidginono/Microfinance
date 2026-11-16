-- New staff decisions need a reason, without a separate document reference.
-- Retain the legacy column and immutable rows for historical review evidence.
ALTER TABLE loan_group_decision DROP CONSTRAINT loan_group_decision_evidence_check;
