# Accountant loan recording

The accountant workspace has separate **Loan records**, **Loan disbursements**, **Loan repayments**, and **Client ledgers** destinations. This path records an existing loan agreement and actual movements directly. It does not create approval, verification, maker/checker, or release evidence. Existing borrower applications and historical specialized accounting retain their own workflows.

## Recording and posting

1. Select an active client in the current institution and branch and an active loan product. Enter application date, requested and contractual principal, tenure, first instalment date, purpose, and supporting information. Income, expenses and other debt remain unavailable when omitted. Information is identified as declared, documented or unavailable.
2. Preview the server-calculated schedule and save the loan record. Saving creates no loan receivable or cash journal. Recorded terms and product pricing are frozen; later product edits do not reprice the contract.
3. Record the actual disbursement date, amount, money account, loan principal control account, transaction reference and supporting reference. Posting opens the loan subledger and creates matching GL debits to principal and credits to the selected cash/bank/mobile-money account in one transaction.
4. Record a received repayment against instalments due on its effective date. Preview the allocation and resulting principal balance. Posting applies due interest before principal, creates the receipt and allocations, and debits the selected money account with matching credits to principal and interest collections clearing.
5. A different authorized accountant can reverse the latest unreversed repayment directly with a date and reason. This correction has no submission or approval stage. The original receipt and its journal remain immutable; the linked reversal restores the same allocations and accounts. A reversal cannot change an original account or silently reuse its payment reference. The original accountant's reversal action is disabled, and both the service and database reject that correction.
6. Records, posted documents, filtered registers and client statements are immediately available as PDF or print. Receipts retain principal balance immediately after posting; client statements show current balances and chronological movements. Arrears, due interest, future interest and principal are separate figures.

## Scope and integrity

Every read, lookup, write and output enforces the institution and first-class branch on the server. Current active accountant role, claims, staff access, institution and branch access are rechecked. Clients can read their own loan subledger through the existing loan detail; they cannot use accountant recording routes.

Posting uses scoped idempotency keys and payload hashes, per-loan serialization, active classified accounts, open-period checks, append-only sources, balanced GL lines, linked repayment transactions, audit and a transactional outbox. A retry returns the original committed record; changing its payload fails. PDF generation occurs after commit and can be retried without posting again. Closing a period cannot race a new posting into it.

Registers and histories use 25-row pages and bounded server queries. Exports require at most 500 records/movements; narrow the date/search filters for larger registers. Schedules are bounded to the existing calculator's supported contract size. Concurrent export generation uses the shared export limiter. These are architecture constraints, not a measured 1,000 requests/second claim.

Migrations `V56` through `V56_3` add immutable accountant sources and guards, independent repayment corrections, and accountant default claims. Contract protection compares JSON numeric values without treating equivalent decimal scales as different terms. No balances are fabricated for legacy loans. Ordinary receipt/payment/journal vouchers cannot access loan control accounts or impersonate this posting path.

## Financial boundaries

This increment supports TZS principal and scheduled interest, full ordinary disbursement, due-instalment partial/full repayments and repayment reversals. Products with application fees, processing fees or insurance charges are explicitly refused. Advances, early settlement, top-ups, restructuring, fee posting, disbursement cancellation and legacy opening-balance migration require their own accounting implementation; they must not be represented by changing a posted record.

Interest collections use an explicitly selected clearing account. The contractual interest schedule does not establish interest recognition, interest accrual, channel reconciliation, an institution's financial statement framework or statutory reporting approval. Statements disclose that they cover the directly recorded loans in this branch rather than claiming complete legacy balances. Supporting documents are recorded as evidence references; file ingestion is separate.

## Verification

### Register and detail UI — 12 October 2026

Loan Records, Disbursements and Repayments filter by created loan product ID instead of a visible status selector. Product choices are institution-scoped and searched/paged in bounded batches; inactive products remain available for historical records. Pagination and PDF/print reports preserve the product filter. Stored product terms and posting rules are unchanged.

These registers and both client-ledger tables include a Loan Product column. Posting details also identify the product. Names come from the original recorded loan snapshot, so renaming a configured product does not relabel historical transactions.

The column follow-up passed 44 focused database, document, Jasper, record-accessor and shared UI checks. Live rendering confirmed matching header/body columns in all three registers and both ledger tables, with horizontal scrolling contained inside the tables at 360px.

Record commands use the shared grey disabled state until selection, and selection clears on filter input/change/submission and restored navigation. Create and whole-register report commands remain independent. Detail actions stay beside the page title; nested breadcrumbs move with the title. Unframed loan, posting, client-ledger, journal, policy, source-opening, reconciliation and statement-result sections now use shared white surfaces and responsive fact grids.

Verification: all 24 accounting/report JSPs in the expanded Jasper check compiled. The full suite ran 1,273 tests with 248 opt-in skips; its sole error was an invalid date in the new product-filter test fixture. After correcting that fixture, all 11 loan-recording PostgreSQL tests passed, including identity-based filtering, inactive/renamed products, reports/totals, pagination and branch isolation. Synthetic live checks covered loan/disbursement product filtering, selection replacement/reset, detail/ledger rendering, login/logout, mobile navigation and representative 360/768/1366/1920px layouts without page overflow. Packaging passed. These checks do not establish throughput or complete the accounting backlog.

The opt-in PostgreSQL test uses only a disposable loopback database named `microfinance_recording_test`, with user `microfinance_test`. It covers recording without moving money, frozen terms, partial allocation, retry identity, independent corrections and original-actor rejection, settlement, concurrent duplicate settlement, scope/current permissions, closed periods, reference uniqueness, filtered reversal totals and full rollback on audit failure. PDF tests check pagination and embedded multilingual text. Financial PostgreSQL fixtures use bounded connection pools and close them after testing.

```powershell
$env:MICROFINANCE_RECORDING_TEST_URL = 'jdbc:postgresql://127.0.0.1:55457/microfinance_recording_test'
mvn '-Dtest=LoanRecordingPostgresTest,LoanRecordingPdfTest' test
mvn test
```

Validation on 9 October 2026:

- Compilation passed. All 50 focused tests passed: loan recording (10), multilingual PDF (1), existing vouchers (8), finance workspace security (4), existing repayment ledger PostgreSQL checks (6), and existing repayment service checks (21).
- All 74 initial Flyway migrations applied successfully to a fresh disposable database. The final 75-migration set, including the independent correction guard, was validated and upgraded through `V56_3` on the recording, voucher and repayment databases.
- The complete browser run passed: recording and previews, direct disbursement, partial repayment allocation, duplicate submission, independent correction, original-accountant correction rejection, current client balances and history, client-owned receipts, PDF and print outputs, CSRF, role and branch isolation, login/logout, sidebar/profile/notification controls, responsive layouts at 360, 768, 1,366 and 1,920 pixels, and no browser console errors. The exported repayment PDF was rendered and visually inspected without clipping.
- The initial full suite ran 1,248 tests: 984 passed, 257 opt-in tests were skipped, and seven view/export contract assertions failed. The follow-up fix updates the PDF/print expectations, checks shell includes through shared fragments, parses quoted JSP form attributes correctly, accepts existing responsive toolbar layouts, and supplies missing loan headers and loan/voucher preview table surfaces. Security coverage now verifies scoped PDF generation and rejection of both Excel and CSV before report generation.
- All 47 follow-up regression checks passed: admin UI contracts (17), shared view contracts (26), and SMS export security (4). The complete browser E2E passed again, and the schedule preview was checked on the shared table surface at 360, 768, 1,366 and 1,920 pixels without page overflow.
- The final full suite passed: 1,250 tests, 993 passed, 257 opt-in tests skipped, and zero failures or errors.

Rendered end-to-end evidence uses an isolated application and synthetic accountant/client/product/accounts, never the operational datasource.

## Startup migration correction — 10 October 2026

The configured database had already applied the original `V55` with checksum `-1305472888`. Later edits to that file changed its checksum to `450367604`, so Flyway validation prevented JPA initialization and produced the dependent `webConfig`/repository bean errors. Restore the original migration rather than repairing schema history or disabling validation, following the [Flyway versioned migration guidance](https://documentation.red-gate.com/fd/versioned-migrations-273973333.html).

`V57` carries the transaction-name size increase and deferred voucher/journal commit triggers forward. The existing `V56` retains the hardened voucher validation and loan-aware commit function. Financial history, posting authorization, append-only safeguards and configuration remain intact.

- `mvn -Dtest=VoucherPostgresTest,LoanRecordingPostgresTest test` passed all 19 checks without failures, errors or skips against fresh disposable PostgreSQL 17 databases. Tests verify the original V55 checksum, upgrade to V57, long transaction names, deferred commit rejection/rollback, and existing voucher and loan posting safeguards. Java 25 compilation passed.
- The configured PostgreSQL 18.3 database validated normally, retained V55's original checksum, and applied V56 through V57. JPA and Tomcat started successfully; `/login` returned HTTP 200 with the password input. The temporary verification launch used port 8082 and disabled SMS, outbox scheduling and retention cleanup only for that launch. The verification application and disposable database server were stopped afterward. No configuration change or schema-history repair was made.
- The full suite, browser financial workflow, packaging and throughput benchmark were not rerun for this migration fix; their earlier results remain documented above. Local commit: `Preserve applied voucher migration checksum`.
