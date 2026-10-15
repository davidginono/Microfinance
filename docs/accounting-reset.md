# Accounting reset — 15 October 2026

The following application pages, routes, services, forms and feature tests have been removed at the user's request:

- Accounting policies and policy settings
- Financial statement preparation, results, mappings, downloads and regulatory submissions
- Accounting releases and statement output reviews
- Suppliers, Business entries and Assets
- Source opening balances and the old journal opening/import workflow
- Reconciliation, period opening, closing and reopening workflows

These features are awaiting new instructions. The old accounting checklist is historical reference, not authorization to recreate them.

Core receipt, payment and journal vouchers, chart of accounts, transaction configuration, loan recording, disbursements, repayments, client loan statements and loan portfolio reports remain.

No stored financial records, evidence, accounts, permissions or historical database migrations were deleted. Old permission identifiers remain compatible with stored records but unavailable permissions are excluded from assignment and effective access. The existing voucher date-bucket helper and closed-date checks remain necessary posting infrastructure; there is no period-management page or action.

Read-only retention checks still prevent deleting institutions or staff referenced by historical accounting evidence. Loans owned by retired source workflows cannot be changed through ordinary loan posting. Accountant-recorded repayments and reversals still require their exact authorized posting tickets.

The removed features' unfinished local edits were saved outside the repository before deletion in `accounting-reset-before.zip` in this chat's artifacts directory. The unrelated voucher detail and table styling edits were left in place.

Validation: the remaining accounting JSP views compiled, all 78 browser tests passed, and PostgreSQL accepted the retained-history and posting-authorization queries in a read-only transaction. The full Java suite ran 1,009 tests with 10 failures, 1 error and 83 skipped database tests. The failures concern existing dashboard/loading/table contracts and a loan-product test fixture; those files and behaviors were outside this removal.
