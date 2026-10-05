-- Bounded history/detail and retained-account/member lookups must not scan whole books.
CREATE INDEX ix_gl_journal_branch_history ON gl_journal(sacco_id,station_id,recorded_at DESC,id);
CREATE INDEX ix_gl_line_journal ON gl_journal_line(journal_id,id);
CREATE INDEX ix_gl_journal_period ON gl_journal(period_id,state,id);
CREATE INDEX ix_gl_journal_maker ON gl_journal(maker_id);
CREATE INDEX ix_gl_journal_checker ON gl_journal(checker_id) WHERE checker_id IS NOT NULL;
CREATE INDEX ix_gl_account_maker ON gl_account(maker_id);
CREATE INDEX ix_accounting_period_creator ON accounting_period(created_by);
CREATE INDEX ix_accounting_period_closer ON accounting_period(closed_by) WHERE closed_by IS NOT NULL;
