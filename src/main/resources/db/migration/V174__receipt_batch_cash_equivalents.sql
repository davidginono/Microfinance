-- Use the same scoped posting-account rule for cash-family funding and collections.
DO $$
DECLARE definition text; previous text;
BEGIN
 SELECT pg_get_functiondef('gl_validate_post()'::regprocedure) INTO definition;
 previous:=$old$((x.kind='DISBURSEMENT' AND gl_loan_disbursement_account(m.id,x.sacco_id))
   OR (x.kind<>'DISBURSEMENT' AND m.kind='POSTING' AND m.type='ASSET' AND m.purpose IN ('CASH','BANK','MOBILE_MONEY')))$old$;
 IF strpos(definition,previous)=0 THEN RAISE EXCEPTION 'Expected loan GL account guard missing'; END IF;
 EXECUTE replace(definition,previous,'gl_loan_disbursement_account(m.id,x.sacco_id)');

 SELECT pg_get_functiondef('loan_charge_validate(gl_journal)'::regprocedure) INTO definition;
 previous:=$old$(CASE WHEN p.kind='DISBURSEMENT' THEN gl_loan_disbursement_account(p.money_account_id,p.sacco_id)
   ELSE EXISTS(SELECT 1 FROM gl_account WHERE id=p.money_account_id AND sacco_id=p.sacco_id AND type='ASSET' AND kind='POSTING' AND purpose IN('CASH','BANK','MOBILE_MONEY')) END)$old$;
 IF strpos(definition,previous)=0 THEN RAISE EXCEPTION 'Expected charged loan account guard missing'; END IF;
 EXECUTE replace(definition,previous,'gl_loan_disbursement_account(p.money_account_id,p.sacco_id)');
END $$;

ALTER TABLE loan_repayment_transactions DROP CONSTRAINT loan_movement_channel;
ALTER TABLE loan_repayment_transactions ADD CONSTRAINT loan_movement_channel
 CHECK (channel IN ('CASH','BANK','MOBILE_MONEY','INTERNAL','CASH_EQUIVALENTS'));

-- Existing drafts without a source reference use the same stable reference as new drafts.
UPDATE receipt_batch_item
SET form_json=jsonb_set(form_json,'{reference}',to_jsonb('BATCH/'||id::text))
WHERE state<>'POSTED' AND nullif(btrim(form_json->>'reference'),'') IS NULL;
