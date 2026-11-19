-- Use the same scoped posting-account rule for cash-family funding and collections.
DO $$
DECLARE guard record; definition text; previous text; remainder text;
BEGIN
 FOR guard IN SELECT * FROM (VALUES
  ('gl_validate_post()',
   $old$((x.kind='DISBURSEMENT' AND gl_loan_disbursement_account(m.id,x.sacco_id))$old$,
   $old$OR (x.kind<>'DISBURSEMENT' AND m.kind='POSTING' AND m.type='ASSET' AND m.purpose IN ('CASH','BANK','MOBILE_MONEY')))$old$,
   'gl_loan_disbursement_account(m.id,x.sacco_id)'),
  ('loan_charge_validate(gl_journal)',
   $old$(CASE WHEN p.kind='DISBURSEMENT' THEN gl_loan_disbursement_account(p.money_account_id,p.sacco_id)$old$,
   $old$ELSE EXISTS(SELECT 1 FROM gl_account WHERE id=p.money_account_id AND sacco_id=p.sacco_id AND type='ASSET' AND kind='POSTING' AND purpose IN('CASH','BANK','MOBILE_MONEY')) END)$old$,
   'gl_loan_disbursement_account(p.money_account_id,p.sacco_id)')
 ) AS rules(signature,head,tail,replacement)
 LOOP
  SELECT pg_get_functiondef(guard.signature::regprocedure) INTO definition;
  remainder:=substr(definition,strpos(definition,guard.head)+length(guard.head));
  -- Stored function bodies can retain the line endings and indentation of the original deployment.
  previous:=guard.head||substring(remainder from '^[[:space:]]+')||guard.tail;
  IF previous IS NULL OR strpos(definition,previous)=0 THEN
   RAISE EXCEPTION 'Expected loan account guard missing in %',guard.signature;
  END IF;
  EXECUTE replace(definition,previous,guard.replacement);
 END LOOP;
END $$;

ALTER TABLE loan_repayment_transactions DROP CONSTRAINT loan_movement_channel;
ALTER TABLE loan_repayment_transactions ADD CONSTRAINT loan_movement_channel
 CHECK (channel IN ('CASH','BANK','MOBILE_MONEY','INTERNAL','CASH_EQUIVALENTS'));

-- Existing drafts without a source reference use the same stable reference as new drafts.
UPDATE receipt_batch_item
SET form_json=jsonb_set(form_json,'{reference}',to_jsonb('BATCH/'||id::text))
WHERE state<>'POSTED' AND nullif(btrim(form_json->>'reference'),'') IS NULL;
