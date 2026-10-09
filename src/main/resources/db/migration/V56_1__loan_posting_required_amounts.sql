-- A posted source must contain complete amounts; SQL CHECK does not reject NULL comparisons.
ALTER TABLE accountant_loan_post ADD CONSTRAINT accountant_post_complete_amounts CHECK
 (state <> 'POSTED' OR (principal_amount IS NOT NULL AND interest_amount IS NOT NULL AND principal_balance IS NOT NULL));
