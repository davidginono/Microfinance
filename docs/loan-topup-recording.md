# Linked loan top-up recording

2026-10-18 charge extension: supported source charge balances are settled separately from replacement charges/deductions, with an additional accepted charge sequence and exact linked corrections. New management fees on top-ups are blocked. See [shared charge behavior and policy gates](recorded-loan-charges.md). Earlier fee exclusions below describe the original increment; unverified charges remain unsupported.

Staff record a replacement agreement from a selected Loan record or its details using **Record top-up**. The new application retains the product's frozen review path and teller restrictions. Saving does not discharge the source or move money. Existing borrower application routes keep their existing safeguards.

## Settlement and cash

This increment supports one verified, directly recorded, active source in the same institution and first-class branch, one replacement agreement and positive net cash in TZS. Untracked balances, fees, outstanding provisions, write-offs and unavailable settlement information are refused. The institution's existing top-up feature switch remains authoritative. A signed agreement reference is required.

The dated quote uses remaining posted principal and unpaid contractual instalment interest due on the settlement date. Future scheduled interest is displayed separately and excluded. This uses the application's existing scheduled-interest convention; it does not introduce daily accrual, accrued-interest receivables, financed fees or a new income-recognition policy. Products and contracts requiring those treatments need their own supported settlement calculation before use.

New contractual principal is distinct from the source settlement and net cash. For example, new principal 1,500, source principal 1,000 and due interest 20 produce an internal settlement of 1,020 and actual cash of 480. The new schedule covers 1,500 and uses `LoanAmortizationCalculator`. Source principal and due interest are explicit financing components disclosed alongside the new agreement.

The teller's dated preview must still match the source sequence and principal/interest amounts when posting. Changed repayments or dates invalidate the preview and clear its confirmation. The contractual principal, pricing and tenure cannot silently change. Changes to approved contractual terms continue to require the existing reassessment/review path.

## Posting and corrections

Migrations V70 and V70.1 add immutable `accountant_loan_topup` sources and distinct `SETTLEMENT` / `SETTLEMENT_UNDO` loan movements with channel `INTERNAL`. Ordinary repayment commands reject that channel. An internal settlement has no cash receipt or receipt-voucher envelope.

One transaction locks the source/replacement records in stable order, opens the new ledger, allocates all remaining source principal and due interest, posts one owning GL journal, retains evidence, audits the event and writes its outbox. The journal debits new principal and credits source principal, interest collections clearing where needed, and the actual money account for net cash only. The specialised operational loan journals remain subledger records, not additional GL postings.

The source becomes settled only with its posted internal movement. Its original contract and future schedule rows remain immutable; the linked settlement terminates the future obligation without recording future interest as money received. A source receipt/statement identifies the internal movement. The disbursement register displays net cash, while the loan record and principal allocation retain the gross new principal. Its linked funding-correction movement offsets both cash and principal totals after reversal.

A different currently authorised accountant may reverse the linked top-up with a date, reason and evidence that the cash movement has been corrected. The replacement must have no repayments, adjustments or dependent top-up applications. The reversal offsets the original accounts, cancels the untouched replacement disbursement and restores the original source obligations. It never deletes or overwrites either contract, allocation or journal. Earlier source repayments cannot be reversed underneath an active top-up. References remain reserved.

Service and deferred database checks enforce linked sources, scope, exact accounts/amounts, balanced entries, allocations, source balances, open periods, independently recorded corrections and committed posting states. Same-payload retries return the original event; changed payloads cannot reuse its request key. No schema-history repair or changes to applied migrations are required.

## Screens and documents

The existing register has a selection-dependent action; there is no new global navigation or row Actions column. Creation uses the existing full-page form, read-only source context, a server-calculated cash breakdown, evidence and schedule preview. Review screens show the source Loan ID and dated estimates. The teller confirms the refreshed settlement and cash amount. Details link both loans and retain the correction history. English/Kiswahili labels and responsive fact grids use the shared ERP shell.

Loan/client history includes internal movements separately from received collections. Linked top-up documents support PDF and print only and show both Loan IDs, principal transfer, due interest, excluded future interest, actual cash and the replacement schedule. They never describe an internal transfer as a cash receipt.

The existing checked-ordinary-loan portfolio report does not certify top-up coverage. Its existing coverage checks exclude unsupported top-up and settlement histories rather than treating them as collections or known-zero records. GL statement totals continue to come from the actual balanced journal; no report-builder or institution-wide access is added here.

## Official-source review, 17 October 2026

- [Published 2019 Tier 2 regulations](https://www.bot.go.tz/Publications/Acts,%20Regulations,%20Circulars,%20Guidelines/Regulations/sw/2020021122490967551.pdf), regulation 42: repayment allocation, early repayment and exclusion of interest for the remaining period after full early settlement. The 2024 Feedback draft is not used as enacted law.
- [2024 guidelines on fees and charges](https://bot.go.tz/Publications/Acts,%20Regulations,%20Circulars,%20Guidelines/Guidelines/en/2024071716375097.pdf), guideline 15: contractual conditions matter for settlement fees. This increment adds no fees and does not infer permission to collect one.
- [2026 Tier 2 submission guidance](https://www.bot.go.tz/Publications/Acts,%20Regulations,%20Circulars,%20Guidelines/Guidelines/en/2026092118400398.pdf): institution/regulator review of changed lending policies, forms and agreements remains separate from software implementation. An evidence reference is not fabricated policy or regulatory approval.

This is a bounded implementation of the top-up/internal-settlement work in accounting checklist C05. It does not complete the accounting package, legacy cutover, daily interest accrual, fee settlement, impairment policy or external channel reconciliation.

## Verification

Java 25 compilation of the changed main/test sources passed. The targeted Maven test run passed all 100 tests, including 53 PostgreSQL recording tests, repayment-ledger regression tests, teller and CSRF/scope MVC checks, loan/statement document tests and Jasper compilation of the five affected full-page views. A fresh disposable PostgreSQL 17.9 database successfully applied all 90 migrations. The JavaScript run passed all 15 tests, including exact large-cent formatting and stale/failed settlement-preview invalidation.

Browser checks used synthetic records and the production JSP/controller/service code in a temporary authenticated harness. The form displayed new principal 1,500, source principal 1,000, due interest 20, excluded future interest 20 and net cash 480. English/Kiswahili details, print output, approved annual pricing, mobile navigation, selection-dependent actions and selection clearing on filter changes were checked. The form/details had no page-level overflow at mobile, tablet and wide desktop widths (360, 768 and 1920 pixels). The original source contract export is preserved; internal history has its own linked document.

No operational database or money was changed. Live login/logout and external payment-channel reconciliation were not exercised by this synthetic harness. The normal deployment must run the new Flyway migrations; policy, agreement approval and the financial coverage limits above remain institution-specific release work.
