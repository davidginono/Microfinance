# Unified receipt vouchers

2026-10-19: receipt entry resolves principal and interest accounts per loan from its retained product-account binding; no shared interest-account input is required. See [product links and existing-loan setup](loan-product-account-links.md).

2026-10-18: supported recorded-loan receipts now allocate due interest, assessed charges and principal, including partial and charge-only collections, through the same loan-owned services. Receipt envelopes still create no second journal. See [recorded loan charges](recorded-loan-charges.md).

Implemented 13 October 2026: one receipt creation flow and receipt register, with the specialized repayment engine retained for directly recorded loans.

## Workflow and boundaries

Select **Loan repayment** as the receipt purpose, choose the branch client, receiving cash/bank/mobile-money account and interest clearing account, then add 1–50 transactions with distinct Loan IDs belonging to that client. Preview shows principal, due interest and principal balance after each payment. Post records the complete collection atomically. Ordinary receipts retain their existing configured/manual debit-credit flow. Purpose changes and client changes clear incompatible selections.

The UI uses an explicit purpose, not inference from activity names/codes. Activity metadata and a polymorphic Account Number field are not implemented by this increment. Payment/disbursement forms and legacy borrower-workflow repayments are not migrated. Loan choices include only directly recorded, disbursed eligible loans in the current institution/branch; loans without a verified ledger opening are not made eligible.

The repayment sidebar entry is removed for direct accounting. `/finance/loan-repayments/new` redirects to the receipt form, preserving Loan ID. Old POST forms return 410 rather than posting outside this workflow. Historical direct-recording repayments appear as single-line receipts without rewriting financial history. Component posting details/PDF/print routes redirect to their complete grouped receipt. Client ledgers and loan payment history still use the original loan-owned records.

## Posting architecture

`VoucherController` routes an explicit purpose to `LoanReceiptService`. The ordinary service rejects loan fields. The receipt service validates input, takes a scoped request lock, checks exact actor/payload retry identity, and locks all distinct loan sources in deterministic order. It invokes the existing `LoanRecordingService.repay` for each transaction inside one outer transaction. That service retains period, loan ledger, allocations, GL, audit and outbox ownership. Failure in any row or audit rolls everything back.

Migration V59 adds only append-only receipt header/row envelopes linking the existing accountant loan postings. Deferred constraints check tenant/branch, payer, dates, amounts, money account, actor, complete row count and complete independent reversals. No second collection journal or fabricated approval evidence is produced. External receipt references are protected across ordinary and loan receipts. Exact retries return the committed receipt; changed purpose/payload/actor cannot reuse the request key.

A correction must reverse every original row, use the original accounts, and be recorded by a different authorized accountant. Each loan's existing latest-unreversed rule remains in force. A later payment on any included loan can therefore prevent the entire correction. Partial component reversals are rejected in the service and database. Originals and linked corrections remain immutable.

## Access, performance and documents

Current ordinary BUSINESS claims plus loan VIEW/POST/REVERSE claims are rechecked. PDF/print require FINANCIAL_REPORTS_EXPORT and LOAN_RECORDING_EXPORT, plus view access. All reads/writes/choice queries/downloads enforce institution and selected branch. Session authentication, CSRF and bounded allowlist binding are retained.

Registers use scoped 25-row pages with a 26th sentinel, bounded search/date/status/sort and single selection. Choice lookups are scoped and paged. Preview uses batched loan reads (at most 50 IDs); posting serializes only the included loan records and the scoped request. Individual financial postings remain in the owning service; this is not a verified 1,000-RPS claim. Long-lived same-loan contention and extreme receipt size require profiling before increasing the cap.

Details and A4 embedded-font PDFs show client number, each Loan ID, principal/interest allocation, balance after posting, receiving/clearing/control account snapshots, exact total and amount in words, narration and real posting identity. Repeated headings/page numbers preserve multi-page receipts. No Details column, approval block, CSV or Excel output is added. PDF rendering runs after posting behind the shared export limiter and may be retried independently.

Only TZS due-instalment payments are supported. Advances, future interest, early settlement, new fees, historical openings and bank-channel reconciliation remain outside scope.

## Verification

Testing uses synthetic records in a disposable loopback PostgreSQL database, never the operational datasource. All 78 migrations applied through V59 and the following checks passed:

- 50 focused tests: 16 loan-recording PostgreSQL tests (including five unified-receipt scenarios), eight voucher MVC/security tests, three ordinary validation tests, 21 repayment-engine tests, one focused Jasper check covering receipt form/details, and one PDF check covering single/50-row English/Kiswahili documents. The final presentation follow-up recompiled both affected JSPs successfully.
- Complete multi-loan posting/retry, exact principal/interest allocation, rollback on a later-row or audit failure, concurrent duplicate receipt identity, complete independent correction, original-actor/partial correction rejection, duplicate external reference rejection, closed periods, branch mismatch and revoked claims.
- Synthetic authenticated browser flow: ordinary/loan purpose visibility, client-dependent Loan IDs, two rows, invalid-value preservation, preview/post/retry, detail and PDF/inline print downloads, register selection/filter reset, old entry redirect, stale POST rejection, CSRF, foreign-branch export refusal, login/logout, mobile sidebar/profile/notification controls and 360/768/1366/1920px layouts without document overflow.
- Shared table alignment follow-up: four contract tests scan every workspace JSP table and enforce the shared leading-edge rule; 48 live route/width checks cover 12 accountant registers/details/configuration views and all legacy numeric classes, with Debit/Credit journal checks. Header and body values are now left-aligned together, with tabular numerals retained. PDF/standalone print stationery is unaffected.
- JavaScript syntax, skill validation, diff checks and packaging passed. Single-page and first/final multi-page receipt previews and Debit/Credit screens were rendered and inspected. No CSV/Excel export was added.

This is focused correctness/UI evidence, not a full-suite, physical-printer, throughput or regulatory-certification claim. Verification helpers and screenshots remain outside production sources; application configuration changes that predated this task are excluded.
