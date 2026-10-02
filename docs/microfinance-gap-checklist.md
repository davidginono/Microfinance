# Microfinance Conversion Gap Checklist

Audit date: 2026-10-01. Baseline: `184b3de` (Transform loan workflow to microfinance).

This is an implementation backlog based on targeted code inspection, not a compliance certification or confirmation that every route was tested. No loan behaviour is changed by this document. Findings describe the baseline above; mark items complete only after implementation and verification.

Implementation update (2026-10-01): a limited local repayment increment now supports new ordinary disbursements, scheduled payments, receipts, linked reversals, and balanced clearing entries. See [Repayment Ledger Increment](repayment-ledger-increment.md) for supported operations and remaining release gates. The baseline gaps below are retained as an audit record; the full accounting/reconciliation slice is not complete.

Credit-estimate update (2026-10-01): server-derived draft/submission snapshots, a shared decimal calculator, normalized affordability, explicit invalid/missing-data failures, anchored monthly dates, and new-quote disbursement term checks are implemented. See [Credit Estimate Increment](credit-estimate-increment.md). This does not complete verified underwriting, automatic existing-debt servicing, approved fee policy, accepted agreement versioning, or reconciliation.

Credit-estimate verification (2026-10-01): the latest packaged build reports 743 tests with no failures, errors, or skips, including five disposable PostgreSQL tests. English Chromium/Playwright checks covered weekly quotes, failed assessments, invalid declarations, CSRF, stale-quote clearing, submitted-snapshot tampering, login/logout, and widths of 360, 768, 1366, and 1920 pixels without page overflow or application JavaScript errors. Kiswahili messages have regression coverage; this increment's Kiswahili browser flow was not verified.

Accounting/report-builder planning (2026-10-02): the [Accounting Package And Report Customization Goal](accounting-report-builder-checklist.md) defines phased agent tasks, dependencies, human approval gates, and acceptance tests. It does not mark the accounting/reconciliation or reporting gaps below complete.

### Verified Increment

- `mvn package` passed 704 tests with no failures, errors, or skips, including five opt-in tests against disposable PostgreSQL 17.9. The WAR was packaged successfully.
- Chromium/Playwright checks exercised registry search and empty/error states, payment allocation, exact retry, CSRF rejection, printable receipts, rejected overpayment/reference reuse, same-officer refusal, linked correction by a second officer, borrower read-only access, Kiswahili preference, logout, and wrong-client/wrong-branch access.
- Rendered widths: 360, 768, 1366, and 1920 pixels. No page overflow or application JavaScript errors were observed. Expected 404 responses for fake accounts without profile photos used the existing fallback. Browser plugin was unavailable; bundled Playwright was used.
- Archive/institution/account deletion guards and repayment-audit retention have regression coverage. No operational credentials or records were used for database or browser verification.
- Still open: approved pricing/schedule conventions, verified affordability/KYC, existing-portfolio cutover, mapped general-ledger accounts and reconciliation, fee/early-settlement handling, top-up settlement, broader reporting, and regulatory release gates. This increment does not establish production readiness or measured 1,000-RPS throughput.

## Target And Boundaries

- Target a Tanzania Mainland Tier 2, non-deposit-taking lender, initially offering individual business loans. Confirm the operator's licence and approved policies before production use. Zanzibar requires a separate jurisdiction review.
- Keep the existing Spring Boot application, session authentication, loan approval workflow, documents, notifications, and institution/branch controls.
- Remove savings/share prerequisites from the borrower journey. Do not reintroduce Foresight. Optional future bank, mobile-money, identity, or credit-bureau connections must not replace the local loan ledger.
- Use Institution, Branch, and Client/Borrower in the interface. Preserve `sacco_id`, `station_id`, entity/package names, routes, and permission keys until a separately planned migration.
- A loan schedule describes what should be paid. A transaction ledger records what actually happened. Financial snapshots do not replace accounting records or reconciliation.

## Priority And Status

- P0: necessary before trusting live loan balances or launching the new lending model.
- P1: necessary for a controlled operational rollout; implement before unrestricted production use.
- P2: later product expansion or automation, after the core controls work.
- Foundation: reusable code exists, not a statement of production readiness.
- Partial: some behaviour exists, with an identified gap.
- Not found: no dedicated implementation identified in the inspected paths; confirm before introducing a replacement.
- Unverified: needs a focused audit rather than an assumption that it is absent.

## Functional Requirements And Gaps

| Priority | Capability | Current Evidence | Required Next Step |
| --- | --- | --- | --- |
| P0 | Repayment posting | Not found: no loan repayment transaction entity or posting command identified. `LoanApplication` stores a schedule and snapshots. | Record confirmed payments, allocations, receipts, reversals, actor, institution, branch, and actual payment date. |
| P0 | Authoritative balances | Partial: `LoanPaymentSummarySyncService` reports success without recalculating balances; scheduled sync returns zero counts. | Derive balances from posted transactions and dated obligations; remove misleading refresh success and snapshot-only payment totals. |
| P0 | Accounting and reconciliation | Not found: no financial journal/posting implementation identified. SMS ledgers are unrelated. | Implement balanced financial entries, cash/bank accounts, settlement controls, and reconciliation to the loan subledger. |
| P0 | Schedule and interest accuracy | Partial: `RepaymentScheduleService` has local flat/reducing schedules; weekly instalments use months times four while the periodic rate uses 52 weeks. Flat fallback ignores tenure; annuity calculation uses doubles. | Agree rate/date conventions, use a tested decimal calculation approach, lock contractual terms, and reconcile the final instalment. Do not retroactively rewrite contracts. |
| P0 | Affordable credit assessment | Partial: `EligibilityService` calculates disposable income and collateral coverage from declarations. Missing/zero repayment can pass; active exposure is returned but not used in this eligibility decision. | Require a valid proposed repayment, normalize repayment/income periods, verify evidence, include debt servicing, and record review/exception reasons. |
| P0 | Pricing and product policy | Foundation: `LoanProductSetting` has limits, tenure, rates, fees, affordability, collateral, guarantors, documents, and approval stages. | Review allowed fees and reducing-balance policy; version approved terms. Existing separate application/processing fee presentation needs review. |
| P0 | Loan agreement and disclosures | Partial/unverified: documents and signatures exist; complete contractual pricing, effective annual rate, and liability disclosure have not been confirmed. | Audit agreements and consent against approved policy; retain the accepted version and evidence. |
| P0 | Existing portfolio cutover | Partial: snapshots and status-based paid totals exist; `ManagerService` can mark a top-up source loan paid without a settlement ledger. | Reconcile opening balances, preserve known history, flag unknown amounts, and account for top-up settlement and net cash movement explicitly. |
| P1 | Client onboarding and KYC | Partial: `Member` and `MemberRegistrationForm` capture basic name/contact/number/branch data, not a structured verified credit profile. | Add staff-assisted onboarding, identity/address/business evidence, verification states, duplicate controls, and consent/retention records. Audit OTP separately. |
| P1 | Business/employment and field verification | Partial: configurable application fields collect employment, income, expenses, and collateral descriptions. | Add verified evidence, officer visit findings, business cash flow, household obligations, and reviewer attribution. |
| P1 | Guarantor strength | Partial: `LoanQualificationPolicyService` checks active-loan/default/count rules. Income is not enforced; a legacy amount-named field is treated as a guarantee count. | Distinguish count from monetary liability limits; assess income, commitments, verified identity/contact, and informed approval. Do not silently change the legacy field's meaning again. |
| P1 | Collateral management | Partial: declarations and document handling exist; no dedicated collateral register identified. | Track ownership, valuation evidence, security/consent, custody, release, and authorized recovery. A declaration is not perfected security. |
| P1 | Approval and disbursement | Foundation: `LoanWorkflowService` and `ManagerService` preserve staged reviews, signatures, proof, and local schedules. | Retain product-selected stages/quorum; add verified-assessment gates and ledger-backed disbursement with duplicate protection. |
| P1 | Arrears and collections | Partial: PAR/default statuses, reminders, and analytics exist. Instalment-level payment reconciliation is unavailable. | Calculate unpaid due amounts and oldest unpaid date; add assigned follow-up, promises to pay, lawful notices, and collection history. |
| P1 | Restructuring and write-off | Unverified: no complete dedicated financial amendment workflow established by this audit. | Check existing routes, then add approvals, revised schedules, preserved originals, accounting treatment, and recovery records. Write-off is not automatic debt forgiveness. |
| P1 | Reports and credit information | Partial: analytics/reporting exists; no dedicated CRB submission implementation identified. | Add reconciled financial/arrears reports, approved credit-sharing consent, validated submissions/exports, submission history, and corrections. |
| P1 | Complaints and privacy | Unverified: support incidents exist; regulatory complaint deadlines, decisions, escalation, and retention controls need a focused audit. | Separate incidents from client complaints, retain accountable handling/evidence, and validate applicable privacy obligations. |
| P1 | Microfinance language and digital journey | Partial: main rebranding exists; registration validations still contain legacy terms and new registrations default to English. | Complete English/Kiswahili wording and review digital clearance, language, disclosures, receipts, and privacy release gates. |
| P2 | Additional products and integrations | Partial: `RepaymentFrequency` supports weekly/monthly only. No complete salary, group, seasonal, or external-channel model established by this audit. | Add independent product capabilities after core readiness; fortnightly needs a real frequency model, not a label change. |

## Workflows In Simple Terms

### Client To Disbursement

1. Register the client under the correct institution and branch; verify identity, contact, and consent.
2. Select a product and show amount limits, rate basis, fees, repayment frequency, and security requirements.
3. Capture purpose, business/employment, income, expenses, existing debts, documents, and proposed guarantors/collateral.
4. The officer verifies evidence and cash flow; the system calculates affordability using consistent periods and known commitments.
5. Guarantors receive liability terms and approve where required; authorized reviewers follow the product's existing approval path.
6. Retain the accepted agreement, complete pre-disbursement checks, and confirm disbursement and the contractual schedule.

### Payment To Receipt

1. Receive a payment or import a confirmed channel record; unresolved transactions go to reconciliation.
2. Match the loan, amount, actual payment date, channel/account, and transaction reference.
3. An authorized command allocates the payment and commits the loan transaction and financial journal together.
4. Show the receipt, updated balance, and remaining due amounts. Send notifications after the database commit.
5. Incorrect postings use approved linked reversals/adjustments; never delete or silently change the original receipt.

### Late Payment To Resolution

1. Compare due instalments with posted allocations, not just maturity dates or manual statuses.
2. Assign follow-up and record contact, a promise to pay, or a dispute.
3. Record a verified payment, approved restructuring, or legally reviewed recovery action.
4. Reconcile the outcome and retain decision history. Close only when settlement criteria are met.

## Architecture

Keep a modular monolith with existing controllers, authorization, service transactions, JPA repositories, PostgreSQL, and the JSP shell. Separate assessment, product policy, loan workflow, schedules, repayment posting, accounting, reconciliation, and reporting by responsibility; no microservices are needed for this conversion.

New financial records need an explicit schema migration. Suggested boundaries, subject to implementation design:

- Payment transaction: received amount, currency, channel/account/reference, effective date, posting time, immutable actor/scope, idempotency key, and linked corrections.
- Payment allocation: links a transaction to principal, due interest, or a permitted fee/charge obligation, preserving amount and obligation identity.
- Journal: balanced entries for disbursements, receipts, adjustments, and settlements, linked to their originating transactions.
- Reconciliation item: channel evidence, matching outcome, review state, and exception history.
- Contract/obligation version: agreed rate, calculation basis, fees, dates, and schedule; revisions retain previous versions.

Use JSON snapshots for historical presentation and assessment evidence, not as the only financial truth. Reports query scoped, indexed projections and aggregates. Reuse existing audit/outbox patterns; receipt notifications and heavy exports run after commit or in bounded background batches.

## First Implementation: Repayment Ledger And Accounting

This slice is not implemented by the checklist. It includes schedule validation and opening-balance controls, not merely a payment form.

### Rules To Establish Before Posting

- [ ] Confirm approved rates, accrual/date basis, rounding, allowed fees, and early-settlement calculation with accounting/compliance staff.
- [ ] Define payment date versus posting timestamp, late imports/backdating, closed periods, holidays, and audit approvals. Use an explicit Tanzanian business timezone.
- [ ] Normalize instalment and affordability periods; do not compare one weekly instalment with a monthly disposable-income limit.
- [ ] Define excess payments, advance principal repayment, unmatched funds, refunds, and partial early settlement. Never discard money, create negative debt, or treat unearned future interest as already due.
- [ ] Define controlled opening-balance migration. Do not invent historical receipts from snapshots or assume every legacy PAID loan has complete transaction history.

### Service And Persistence

- [ ] Add financial entities and an explicit migration without renaming legacy institution/branch/client columns.
- [ ] Use decimal arithmetic with documented precision/rounding; avoid binary-floating-point financial calculations.
- [ ] Derive institution, branch, actor, and ownership from trusted server context. Require dedicated posting/reversal permissions and authorized scope.
- [ ] Validate amount, currency, dates, channel, receiving account, loan state, evidence, and contractual obligations on the server.
- [ ] Store actual payment date separately from posting time. Unique scoped idempotency keys return the original result on retry and reject changed payloads.
- [ ] Allocate confirmed payments using applicable legal order: due interest, outstanding fees/charges, then due principal. Handle the remainder under the approved advance-payment policy.
- [ ] Commit transaction, allocation, balanced journal, balance projection, audit, and outbox atomically. Protect concurrent posting with a narrowly scoped per-loan strategy and bounded retry, never a global lock.
- [ ] Generate unique receipt references. Use linked, authorized reversals with reasons and maker/checker controls where policy requires them.
- [ ] Replace status-only paid totals and refresh placeholders with ledger-backed results. Unknown legacy balances need an unavailable/reconciliation state, not a success message.
- [ ] Set PAID only after valid settlement; reversals restore the correct outstanding/delinquency state without bypassing approval rules.
- [ ] Post top-up source-loan settlement and net disbursement explicitly; do not fabricate a cash receipt for an internal settlement.
- [ ] Add scoped reconciliation and loan-specific payment/receipt views using the shared shell. Keep global destinations in the sidebar and distinguish pending from posted transactions.

### Acceptance Tests

- [ ] Exact, partial, multiple-instalment, and zero-interest payments allocate and reconcile correctly.
- [ ] Remaining cents reconcile; principal, interest, and fees do not go negative from rounding.
- [ ] Duplicate submissions, network retries, and imported duplicates do not create extra money/receipts; conflicting payloads fail.
- [ ] Concurrent postings produce a valid serial-equivalent result without lost balances or duplicate allocations.
- [ ] Every posting/reversal balances the journal and agrees with the loan subledger and receiving-account reconciliation.
- [ ] Reversals retain originals, adjust balances once, restore overdue status when necessary, and cannot apply twice.
- [ ] Backdated and imported payments follow approved effective-date treatment and cannot silently alter closed periods.
- [ ] Overpayments, unmatched funds, advance principal, and full early settlement follow approved policy; future interest is not collected as earned.
- [ ] Wrong-role/institution/branch/client requests fail for posting, lists, exports, and receipts, including forged fields.
- [ ] Ledger failures roll back the posting. Notification failures neither roll back committed money nor cause duplicate receipts.
- [ ] Reports reconcile for partial payments, refunds/reversals, opening balances, and top-up settlements.
- [ ] Existing application, guarantor, review/quorum, disbursement, login/logout, CSRF, and ownership tests still pass.
- [ ] Changed JSP flows work on mobile/desktop and in English/Kiswahili without page overflow, clipped amounts, or misleading success states.

## Delivery Sequence

1. Validate financial policy/schedules; implement ledger, accounting, receipts, portfolio cutover, and reconciliation together. Do not claim reliable repayment tracking before completion.
2. Complete verified client/guarantor assessment, pricing review, consent, and agreements; integrate gates into application/disbursement. These remain production blockers even if ledger work lands first.
3. Complete arrears/collections, approved restructuring/write-off, reconciled reports, credit-information submissions, complaints, and privacy/digital release checks.
4. Expand salary, group, seasonal/fortnightly products and approved external channels. File export/manual submission can precede a CRB API; reporting obligations cannot be deferred because no API is connected.

Do not claim 1,000 requests/second without measurements. Preserve a path toward that target through scoped pagination, indexes, short transactions, no network/file work under posting locks, and configurable batches.

## Regulatory Checkpoints And Research

These checkpoints inform the backlog; review implementation against the actual licence, approved policy, and current official requirements. Forum advice is not a compliance source.

- The [2019 Tier 2 regulations](https://www.bot.go.tz/Publications/Acts%2C%20Regulations%2C%20Circulars%2C%20Guidelines/Regulations/en/2020021122490967551.pdf), regulations 24 and 35-44, cover records, credit sharing, lending policy, applications, agreements, security, repayment, and past-due loans. Regulation 42 requires credit on the payment date and allocation to due interest, then outstanding fees/charges, then due principal. Full early settlement excludes interest for the remaining term; regulations 39 and 55 require borrower/guarantor disclosures.
- The [2026 submission guidance](https://www.bot.go.tz/Publications/Acts%2C%20Regulations%2C%20Circulars%2C%20Guidelines/Guidelines/en/2026092118400398.pdf), Form 1, lending-policy checklist, specifies reducing-balance interest. Sections 19-20 address approval requests for policy/form/agreement changes. The note explicitly does not supersede legislation; this audit does not assert that a new 2026 regulation replaced the verified 2019 regulations.
- The [2024 fees guidelines](https://bot.go.tz/Publications/Acts%2C%20Regulations%2C%20Circulars%2C%20Guidelines/Guidelines/en/2024071716375097.pdf) require a fee-policy review before retaining separate application/processing charges. Verify insurance coverage and actual permitted costs; do not translate legacy configuration directly into new charges.
- The [2024 digital-lender guidance](https://bot.go.tz/Publications/Acts%2C%20Regulations%2C%20Circulars%2C%20Guidelines/Guidelines/en/2024082813141188.pdf) covers platform clearance, pre-application pricing, Kiswahili default, transaction acknowledgements, and data/collections restrictions. Treat applicability/evidence as release gates, not a certification supplied by this conversion.
- [JamiiForums operator discussion](https://www.jamiiforums.com/threads/naomba-ushauri-wa-njia-bora-ya-kuendesha-microfinance.2216327/) helps identify questions about arrears, verification, cash flow, and repayment frequency. Anecdotes are not validated policy; do not adopt suggestions involving ATM PINs, harassment, arbitrary interest, or informal collateral seizure.

## Code Evidence Index

Repository-relative paths keep this checklist portable:

- `src/main/java/com/sacco/mvp/domain/LoanApplication.java`
- `src/main/java/com/sacco/mvp/domain/LoanProductSetting.java`
- `src/main/java/com/sacco/mvp/domain/RepaymentFrequency.java`
- `src/main/java/com/sacco/mvp/domain/Member.java`
- `src/main/java/com/sacco/mvp/service/LoanWorkflowService.java`
- `src/main/java/com/sacco/mvp/service/ManagerService.java`
- `src/main/java/com/sacco/mvp/service/RepaymentScheduleService.java`
- `src/main/java/com/sacco/mvp/service/LoanRepaymentScheduleDisplayService.java`
- `src/main/java/com/sacco/mvp/service/LoanPaymentSummarySyncService.java`
- `src/main/java/com/sacco/mvp/service/EligibilityService.java`
- `src/main/java/com/sacco/mvp/service/LoanQualificationPolicyService.java`
- `src/main/java/com/sacco/mvp/service/LoanAnalyticsService.java`
- `src/main/java/com/sacco/mvp/service/LoanPresentationService.java`
- `src/main/java/com/sacco/mvp/service/SaccoConfigurationService.java`
- `src/main/java/com/sacco/mvp/service/MemberRegistrationService.java`
- `src/main/java/com/sacco/mvp/web/form/MemberRegistrationForm.java`
- `src/main/java/com/sacco/mvp/repository/LoanApplicationRepository.java`
- `src/main/java/com/sacco/mvp/security/AuthzService.java`
- `src/main/java/com/sacco/mvp/config/SecurityConfig.java`

## Verification Record

- [x] Inspected targeted baseline services, entities, controllers/authorization, and shared UI contracts.
- [x] Checked source/build/readme paths for remaining Foresight references; none found in the inspected Java, JSP, message, XML, or Markdown files.
- [x] Added microfinance-specific project agent guidance and local UI skills.
- [ ] Implemented the new repayment/accounting slice.
- [ ] Verified the complete converted application through financial tests, rendered workflows, and operator compliance review.
