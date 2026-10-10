-- NULL preserves existing products that permit applications without a count limit.
ALTER TABLE loan_product_settings
    ADD COLUMN maximum_active_loans integer,
    ADD CONSTRAINT ck_loan_product_maximum_active_loans
        CHECK (maximum_active_loans IS NULL OR maximum_active_loans BETWEEN 1 AND 10);
