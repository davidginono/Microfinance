-- Extend the existing frozen-term guard with the replacement product's selected top-up fees.
DO $$
DECLARE definition text; updated text;
BEGIN
    SELECT pg_get_functiondef('loan_charge_terms_guard()'::regprocedure) INTO definition;
    updated := replace(definition,
        $previous$OR (a.top_up_source_loan_id IS NOT NULL AND NEW.kind IN('APPLICATION','PROCESSING'))$previous$,
        $current$OR (a.top_up_source_loan_id IS NULL AND NEW.basis IN ('TOPUP_ADDITIONAL_RATE','TOPUP_COMBINED_RATE'))
 OR (a.top_up_source_loan_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM loan_product_settings product
     WHERE product.id=a.loan_product_setting_id AND product.sacco_id=a.sacco_id
       AND NOT product.custom_recording_configuration AND product.top_up_allowed AND product.top_up_fees_enabled
       AND CASE NEW.kind
           WHEN 'APPLICATION' THEN product.top_up_application_fee AND NEW.basis='FIXED'
           WHEN 'PROCESSING' THEN product.top_up_processing_fee
           WHEN 'INSURANCE' THEN product.top_up_insurance_fee
           ELSE false
       END
       AND (NEW.kind='APPLICATION' OR NEW.basis=CASE product.top_up_percentage_basis
           WHEN 'ADDITIONAL_AMOUNT' THEN 'TOPUP_ADDITIONAL_RATE'
           WHEN 'COMBINED_NEW_PRINCIPAL' THEN 'TOPUP_COMBINED_RATE'
       END)))$current$);
    IF updated=definition THEN RAISE EXCEPTION 'Expected frozen top-up fee guard'; END IF;
    EXECUTE updated;
END $$;
