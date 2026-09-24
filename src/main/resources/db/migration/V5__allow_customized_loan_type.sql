ALTER TABLE public.loan_product_settings
    DROP CONSTRAINT IF EXISTS loan_product_settings_loan_type_check;

ALTER TABLE public.loan_product_settings
    ADD CONSTRAINT loan_product_settings_loan_type_check
        CHECK (loan_type::text = ANY (ARRAY[
            'LOAN_ADVANCE',
            'EDUCATION_LOAN',
            'EMERGENCY_LOAN',
            'DEVELOPMENT_LOAN',
            'CUSTOMIZED_LOAN'
        ]));

ALTER TABLE public.loan_applications
    DROP CONSTRAINT IF EXISTS loan_applications_loan_type_check;

ALTER TABLE public.loan_applications
    ADD CONSTRAINT loan_applications_loan_type_check
        CHECK (loan_type::text = ANY (ARRAY[
            'LOAN_ADVANCE',
            'EDUCATION_LOAN',
            'EMERGENCY_LOAN',
            'DEVELOPMENT_LOAN',
            'CUSTOMIZED_LOAN'
        ]));
