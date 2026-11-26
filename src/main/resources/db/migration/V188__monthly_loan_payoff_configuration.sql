ALTER TABLE loan_product_settings ADD COLUMN early_payoff_interest varchar(32) NOT NULL DEFAULT 'EXCLUDE_OPEN_PERIOD'
    CHECK (early_payoff_interest IN ('EXCLUDE_OPEN_PERIOD', 'ELAPSED_DAYS'));
