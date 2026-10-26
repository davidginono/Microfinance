-- Preserve V109 and bind the executed instruction to the real net credit, never a second cash event.
CREATE FUNCTION lending_group_org_funding_cash_commit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF NOT EXISTS(SELECT 1 FROM lending_group_org_payout q
  JOIN accountant_loan_post t ON t.id=NEW.posting_id AND t.loan_id=NEW.loan_id
  JOIN gl_journal j ON j.id=NEW.journal_id
  JOIN members m ON m.id=NEW.actor_id
  WHERE q.cycle_id=NEW.cycle_id AND q.packet_hash=NEW.packet_hash
   AND j.source_reference=NEW.posting_id::text AND j.currency='TZS' AND j.effective_date=t.effective_date
   AND j.source_type IN ('LOAN_RECORD_DISBURSEMENT','LOAN_CHARGE_FUNDING')
   AND (m.position<>'MEMBER' OR EXISTS(SELECT 1 FROM member_staff_roles r WHERE r.member_id=m.id))
   AND q.net_amount=(SELECT sum(z.credit-z.debit) FROM gl_journal_line z WHERE z.journal_id=j.id AND z.account_id=t.money_account_id)
   AND q.principal=(SELECT sum(z.debit-z.credit) FROM gl_journal_line z WHERE z.journal_id=j.id AND z.account_id=t.principal_account_id))
 THEN RAISE EXCEPTION 'Organization funding must conserve the authorized principal and net payout'; END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER group_org_funding_cash_commit AFTER INSERT ON lending_group_org_funding DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION lending_group_org_funding_cash_commit();
