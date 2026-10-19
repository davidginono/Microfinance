-- Journals have no payer/payee field. Preserve all existing voucher history.
ALTER TABLE accounting_voucher DROP CONSTRAINT accounting_voucher_party_check;
ALTER TABLE accounting_voucher ADD CONSTRAINT accounting_voucher_party_check
 CHECK (type='JOURNAL' OR length(trim(party))>0);
