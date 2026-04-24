ALTER TABLE loan_product_settings
    ADD COLUMN IF NOT EXISTS product_name VARCHAR(120);
