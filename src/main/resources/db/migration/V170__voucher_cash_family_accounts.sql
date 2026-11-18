-- Use the existing cash-family classification without changing account purposes or versions.
DO $$
DECLARE definition text; previous text;
BEGIN
 SELECT pg_get_functiondef('validate_core_voucher_post(gl_journal)'::regprocedure) INTO definition;
 previous:=$old$(a.type='ASSET' AND a.purpose IN ('CASH','BANK','MOBILE_MONEY'))$old$;
 IF strpos(definition,previous)=0 THEN RAISE EXCEPTION 'Voucher money guard not found'; END IF;
 EXECUTE replace(definition,previous,'gl_loan_disbursement_account(a.id,a.sacco_id)');
END $$;
