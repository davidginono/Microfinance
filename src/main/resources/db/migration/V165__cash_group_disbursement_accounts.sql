-- Cash-family posting accounts can fund loans without rewriting their purpose,
-- approved account versions or historical journals. Names do not grant eligibility.
CREATE FUNCTION gl_loan_disbursement_account(account_id uuid,institution varchar)
RETURNS boolean LANGUAGE sql STABLE AS $$
 SELECT EXISTS(SELECT 1 FROM gl_account a WHERE a.id=account_id AND a.sacco_id=institution
   AND a.active AND a.type='ASSET' AND a.kind='POSTING'
   AND (a.purpose IN('CASH','BANK','MOBILE_MONEY') OR a.purpose='OTHER' AND EXISTS(
     SELECT 1 FROM gl_cash_bank_group g WHERE g.id=a.parent_id AND g.sacco_id=a.sacco_id)))
$$;

DO $$
DECLARE definition text; previous text; replacement text;
BEGIN
 SELECT pg_get_functiondef('gl_validate_post()'::regprocedure) INTO definition;
 previous:=$old$m.kind='POSTING' AND m.type='ASSET' AND m.purpose IN ('CASH','BANK','MOBILE_MONEY')$old$;
 replacement:=$new$((x.kind='DISBURSEMENT' AND gl_loan_disbursement_account(m.id,x.sacco_id))
   OR (x.kind<>'DISBURSEMENT' AND m.kind='POSTING' AND m.type='ASSET' AND m.purpose IN ('CASH','BANK','MOBILE_MONEY')))$new$;
 IF strpos(definition,previous)=0 THEN RAISE EXCEPTION 'Expected loan GL account guard missing'; END IF;
 EXECUTE replace(definition,previous,replacement);

 SELECT pg_get_functiondef('loan_charge_validate(gl_journal)'::regprocedure) INTO definition;
 -- V164 is already deployed. Correct its ambiguous term alias forward, preserving its checksum.
 previous:=$old$FROM loan_charge_term t WHERE t.loan_id=p.loan_id AND$old$;
 replacement:=$new$FROM loan_charge_term charge_term WHERE charge_term.loan_id=p.loan_id AND$new$;
 IF strpos(definition,previous)=0 THEN RAISE EXCEPTION 'Expected V164 charge term alias missing'; END IF;
 definition:=replace(definition,previous,replacement);
 previous:=$old$(t.timing='DEDUCT' OR EXISTS(SELECT 1 FROM loan_charge_event e WHERE e.term_id=t.id$old$;
 replacement:=$new$(charge_term.timing='DEDUCT' OR EXISTS(SELECT 1 FROM loan_charge_event e WHERE e.term_id=charge_term.id$new$;
 IF strpos(definition,previous)=0 THEN RAISE EXCEPTION 'Expected V164 charge timing alias missing'; END IF;
 definition:=replace(definition,previous,replacement);
 previous:=$old$NOT EXISTS(SELECT 1 FROM gl_account WHERE id=p.money_account_id AND sacco_id=p.sacco_id AND type='ASSET' AND kind='POSTING' AND purpose IN('CASH','BANK','MOBILE_MONEY'))$old$;
 replacement:=$new$NOT (CASE WHEN p.kind='DISBURSEMENT' THEN gl_loan_disbursement_account(p.money_account_id,p.sacco_id)
   ELSE EXISTS(SELECT 1 FROM gl_account WHERE id=p.money_account_id AND sacco_id=p.sacco_id AND type='ASSET' AND kind='POSTING' AND purpose IN('CASH','BANK','MOBILE_MONEY')) END)$new$;
 IF strpos(definition,previous)=0 THEN RAISE EXCEPTION 'Expected charged loan account guard missing'; END IF;
 EXECUTE replace(definition,previous,replacement);

 SELECT pg_get_functiondef('loan_charge_lifecycle_guard()'::regprocedure) INTO definition;
 previous:=$old$JOIN loan_charge_term t ON t.id=e.term_id WHERE e.loan_id=NEW.id AND e.journal_id=journal$old$;
 replacement:=$new$JOIN loan_charge_term charge_term ON charge_term.id=e.term_id WHERE e.loan_id=NEW.id AND e.journal_id=journal$new$;
 IF strpos(definition,previous)=0 THEN RAISE EXCEPTION 'Expected V164 charge lifecycle alias missing'; END IF;
 definition:=replace(definition,previous,replacement);
 previous:=$old$AND e.kind='DEDUCT' AND t.timing='SEPARATE'$old$;
 replacement:=$new$AND e.kind='DEDUCT' AND charge_term.timing='SEPARATE'$new$;
 IF strpos(definition,previous)=0 THEN RAISE EXCEPTION 'Expected V164 charge lifecycle timing missing'; END IF;
 EXECUTE replace(definition,previous,replacement);

 SELECT pg_get_functiondef('loan_topup_validate(gl_journal)'::regprocedure) INTO definition;
 previous:=$old$NOT EXISTS(SELECT 1 FROM gl_account WHERE id=p.money_account_id AND kind='POSTING' AND type='ASSET' AND purpose IN ('CASH','BANK','MOBILE_MONEY'))$old$;
 replacement:=$new$NOT gl_loan_disbursement_account(p.money_account_id,p.sacco_id)$new$;
 IF strpos(definition,previous)=0 THEN RAISE EXCEPTION 'Expected top-up account guard missing'; END IF;
 EXECUTE replace(definition,previous,replacement);
END $$;
