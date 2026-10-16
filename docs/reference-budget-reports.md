# Account budgets and reference operational reports

This increment implements the user's 2026-10-15 FERPS and screenshot references in the existing Spring MVC/JSP application. It does not enable deposits, shares, external Foresight integrations, or new loan-posting workflows.

## Reference inspection

FERPS was inspected using the supplied login without creating or changing any records. Budget creation selects a date, year and account, then captures January–December amounts. Its created-budget register was empty. Daily Collections filters by account and date range. The live portfolio and ageing screens expose client/product/date filters. The Cashbook link produced an empty content area; its supplied parameter and output screenshots therefore supply that layout reference.

The supplied risk screenshot uses 181–365 and over-365-day bands; the live ageing screen uses 181–360 and over-360. The implementation follows the screenshot's 365-day boundaries. Those reference bands and 0/10/30/50/100% estimates are explicitly management assumptions, not approved institution provisions or a regulatory return. Existing regulatory-reference metrics and lending rules remain independent.

## Budgets

`/finance/budgets` lists the latest account/year revisions in the current branch. Create captures an active institution posting account, budget date, year and twelve nonnegative exact monthly amounts in TZS. All months are required; zero is explicit and the annual total must be positive. Details show monthly and annual totals, recording identity and the previous revision.

Saving is planning configuration and creates no journal. Revisions append records rather than editing prior amounts. Scoped request-key and account/year transaction locks, canonical payload hashes, uniqueness constraints and audit logging protect retries and concurrent saves. A stale revision cannot replace a newer budget. The new `V68__account_budgets.sql` migration adds this storage without changing seeded business rules or existing financial records.

The existing `ACCOUNTING_ACCOUNTS_VIEW` and `ACCOUNTING_ACCOUNTS_CREATE` claims govern viewing and creation. Revision saves additionally require `ACCOUNTING_ACCOUNTS_UPDATE`. There are no new automatic permission grants or approval steps.

## Reports

The shared Accountant Office navigation supplies five fixed reports under `/reports/financial/operations/`:

| Path suffix | Parameters and row grain |
| --- | --- |
| `daily-collections` | Inclusive transaction dates, cash/bank account bounds, client search. Ordinary receipt transactions and separate loan principal/interest components, grouped by effective date and activity. |
| `cashbook` | Inclusive transaction dates, cash/bank account bounds, client search. One posted GL cash/bank/mobile-money journal line, grouped by account. |
| `loan-portfolio` | Disbursement dates, client/product search. One loan, with contractual instalment/frequency, maturity, principal balance and contractual interest balance. |
| `portfolio-risk` | As-at date, client/product search. Whole outstanding principal classified by the oldest unpaid instalment, plus overdue amounts and the reference provision estimate. |
| `portfolio-analysis` | As-at date, client/product search. Disbursed principal, signed payments, principal/interest balances and arrears. |

Each route has a `/pdf` and `/print` endpoint. These reports remain PDF/print only; the separate financial-statement Excel exception is unchanged. Their paper faces use centered sans-serif titles, serif headings, monospaced detail rows, account/product/activity grouping and underlined totals. Wide tables remain in the preview's scrolling canvas; PDFs and print use landscape A3 with repeated table headings. No screen controls appear in exported report documents.

All routes require fresh financial-report and dataset claims in a valid institution/branch staff workspace. Collections additionally require `ACCOUNTING_BUSINESS_VIEW`, Cashbook `ACCOUNTING_JOURNALS_VIEW`, and loan reports `LOAN_REPORTS_VIEW`. Both PDF and print additionally require `FINANCIAL_REPORTS_EXPORT`. Reads, account choices and exports retain server scope, and run/export audits omit client search text and borrower details. Budget mutations retain CSRF protection.

## Financial meaning and limits

- Receipt envelopes group loan-owned postings without creating or counting another collection journal. Principal and interest component amounts reconcile to the posted payment. Original collections and linked corrections remain separate credit/debit rows. Activity, daily and grand totals include all matched pages.
- Daily Collections explicitly counts legacy repayment transactions missing supported posted receipt sources as unavailable. Ordinary receipts do not invent client numbers or phone details. Loan transaction codes unavailable from the posting source remain blank rather than being fabricated.
- Cashbook is an account movement report, not a certified opening/closing balance or channel reconciliation. Transfers can appear in each affected account. Loan component columns use linked loan postings; unavailable allocations remain unavailable.
- The loan reports reuse `LoanPortfolioReportRepository`'s immutable origins, signed transactions, allocations, contract checks and coverage exclusions. Mutable paid/status projections do not establish balances. Fees, settlement, write-off and provision postings are not introduced here.
- Risk bands assign each verified loan's entire principal exposure once. Reference provisions round each loan's estimate to cents before aggregation. The overdue column is overdue principal plus interest, not total future contractual payments.
- Contractual interest includes future scheduled interest and is labelled separately from today's settlement amount. The instalment is the largest contractual payment, with frequency shown separately. Gender and default-rate fields without a supported source display unavailable.
- Loan exclusion counts describe unresolved branch history before client/product filtering. Partial loan totals cover verified sources only; all-unverified balances and totals remain unavailable.
- Results use a repeatable-read snapshot and an explicit recorded-data cutoff retained in pagination/export links. These are live management reports, not retained finalized statements; they do not freeze later changes to client/account display names.

Preview pages fetch 25 rows plus lookahead. Exports are limited to 5,000 rows, groups to 1,000, and date ranges to 366 days. Existing export admission control is reused. Queries are scoped and parameterized, with aggregate totals across the filtered result; there are no per-row database lookups, external calls, new workers or executors. No throughput benchmark or 1,000-RPS claim is made.

## Verification

Verification results are recorded in the accounting checklist evidence log. PostgreSQL tests use disposable loopback databases, never application datasource credentials. Temporary preview servers, PDFs and screenshots are verification artifacts and are excluded from the production commit. Institution policy approval, historical source reconciliation and the broader accounting/report-builder release gates remain separate.
