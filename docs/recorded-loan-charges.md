# Recorded loan charges

For a new top-up, the replacement product controls whether each fee applies. Application uses the configured fixed amount. Processing and insurance use the configured rates and the selected principal basis. The charge terms retain the basis, rate, amount and collection timing when recorded. Outstanding source charges remain part of settlement. See [loan top-up settings and settlement](loan-topup-recording.md).

## Custom insurance and charge-card regression fixes (12 November 2026)

Custom loan recording now retains the internal product's stored insurance ceiling when building the per-loan terms. It still takes application and processing amounts from staff entry. Premiums above that ceiling, including any positive premium against a zero ceiling, remain rejected. No pricing controls are added to the approval-only Customised Loan Configuration screen.

The charge-card JavaScript fixture now uses the actual JSP's single error element. Checks follow the current field-validation behavior: safely render the server message beside the affected field, clear stale field feedback when a quote request fails, and keep entered source amounts/percentages. Request failures remain displayed by the quote request owner.

Verification: affected Java source/tests compiled with Java 25. All 17 selected Java checks passed: the three previously failing PostgreSQL recording scenarios, ten custom-charge checks, two calculator checks and two fee-policy checks. All 30 related JavaScript checks passed; the final seven charge-card checks were rechecked after the fixture adjustment and passed. PostgreSQL checks used the owned disposable database on loopback port 55441 and validated all 177 existing migrations. No operational database was changed. This is a focused regression result, not a full-suite or throughput result.

## Simple charge entry (8 November 2026)

The recording form's Charges card contains only Application fee (TZS), Processing fee (% / TZS) and Insurance (% / TZS). Configured loans show read-only product values. Authorized staff entering a customized loan supply the application amount and can edit either the processing/insurance percentage or its amount; the server returns the matching value. The last edited value is authoritative. Missing custom values remain missing until explicitly entered, including zero. Monetary calculations and rounding stay in `LoanChargeCalculator`; JavaScript performs no financial arithmetic and preserves the edited control while quotes refresh.

The former explanations, override accordion, actual-premium/evidence inputs, charge table, timing column, totals, deductions/net-cash preview and policy link are removed from this card and its handlers. Real validation errors appear above the form actions. Product/policy configuration, custom-charge claims and caps, the insurance limit, top-up safeguards, scoped posting, idempotency and independent corrections remain enforced. Configured amounts cannot be overridden through the recording request. Separate funding/review evidence and existing financial details/exports remain in their owning flows.

New charge terms retain request-based entry provenance when no legacy supporting reference is supplied. This identifies recorded terms; it is not insurer-premium verification, a signed agreement, approval or payment evidence. The browser binder no longer accepts the removed insurance evidence field. Its nullable DTO property remains solely for existing stored/internal records and their original supporting references and duplicate checks. Prior frozen terms, financial records and account mappings are not rewritten. Four nullable input fields record the chosen percentage/amount basis without changing existing JSON defaults or adding database columns, migrations, indexes, external calls or executors. The saved application uses server-calculated charge amounts while the original request remains intact for audit and idempotency. The quote response omits the removed summary/policy fields and no longer loads source-loan settlement totals for this card. The existing institution/branch/record scope and CSRF remain unchanged; no throughput benchmark is claimed.

Verification for this change: 36 focused Java/MVC/PostgreSQL checks and 25 JavaScript checks passed, and the full recording JSP compiled. Sixteen rendered charge-card checks passed in English/Kiswahili at 360/768/1366/1920 pixels, including both custom edit directions and principal changes. Eight additional desktop/mobile schedule checks used the actual schedule JSP, quote controller, central amortization calculator and production JavaScript in a synthetic authenticated fixture. Both configured and custom schedules displayed below Charges, retained their principal/interest amounts after charge-only edits, refreshed after principal edits and ended at TZS 0.00. Due dates remain assigned at disbursement. PostgreSQL checks used an owned disposable database; no operational financial records were changed. This is targeted verification, not a complete repository test, production login or throughput acceptance run.

This increment extends the linked top-up implementation committed as `ed4a254f`. It covers ordinary accountant-recorded loans and their supported top-ups. It does not certify an institution's pricing, insurer, agreement or regulatory approval.

## Customised loan fee entry (2026-10-19)

For new customised loans, the recorder explicitly enters application, processing and actual insurance amounts in TZS, including zero where no fee applies. These amounts do not inherit the hidden customised product's application or processing defaults. Entry requires the existing `LOAN_CHARGES_CUSTOM` claim; no claims are assigned automatically. Positive management components still require the effective policy's custom-fee permission, combined limit and supporting evidence. Actual insurance must remain within the configured product insurance amount and retain licensed-insurer, verification, premium-evidence and duplicate-reference checks. Configured-product calculations remain unchanged.

The recording form has fee entry and agreed totals, with no deduction controls or net-cash preview in customised mode. Saving freezes agreed fees but creates no assessment, collection or journal. Existing policy timing, disbursement-officer review/funding, linked corrections and top-up safeguards remain authoritative. Existing loans retain their frozen terms.

Verification: 85 selected Java checks passed across focused runs (64 PostgreSQL recording scenarios, 12 MVC checks, five custom-fee checks, two calculator checks, one presentation check and JSP compilation of three affected pages). All 21 recording/charge/top-up JavaScript checks passed. Browser connection failures prevented a live rendered check; the running application needs a restart to load the new backend rules and translations.

## Supported recording and disclosures

The recording form calculates the fixed application component, processing percentage and insurance percentage on the server using decimal, component-by-component HALF_UP rounding to TZS cents. Ordinary loans use contractual principal. Top-ups use the selected fees and percentage basis from the replacement product. Application and processing remain components of one management fee, with a disclosed subtotal.

A dated, immutable product charge policy must identify the institution's existing approved policy and agreement, collection timing, and active scoped control accounts. Configuration requires both LOAN_PRODUCTS_CONFIGURE and ACCOUNTING_ACCOUNTS_UPDATE. No fee, account, policy approval, opening balance or custom-charge permission is seeded. Custom application/processing amounts additionally require LOAN_CHARGES_CUSTOM, an enabled policy permitting overrides, its combined management-fee limit and supporting evidence. The permission is not added to default accountant grants.

Insurance requires a licensed insurer and verification reference in the approved configuration, an actual premium and policy/premium evidence for the particular loan. This increment conservatively requires the calculated premium to equal the evidenced actual premium; mismatches, including a proposed discount, are blocked. The regulatory ceiling itself does not mandate equality. Reuse of the same insurer/premium reference is rejected within the institution, including across a source loan and its replacement. These are recorded evidence references, not an online licence check or independent insurer confirmation.

Frozen charge terms, calculation basis, amounts, timing, policy/agreement references and insurance evidence are retained in the contract snapshot and immutable charge terms. Product changes do not reprice them. Shared review views and loan PDFs disclose these terms alongside the principal/interest schedule. The PDF disclosure supplements the institution's approved signed agreement; the application does not generate regulatory approval or replace legal review of that agreement. A signed agreement evidence reference is required for assessment/funding. Declarations and supporting documents are explicitly distinguished from posted collections.

## Collection treatments

- **Separate:** charges are assessed once against the signed agreement, normally atomically at funding, and collected separately after disbursement through the explicit Loan repayment receipt purpose. Allocation is due interest, outstanding charges, then principal (including future principal for the user's 2026-10-19 advance-payment instruction). Partial charge collections and charge-only receipts are supported. A charge-only receipt creates no fictitious principal/interest repayment transaction.
- **Deduct:** the same funding transaction assesses the charges, collects exactly the frozen deductions internally, opens the original contractual principal and pays only net cash. Principal, deductions, separately payable charges and net cash are distinct. Deductions leaving no positive cash are rejected.
- **Financed:** visibly unavailable. Capitalization, affordability, effective-interest and accounting policy must be approved and implemented before fees can be added to principal.

A source top-up quote includes remaining posted principal, due contractual interest and outstanding assessed source charges. Future scheduled interest is excluded under the existing supported settlement convention. Selected new charges belong to the replacement agreement. Source charges are settled once. Both debt and charge sequences and amounts must still match the teller's accepted dated preview. For example, source principal 1,000, due interest 20 and source charges 30, against new principal 1,500 and a new deduction of 7.50, produce net cash 442.50. Saving or approving the application leaves settlement pending.

## Withheld fees recovered through repayments

A new version of an approved product charge policy can specify a withheld fee holding account. Choose an active liability clearing account and retain the approved policy and signed agreement references. The option applies to application and processing fees on ordinary individual funding. Insurance follows the frozen insurance policy.

For a full payout, record the actual cash or bank receipt and reference for the management fees. Funding and the fee receipt are linked and commit together. For a reduced payout, the amount paid out must equal approved principal less the agreed management fees and any authorized insurance deductions. The contract and ledger retain gross principal. The funding journal transfers the withheld management fees to the holding account.

On an individual or batch repayment, the principal portion first releases available application fees, then processing fees. Each release transfers the amount from the holding account to that fee's retained account inside the same repayment journal. The entire principal portion reduces the loan balance. Linked corrections restore the matching withheld-fee amounts. The batch and receipt screens show that recovery within principal.

Existing loans require a reconciled per-loan proposal and review by a different authorized staff member. Under Loan details, open **Review withheld fees** to record the verified outstanding amounts, holding account, and supporting record references. An open-period reclassification moves those amounts to the holding account. Loan and repayment history remains append-only. The new submit and approve claims require explicit assignment. Top-up settlement and other unsupported debt closures stop while a withholding recovery balance remains.

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
- Batch receipts support separately outstanding fees with the same allocation as individual receipts. See [receipt batch processing](receipt-batch-processing.md).
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
# Separate fee Income mappings (12 November 2026)

New staff-saved charge policies use accounting version 2 and separate Application fee and Processing fee Income posting accounts. Assessment debits charge receivables and credits the corresponding Income account; later collections clear receivables and do not recognize income again. Insurance retains its insurer payable account. Existing timing, insurer evidence, custom-charge limits, top-up restrictions and independent linked corrections remain enforced.

Version 1 policies retain the shared deferred management-fee account. Frozen loans keep their original immutable policy and term accounts, including when staff subsequently save version 2 for the product. Assessment and corrections resolve that frozen policy, rather than today's product mappings. The stored `liability_account_id` and serialized `liabilityAccountId` remain compatibility identifiers for the term's credit account; version 2 can reference Income through these identifiers. Prior deferred balances are not transferred to Income by this change.

Charge configuration is embedded in Create/Edit Loan Product. Existing products show generated Income accounts as proposed defaults and retain their old policy until staff save a new effective revision. Account generation supplies no missing policy/agreement/insurer evidence and does not enable charges. See [account generation, migration and deployment](loan-product-account-links.md).

The deferred-fee-only statements in the earlier implementation notes describe accounting version 1. They do not apply to the explicitly selected version 2 assessment treatment.

## Funding charge selection (16 November 2026)

The disbursement form's Deduct picker can collect selected existing separate charges from the funding cash. The server quotes the exact frozen amounts, keeps agreed deductions mandatory, and rejects unknown or duplicate selections and a non-positive cash payout. The existing disbursement permission remains required. Charge events use the payment reference when no separate funding evidence is supplied; ordinary funding does not request another signed agreement reference. Top-up settlement retains its separate evidence requirement. Changing selections refreshes the quote and clears confirmation; a failed refresh disables posting until retry succeeds.

V164 permits this election through the existing append-only DEDUCT events, tied to the same scoped funding posting and balanced journal. It changes no tables, claims, principal, frozen charge terms or historical estimate. Top-up source settlement remains separate from new-loan deductions. Independent funding corrections retain linked DEDUCT_UNDO evidence. Apply deployment migrations only with the verified backup required by the staff-only accounting deployment procedure; verification uses a disposable database.

Monetary entry controls share the amount formatter, including product fees, client income and loan evidence rows. Commas are presentation only; native validation and submitted decimal values remain unchanged. The loan summary keeps its credit cards and unavailable values without the redundant live-status notice.

Verification: 113 focused Java/PostgreSQL/JSP checks and 95 JavaScript checks passed. PostgreSQL 18.3 applied all migrations through V164 in a fresh disposable database. Browser checks covered charge selection, retained references, invalid-date recovery, local credit-card hydration, typed income/expense separators and contained layouts at 390, 768, 1366 and 1920 pixels. Browser checks posted no funding. These correctness checks do not measure throughput.

17 November update: Amount to Disburse and Deduct share one responsive row, and ordinary funding has no extra agreement-reference input. Verification passed 107 focused Java/PostgreSQL/JSP checks and 14 JavaScript checks, plus browser checks at 390, 768, 1366 and 1920 pixels. No database migration or additional query is required.

## Cash-group disbursement accounts (17 November 2026)

Disburse loan uses the existing transactional posting service: debit the linked loan principal account, credit the selected payment account with net cash, and post any charge deductions or top-up settlement through their owning ledgers. Repeated requests retain the same posting; independent corrections retain the original accounts and linked history.

The funding chooser accepts active Asset posting accounts with Cash, Bank or Mobile Money purpose, plus Other-purpose posting accounts directly under the configured cash family (`gl_cash_bank_group`, structural code `111000`). This supports the group named Cash and Cash equivalents without inferring eligibility from editable names or changing account purposes. Headings, inactive accounts and unrelated Other accounts remain ineligible. Lookup, retained selection, posting validation and database guards use the same rule. Existing disbursement claims and loan institution/branch checks remain authoritative; receipt and repayment account rules remain separate.

V165 adds that shared database rule and updates ordinary, charged and top-up funding guards without rewriting accounts, approved versions, financial records or permissions. Apply it with a verified backup before starting this build. The chooser remains bounded to 26 rows per request. Empty setup and failed lookup show distinct local messages; reopening a failed menu retries the lookup. The standalone Try again button is removed; a failed payout quote can be refreshed through the date or charge controls.

Deployment preflight recovered the exact already-installed V164 source (Flyway checksum `1020106983`). Its published text stays intact; V165 corrects the ambiguous charge-term aliases forward. This avoids a checksum repair or a rewrite of installed migration history.

Verification passed 111 focused Java/PostgreSQL/JSP checks and 59 JavaScript checks. Coverage includes unrelated and inactive accounts, heading exclusion, branch scope, bounded choices, balanced selected-account postings, idempotency, deducted charges, top-ups and independent corrections. Browser checks used synthetic cash-family accounts on desktop and mobile and posted no funding. The loan regression rerun used the database without the temporary browser application's background workers. These checks do not measure throughput. Deploy the migration and restart the application to replace an older running build.
