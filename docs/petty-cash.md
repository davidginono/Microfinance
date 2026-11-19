# Petty cash: direct expenses

Petty cash is a branch-scoped view of posted GL entries. It never creates a second book or a second posting. Record expense opens the ordinary payment form with the designated cash account selected; Payments can use that same account. Existing branch voucher approval rules and explicit claims still apply.

## Setup and opening evidence

Use Accounting Setup → Petty cash setup to designate one existing active cash posting account. Account VIEW and UPDATE claims are required. The designation is permanent and an account cannot serve two branches. Account creation and its version approval remain separate existing workflows; this feature seeds no accounts, permissions, balances or business limits.

Setup requires an evidence reference and either:

- A new account, created on the setup day, with no journal lines anywhere. Its known starting balance is zero. This records a zero origin, not approval of an imported financial balance.
- Complete independently reviewed opening coverage retained in `accounting_archive.gl_cutover_coverage`, linked to its posted opening journal. The account must be included in that opening or demonstrably created afterward without earlier movements. Historical use in another branch prevents designation. Coverage starts on the day after the opening journal.

The archived opening workflow stays retired and read-only. A used account without retained coverage cannot be activated through this feature. Missing setup or pre-coverage balances are unavailable, not zero. Reconcile existing petty cash records to journals and coverage before designation; do not replay those records as new expenses. The forward migration changes no posted history. Deploy with a verified backup and test migrations on disposable databases.

## Spending and funding

Each transaction involving petty cash may pay one ordinary expense account. A split between petty cash and another paying account is allowed; the expense table shows only the actual petty cash credit. Use separate transactions for different expenses. Advances, payable settlements, loans, asset purchases and outgoing transfers cannot use petty cash. These restrictions apply to templates, manual entries, approval validation and posting. Database guards reject specialized routes that try to bypass ordinary expense posting.

Add funds through an ordinary journal: debit petty cash and credit another eligible cash, bank or mobile-money account. Corrections use existing exact linked reversals. Funding reversal is blocked if it would overdraw petty cash. No new posting, approval or reversal permissions are granted.

Spending is checked against daily effective-date balances, including every subsequent recorded date, so a later top-up cannot conceal an earlier overdraft. Journal-line insertion and posting share account locks with setup. Concurrent attempts cannot spend the same money. The existing request identity, transactional journal/outbox/audit and period protections remain authoritative.

## Reading the cashbook

Expenses lists posted payment transactions and signed linked corrections. All movements groups the designated account’s posted GL lines by journal and includes every movement, including historical non-expense activity. Original records are retained and their reversal status is determined at the response cutoff. Historical split rows with expense and non-expense debits cannot be reliably attributed and remain in All movements.

Opening is the balance before the selected start date; closing includes its end date; current includes posted movements through today. Reporting uses one recorded cutoff and a repeatable-read transaction. Search and pagination do not change balances. Running balances are calculated across all movements before filtering or pagination, ordered by effective date, posting time, journal ID. Historical reports are current reconstructions at the displayed cutoff, not previously frozen statements.

The default view is Expenses for the current month. Tables have bounded server filtering, whitelisted sorting and 10/30/50-row pages. Pending payments use separate approval records and require the existing approval-workspace VIEW claim. They do not affect balances. Voucher details and PDF/print keep their existing permissions; this increment adds no exports.

## Verification

Use the isolated PostgreSQL voucher test harness for migration, exact attribution, corrections, prohibited uses, opening gates, concurrency and backdating. MVC tests cover entry routing, filter binding and CSRF; compile the three petty cash JSPs and shared payment form with Jasper. Run UI checks in both languages and at mobile, tablet and desktop widths against the changed build.

No throughput claim is made. Reads use a fixed number of scoped aggregate/projection queries and size-plus-one slices; the existing GL scope/date and account/journal indexes are reused. Query plans and lock behavior must be measured on representative deployment data before any capacity claim.

## Verified increment — 19 November 2026

- Java 25 production compilation passed. The final changed classes and affected tests were recompiled against the isolated build classpath, then verified through Surefire.
- **102 focused checks passed, with zero failures, errors or skips:** 56 PostgreSQL voucher/workflow checks; four petty cash MVC checks; three service checks; one architecture check; 24 existing voucher MVC checks; 12 voucher validation checks; one Jasper check compiling the three petty cash JSPs and shared payment form; and one rendered-form check covering both languages and all voucher types. The final checks ran in two disjoint groups of 65 and 37.
- Disposable PostgreSQL 17.9 applied all 193 migrations through V172. Coverage includes exact split attribution, linked reversals, missing opening evidence, live claim revocation, branch isolation, duplicate requests, concurrent spending, backdated overdrafts, pending exclusion, stale header exclusion, approval-validation recovery, unchecked outgoing journals and permanent designation protection. No operational database was migrated or configured.
- Browser checks used real controllers, services, repositories and JSPs with synthetic staff/data on loopback. Verified Expenses/All movements, server filters with unchanged cash totals, dated opening coverage, repeated expense rows with petty cash preselected, expense-only choices, shared status icons after refresh, pending status presentation, and English/Kiswahili rendering. Page containment passed at 360, 768, 1366 and 1920 pixels; the expense form also fit at 360 pixels. No browser financial posting was performed.
- A scoped balance aggregate over 33 synthetic posted movements used `ix_gl_line_account` and the journal scope key; one EXPLAIN ANALYZE sample took 3.984 ms execution time. This small correctness/query-plan sample is not a capacity benchmark. No new executors or asynchronous work were introduced. Throughput and the full application suite remain unverified.

Reproduction uses Java 25 and the existing isolated voucher test gate, `MICROFINANCE_VOUCHER_TEST_URL=jdbc:postgresql://127.0.0.1:<disposable-port>/microfinance_vouchers_test`, with `mvn -Dtest=VoucherPostgresTest,PettyCashControllerTest,PettyCashServiceTest,PettyCashArchitectureTest,VoucherMvcTest,VoucherValidationTest,ChartOfAccountsJspCompilationTest,VoucherFormRenderingTest test`. Scope Jasper with `-Dmicrofinance.jsp.pages=pettycash/workspace.jsp,pettycash/setup.jsp,pettycash/pending.jsp,vouchers/form.jsp`. Never point the migration harness at operational data.
