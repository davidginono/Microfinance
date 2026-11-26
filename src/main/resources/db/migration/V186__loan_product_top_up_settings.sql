ALTER TABLE loan_product_settings
    ADD COLUMN top_up_allowed boolean NOT NULL DEFAULT true,
    ADD COLUMN top_up_fees_enabled boolean NOT NULL DEFAULT false,
    ADD COLUMN top_up_application_fee boolean NOT NULL DEFAULT false,
    ADD COLUMN top_up_insurance_fee boolean NOT NULL DEFAULT false,
    ADD COLUMN top_up_processing_fee boolean NOT NULL DEFAULT false,
    ADD COLUMN top_up_percentage_basis varchar(24),
    ADD CONSTRAINT loan_product_top_up_fee_settings CHECK (
        (top_up_percentage_basis IS NULL OR top_up_percentage_basis IN ('ADDITIONAL_AMOUNT', 'COMBINED_NEW_PRINCIPAL'))
        AND (NOT top_up_fees_enabled OR top_up_allowed)
        AND (top_up_fees_enabled = (top_up_application_fee OR top_up_insurance_fee OR top_up_processing_fee))
        AND ((top_up_percentage_basis IS NOT NULL) = (top_up_insurance_fee OR top_up_processing_fee))
    );

ALTER TABLE loan_charge_term DROP CONSTRAINT loan_charge_term_basis_check;
ALTER TABLE loan_charge_term ADD CONSTRAINT loan_charge_term_basis_check
    CHECK (basis IN ('FIXED', 'PRINCIPAL_RATE', 'CUSTOM_AMOUNT', 'TOPUP_ADDITIONAL_RATE', 'TOPUP_COMBINED_RATE'));
