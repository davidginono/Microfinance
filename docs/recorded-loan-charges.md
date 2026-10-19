# Recorded loan charges

This increment extends the linked top-up implementation committed as `ed4a254f`. It covers ordinary accountant-recorded loans and their supported top-ups. It does not certify an institution's pricing, insurer, agreement or regulatory approval.

## Customised loan fee entry (2026-10-19)

For new customised loans, the recorder explicitly enters application, processing and actual insurance amounts in TZS, including zero where no fee applies. These amounts do not inherit the hidden customised product's application or processing defaults. Entry requires the existing `LOAN_CHARGES_CUSTOM` claim; no claims are assigned automatically. Positive management components still require the effective policy's custom-fee permission, combined limit and supporting evidence. Actual insurance must remain within the configured product insurance amount and retain licensed-insurer, verification, premium-evidence and duplicate-reference checks. Configured-product calculations remain unchanged.

The recording form has fee entry and agreed totals, with no deduction controls or net-cash preview in customised mode. Saving freezes agreed fees but creates no assessment, collection or journal. Existing policy timing, disbursement-officer review/funding, linked corrections and top-up safeguards remain authoritative. Existing loans retain their frozen terms.

Verification: 85 selected Java checks passed across focused runs (64 PostgreSQL recording scenarios, 12 MVC checks, five custom-fee checks, two calculator checks, one presentation check and JSP compilation of three affected pages). All 21 recording/charge/top-up JavaScript checks passed. Browser connection failures prevented a live rendered check; the running application needs a restart to load the new backend rules and translations.

## Supported recording and disclosures

The recording form calculates the existing product's fixed application component, processing percentage and insurance percentage on the server, using contractual principal and decimal, component-by-component HALF_UP rounding to TZS cents. Application and processing are components of one management fee, with a disclosed subtotal. They are not additional top-up fees. The new agreement's management components must be zero for a top-up.

A dated, immutable product charge policy must identify the institution's existing approved policy and agreement, collection timing, and active scoped control accounts. Configuration requires both LOAN_PRODUCTS_CONFIGURE and ACCOUNTING_ACCOUNTS_UPDATE. No fee, account, policy approval, opening balance or custom-charge permission is seeded. Custom application/processing amounts additionally require LOAN_CHARGES_CUSTOM, an enabled policy permitting overrides, its combined management-fee limit and supporting evidence. The permission is not added to default accountant grants.

Insurance requires a licensed insurer and verification reference in the approved configuration, an actual premium and policy/premium evidence for the particular loan. This increment conservatively requires the calculated premium to equal the evidenced actual premium; mismatches, including a proposed discount, are blocked. The regulatory ceiling itself does not mandate equality. Reuse of the same insurer/premium reference is rejected within the institution, including across a source loan and its replacement. These are recorded evidence references, not an online licence check or independent insurer confirmation.

Frozen charge terms, calculation basis, amounts, timing, policy/agreement references and insurance evidence are retained in the contract snapshot and immutable charge terms. Product changes do not reprice them. Shared review views and loan PDFs disclose these terms alongside the principal/interest schedule. The PDF disclosure supplements the institution's approved signed agreement; the application does not generate regulatory approval or replace legal review of that agreement. A signed agreement evidence reference is required for assessment/funding. Declarations and supporting documents are explicitly distinguished from posted collections.

## Collection treatments

- **Separate:** charges are assessed once against the signed agreement, normally atomically at funding, and collected separately after disbursement through the explicit Loan repayment receipt purpose. Allocation is due interest, outstanding charges, then due principal. Partial charge collections and charge-only receipts are supported. A charge-only receipt creates no fictitious principal/interest repayment transaction.
- **Deduct:** the same funding transaction assesses the charges, collects exactly the frozen deductions internally, opens the original contractual principal and pays only net cash. Principal, deductions, separately payable charges and net cash are distinct. Deductions leaving no positive cash are rejected.
- **Financed:** visibly unavailable. Capitalization, affordability, effective-interest and accounting policy must be approved and implemented before fees can be added to principal.

A source top-up quote includes remaining posted principal, due contractual interest and outstanding assessed source charges. Future scheduled interest is excluded under the existing supported settlement convention. New insurance charges/deductions belong to the replacement agreement. Source charges are settled once; they are not assessed again. Both debt and charge sequences/amounts must still match the teller's accepted dated preview. For example, source principal 1,000, due interest 20 and source charges 30, against new principal 1,500 and new insurance deduction 7.50, produce net cash 442.50. Saving or approving the application does not settle the source.

## Accounting ownership

| Event | Debit | Credit |
| --- | --- | --- |
| Management assessment | Charge receivable control | Deferred management-fee liability |
| Insurance assessment | Charge receivable control | Insurance payable control |
| Separate collection | Actual cash/bank/mobile-money | Due interest clearing, charge receivable and principal control, according to allocations |
| Ordinary funding with deductions | Gross contractual principal control | Net actual money and deducted charge receivables |
| Top-up funding | New contractual principal control | Source principal, due-interest clearing, source charge receivables, new deductions and net actual money |

Management fees are deferred liabilities, not automatically income. Insurance is payable to the insurer, not lender income. Recognition/amortization of management fees and insurer remittance are not implemented here. Core ordinary vouchers cannot use these control accounts to bypass loan-owned posting.

Assessment commands and charge events form an append-only charge subledger. Specialized GL sources are LOAN_CHARGE_ASSESS, LOAN_CHARGE_UNDO, LOAN_CHARGE_FUNDING, LOAN_CHARGE_RECEIPT and LOAN_CHARGE_REVERSAL. Top-ups retain LOAN_TOPUP/LOAN_TOPUP_UNDO ownership. Existing LoanRecordingService, LoanRepaymentLedgerService, LoanReceiptService and the existing funding-correction workflow retain their responsibilities. Receipt envelopes group the owning loan postings without creating another collection journal. Core vouchers still post directly; no new approval queue is introduced. Product-configured lending reviews remain authoritative.

Full linked corrections preserve original terms, allocations, accounts, dates and evidence. A different authorized staff member reverses an assessment; direct assessment reversal is available before funding. Funding corrections also unwind deductions and active assessments, with the existing independent funding-correction safeguards. Top-up correction restores the source charges and debt while cancelling the replacement. Active dependent receipts/top-ups must be corrected first. Reversal retries return the original result; history is never overwritten.

## Integrity and access

V71 adds no legacy opening balances. It freezes charge contracts and terms, enforces scoped relationships, serializes financial writes per recorded loan, protects request/actor/payload identity and ordered event sequences, rejects duplicate assessments/overcollection and requires exact balanced owning GL journals, open periods, active accounts and outbox entries. Deferred lifecycle checks prevent omitted assessments/deductions and paid status with outstanding charges. Full funding corrections must leave no charge balance. Receipt component totals explicitly include charges. Corrections retain exact independent reversal links.

Every request uses institution and the loan's first-class branch. Client charge views/exports additionally require MEMBER_LOANS_VIEW and ownership. History reads are 25 plus a sentinel; exports stop at 500 movements. Financial charge audit events use ACCOUNTING_LOAN_CHARGES, retained by the existing accounting audit-retention exception.

Details, receipt previews/details, client statements and relevant PDF/print documents show charge amounts and outstanding balances. Borrowers have an owner-only charge statement with frozen terms, history and PDF/print. Assessment documents are labelled as non-cash. Disbursement documents use the actual balanced journal, including deductions. Screen tables retain leading-edge amount alignment and contained horizontal scrolling. This feature adds no CSV or Excel output and does not change the separate financial-statement workspace exception.

Posted evidence and arithmetic reconciliation are not bank/channel reconciliation. Refreshing a financial snapshot still does not receive money.

## Official requirements and institution policy

Official sources were rechecked on 18 October 2026:

- [BoT Fees and Charges Guidelines, 2024](https://bot.go.tz/Publications/Acts,%20Regulations,%20Circulars,%20Guidelines/Guidelines/en/2024071716375097.pdf): guidelines 13, 14, 15, 17 and 19 govern the management fee, insurance ceiling/licensing, agreed early-settlement fees, prohibited top-up/refinancing fees, and bilingual disclosure/publication. The implementation blocks a new management fee on a top-up; it does not silently waive a configured charge.
- [Enacted Tier 2 Regulations, GN 679 of 2019](https://bot.go.tz/Publications/Acts,%20Regulations,%20Circulars,%20Guidelines/Regulations/sw/2020021122490967551.pdf): regulations 39, 42 and 53 govern agreements, repayment/early settlement and disclosures. Existing supported top-up settlement excludes future scheduled interest; it does not introduce daily accrual or a new interest-recognition method.
- [BoT lending-policy guidance, 2026](https://www.bot.go.tz/Publications/Acts,%20Regulations,%20Circulars,%20Guidelines/Guidelines/en/2026092118400398.pdf): institution/regulator approvals for the applicable policy/forms remain external requirements. Recording reference text does not establish that those approvals exist.

Fee levels, reasonableness/affordability, overrides, account mappings, timing and recognition policy are institution decisions. No statutory fee rate or fictitious approval is inferred. Annual publication/submission, legal review and actual policy/insurer verification remain institutional release responsibilities.

## Remaining gates and visible restrictions

- Financed fees, income recognition/EIR amortization, insurer remittance/refunds, tax/third-party fees, new penalties, discretionary waivers and early-settlement/cancellation fees need their own approved policy and specialized accounting. None is synthesized by this increment.
- Separate cash collections before funding and arbitrary new charge kinds are unsupported. The enabled separate-collection timing is after disbursement.
- The batch allocator still supports principal/interest only. Loans with outstanding charges are visibly blocked there with directions to the interactive loan-repayment receipt form. Once deductions/collections have discharged the charges, existing batch behavior is unchanged.
- Unverified legacy charges/openings and other borrower-originated fee-posting paths are not migrated. Do not infer a verified legacy settlement from product settings or a JSON estimate.
- The existing supported scheduled-interest settlement convention, legal agreement/consent requirements and underwriting gates still apply. Complete bank reconciliation and the broader accounting/report-builder backlog are not declared complete.

## Verification

Verification on 18 October 2026 used Java 25, PostgreSQL 17.9 and synthetic records in the owned disposable test database. No operational loan or payment was posted.

- Standard Maven compile succeeded for all 369 main sources. Latest changed sources and tests were also compiled with Java 25 into isolated output directories; Maven Surefire used those outputs with the same project dependencies.
- 107 selected Java tests were exercised. After correcting new test fixture wiring and PDF capitalization/line-wrap assertions, 106 passed across the focused runs. This includes all 63 PostgreSQL loan-recording scenarios, all 11 recording MVC tests, fee calculation/presentation, existing disbursement and top-up paths, schedule details, receipt/posting/loan/client-statement PDF tests and compilation of the affected JSP pages.
- One existing source-wide JSP accessor scan remains red: unchanged ManagementReports.Choice lacks label(), and unchanged LoanRepaymentLedgerService.InstallmentRow lacks scheduledPayment(). Their getter-only definitions and JSP property use already exist in HEAD. The other two checks in that class pass. The complete repository test suite was not declared green.
- All 17 selected JavaScript tests passed: loan-charges.test.cjs, loan-recording.test.cjs and loan-topup.test.cjs. Coverage includes stale/pending/error clearing, decimal cents beyond JavaScript integer precision, safe text rendering, top-up preview freshness and teller confirmation behavior.
- PostgreSQL scenarios exercise component rounding, immutable terms, custom permission/cap, duplicate insurer evidence, partial/fee-only receipts, all-row rollback, concurrent idempotent receipts, deductions, closed periods, scope/ownership, source-charge top-up settlement, linked corrections and exact GL/control reconciliation. V71 applied successfully through Flyway; its final SQL also passed an independent transactional syntax preflight.
- Browser checks used real JSPs/controllers/services with a synthetic authenticated fixture on localhost. Reviewed the recording charge preview, funded details, receipt allocation, policy configuration and owner-only borrower statement; English/Kiswahili text and 360/768/1366/1920 CSS-pixel iframe previews were inspected. Tables retain their own horizontal scroll at narrow widths. The live ordinary quote showed principal 1,000, management fee 20, insurance 10, deductions 30 and net cash 970. The inspected recording page had no console errors. Financial POST actions were blocked in this fixture except the read-only quote endpoint.
- Loan and client-statement PDF extraction passed in both languages; the rendered English loan PDF was inspected for legibility and overlap. Browser evidence is saved outside the repository as loan-charges-recording.png in the task visualization directory. git diff --check passed.

The synthetic harness does not establish a full production login/logout, OTP or channel reconciliation acceptance run. Institution policy, actual insurer verification, approved account configuration and the release gates above remain required.
