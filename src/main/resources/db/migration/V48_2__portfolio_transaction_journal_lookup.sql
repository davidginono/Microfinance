-- Scoped ordinary-loan reports validate immutable transaction journals at the retained recorded cutoff.
CREATE INDEX ix_portfolio_transaction_journal
    ON loan_journal_entries(transaction_id,posted_at,loan_application_id)
    WHERE transaction_id IS NOT NULL;
