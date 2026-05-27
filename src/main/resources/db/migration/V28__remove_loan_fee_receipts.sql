ALTER TABLE sacco_settings
    DROP COLUMN IF EXISTS loan_fee_payment_method,
    DROP COLUMN IF EXISTS loan_fee_payment_account,
    DROP COLUMN IF EXISTS loan_fee_payment_payee,
    DROP COLUMN IF EXISTS loan_fee_payment_instructions;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'loan_applications'
          AND column_name = 'attachments_json'
    ) THEN
        UPDATE loan_applications la
        SET attachments_json = COALESCE((
            SELECT jsonb_agg(item ORDER BY ordinality)
            FROM jsonb_array_elements(la.attachments_json) WITH ORDINALITY AS attachment(item, ordinality)
            WHERE item->>'attachmentCategory' IS DISTINCT FROM 'FEE_INSURANCE_RECEIPT'
        ), '[]'::jsonb)
        WHERE la.attachments_json IS NOT NULL
          AND jsonb_typeof(la.attachments_json) = 'array'
          AND EXISTS (
              SELECT 1
              FROM jsonb_array_elements(la.attachments_json) AS attachment(item)
              WHERE item->>'attachmentCategory' = 'FEE_INSURANCE_RECEIPT'
          );
    END IF;
END $$;
