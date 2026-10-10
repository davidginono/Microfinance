-- Preserve the originally applied V55 checksum. V56 already carries the hardened
-- voucher validation and the loan-aware commit function; install its missing triggers.
ALTER TABLE accounting_voucher_transaction ALTER COLUMN transaction_name TYPE varchar(240);

CREATE CONSTRAINT TRIGGER core_voucher_commit AFTER INSERT ON accounting_voucher
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION core_post_commit_guard();
CREATE CONSTRAINT TRIGGER core_journal_commit AFTER INSERT ON gl_journal
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW WHEN(NEW.direct_post)
 EXECUTE FUNCTION core_post_commit_guard();
