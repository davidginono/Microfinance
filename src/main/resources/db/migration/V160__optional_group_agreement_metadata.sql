-- Staff retain the actual signed PDF without entering a separate reference or language.
-- Existing agreement metadata remains immutable historical evidence.
ALTER TABLE staff_group_loan ALTER COLUMN reference DROP NOT NULL;
ALTER TABLE staff_group_loan ALTER COLUMN language DROP NOT NULL;
