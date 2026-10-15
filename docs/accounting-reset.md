# Accounting reset — 15 October 2026

Subsequent user instruction on 15 October: the new [financial statement workspace](financial-statement-workspace.md) is authorized, including PDF/print/Excel and preview-only Details drill-down. It builds on the remaining posted ledger and does not reactivate archived policies, release gates, supplier/asset workflows or period-management pages.

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

## Database schema follow-up

V63 moves the 43 retired accounting tables and the reconciliation allocation view from `public` to `accounting_archive`. Existing rows, identifiers, indexes and foreign keys are preserved. Archive tables reject inserts, updates, deletes and truncation. The migration removes 47 obsolete workflow functions and their triggers. Historical migrations are unchanged.

The remaining application reads archive evidence only for record retention, loan ownership and honest portfolio coverage. Core voucher, loan and repayment tables remain active. Existing accounting periods and their closed dates are frozen; core vouchers can still create their daily posting buckets. Old policy references remain on historical journals and periods so their evidence stays linked.

The development app automatically applied V63 during the build. Its applied checksum is preserved. A database backup was saved after that archive move and before the V64 follow-up. V64 preserves core period insert compatibility and indexes archived loan ownership lookups. Existing closing/reopening actions stay frozen. New financial statements and period-management workflows still require the user's new instructions.

Validation of the schema follow-up: 67 accounting, loan, scope and retention checks passed against the retired schema. The four upgrade checks also passed through the final V62 to V63 to V64 chain with retained source data. The package build passed. The local app was restarted on port 8081, Flyway applied V64, and the login page returned HTTP 200. A snapshot confirmed unchanged table identities and row counts for all 60 checked core and archived financial tables. The three disposable PostgreSQL test databases were removed after testing.

`AccountingSchemaRetirementPostgresTest` requires a fresh, disposable database named `microfinance_accounting_reset_test` and the `MICROFINANCE_RESET_TEST_URL` environment variable. It deliberately starts at V62 to test preservation of existing evidence during the upgrade.
