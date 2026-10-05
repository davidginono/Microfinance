-- Preserve applied V43; select the trigger record field only for its actual table.
CREATE OR REPLACE FUNCTION accounting_journal_balance_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE jid uuid; j accounting_journal;
BEGIN
 IF TG_TABLE_NAME='accounting_journal' THEN
  jid:=NEW.id;
 ELSE
  jid:=NEW.journal_id;
 END IF;
 SELECT * INTO STRICT j FROM accounting_journal WHERE id=jid;
 IF j.state<>'DRAFT' AND (SELECT count(*)<2 OR count(*)>200 OR sum(debit)<>sum(credit) FROM accounting_journal_line WHERE journal_id=jid)
  THEN RAISE EXCEPTION 'Approved and posted journals require 2-200 balanced lines'; END IF;
 IF j.source_kind='OPENING' AND j.state<>'DRAFT' AND NOT EXISTS(SELECT 1 FROM accounting_opening_batch b WHERE b.journal_id=j.id AND b.sacco_id=j.sacco_id AND b.station_id=j.station_id AND b.state IN ('APPROVED','POSTED') AND b.reviewed_by=j.approved_by)
  THEN RAISE EXCEPTION 'Opening requires independently reviewed import'; END IF;
 RETURN NEW;
END $$;
