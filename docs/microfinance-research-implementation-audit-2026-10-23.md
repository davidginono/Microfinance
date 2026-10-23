# Microfinance research: implementation audit and next steps

Reviewed: 23 October 2026. Source baseline: `69b9179b`.

This review compares the supplied **Research Summary: Microfinance Activities** with current application code, migrations, tests and maintained implementation notes. It covers the ten customer services and eight internal functions described in the research. It is a source review, not a live workflow test, financial reconciliation or compliance certification. Existing test files are evidence of intended coverage; their presence does not establish that they currently pass.

The system already has substantial individual lending, repayment and accounting functionality. The main missing operational work is verified underwriting, collateral lifecycle, collections casework, restructuring, consumer complaint handling and regulatory submissions. Broader products are separate expansion decisions.

## Status and scope

- **Present:** a current implementation was identified for the stated limited capability.
- **Partial:** a current implementation exists, but important parts of the activity are missing.
- **Not found:** no dedicated current workflow was identified in the inspected Java, JSP and migration paths. This is not proof that the institution does not perform the activity outside the software.
- **Retired:** a workflow was deliberately removed; retained tables, migrations or permissions do not make it available.
- **Outside current scope:** requires a separate product/licensing decision, rather than being an unfinished individual-business-loan feature.

Priorities below are engineering recommendations: **P0** protects lending decisions or financial truth; **P1** completes controlled operations; **P2** expands products and automation. They are not statutory deadlines.

The project targets a Tanzania Mainland non-deposit-taking lender. The research describes microfinance broadly and does not authorize every listed activity for this application. BoT's published Tier 2 regulations distinguish permitted services from public deposits and funds transfers requiring authorization; insurance is described as agency for insurers. Confirm the operator's actual licence before expanding services. See [BoT regulations 21–22](https://www.bot.go.tz/Publications/Acts%2C%20Regulations%2C%20Circulars%2C%20Guidelines/Regulations/sw/2020021122490967551.pdf) and the [current BoT regulations listing](https://bot.go.tz/BankSupervision/Regulations).

## 1. The ten activities in the research

| Activity | Current status and evidence | How to implement the missing part |
| --- | --- | --- |
| Giving loans | **Partial overall; core workflow present.** Products, decimal quotations, applications, guarantor decisions, product-selected reviews, disbursement and recorded-loan top-ups exist. See `LoanWorkflowService`, `LoanProductWorkflowService`, `LoanRecordingService` and `LoanAmortizationCalculator`. | Complete the verified assessment, agreement and collateral gates in section 2. Integrate them with both borrower applications and staff-recorded applications; preserve existing product routing and quorum rules. Do not build a second lending engine. **P0.** |
| Collecting savings | **Outside current scope.** `SavingsAccount` is a legacy balance model with available/shares/deposits fields; it is not a deposit transaction and withdrawal service. | Keep public savings collection outside this lender's backlog. If a separately authorized deposit product is ever requested, first establish its legal/operator boundary, then design a deposit ledger, reconciliation, statements and withdrawal controls. Never make savings or shares a condition of the current loan journey. |
| Loan repayment | **Present for supported local-ledger loans; partial across the portfolio and channels.** `LoanRepaymentLedgerService` supports receipts, allocations, partial/advance principal, settlement quotes and linked reversals. Specialized charges, individual/batch receipts and early-interest exclusions also exist. | Preserve these services. Finish verified legacy openings, unmatched/overpaid funds and channel reconciliation only within newly authorized scope. Prove that source loan, receipt and GL totals agree. See sections 2–3. **P0.** |
| Group lending | **Not found as a dedicated product.** Individual guarantors are not a group loan model. | Add a scoped group register, dated membership, authorized group representatives, member assessments, explicit liability agreements and group/member allocation rules. Reuse existing loan posting per approved liability model; prevent counting group and member balances twice. **P2.** |
| Microinsurance | **Partial.** `LoanChargeService` records loan-linked insurance premium/evidence and related accounting. A full policy, cover and claims lifecycle was not found. | Add insurer/product agreements, policy/certificate records, insured persons, cover dates, renewals/cancellations, premium remittance matching and claim tracking. A premium receipt must not imply that cover was issued or a claim paid. Work through an authorized insurer arrangement. **P2; remittance traceability is an earlier dependency if premiums are collected.** |
| Money transfers | **Not found as a customer transfer service; outside current lending scope.** Bank/mobile-money channel labels, payment destinations and GL accounts exist. | First separate loan payment integration from customer-to-customer transfers. For lending channels, add authenticated provider events/imports, an idempotent inbox, matching, settlement and exception handling. A remittance product needs its own authorization, provider agreement, fees, limits, controls and transaction states. **P2 for APIs; accurate manual reconciliation is earlier.** |
| Financial education | **Not found as a dedicated workflow.** An `EDUCATION_LOAN` label funds education; it does not teach financial literacy. | Start with approved English/Kiswahili materials and records of staff-led sessions, delivery, attendance or acknowledgement. Cover the institution's products, budgeting, costs and repayment. Online courses are optional; do not require a new learning platform to record offline education. BoT regulation 57 recognizes multiple delivery methods. **P1.** |
| Microleasing | **Not found.** A generic equipment loan is not a lease. | Add supplier/equipment records, ownership, delivery acceptance, deposits where approved, lease schedules, insurance/maintenance duties, residual value, purchase options, termination and distinct accounting. Reuse calculation components only where their conventions fit the approved lease. **P2.** |
| Housing finance | **No dedicated housing workflow found.** Configurable products can supply general loan terms. | Begin with a housing-purpose product if its approved terms fit existing lending. Add property ownership/valuation/security and construction inspections; implement approved staged disbursement before offering construction tranches. A product name alone does not provide these controls. **P2.** |
| Business support | **Not found as a separate advisory service.** Application business descriptions are assessment inputs. | Add advisor assignments, appointments/visits, agreed actions, referrals, follow-up and outcomes. Link advice to clients without treating advice completion as verified income or credit approval. Reuse the proposed field-visit records where appropriate. **P2.** |

## 2. Internal operations: concrete gaps

### G01 — Verified client onboarding and KYC — partial, P0

**Evidence:** `Member.java` captures identity labels, contact, branch, account access and signatures. `MemberRegistrationForm.java` and `MemberRegistrationService.register()` provide registration, OTP-related inputs and uniqueness checks. They do not establish a structured verified identity/address/business profile. The employee number is optional declared information.

**Implementation:** add institution/branch-scoped client verification and evidence records linked to `Member`; keep the compatibility identifier. Record document type/expiry, address/business evidence, verifier, verification date, result, reason and review due date. Reuse protected upload storage. Extend duplicate checking beyond exact account contact fields using reviewed identity matches; support people without self-service email/password through a staff-assisted onboarding use case.

**Done when:** declarations and verified facts are distinct; required expired/missing evidence blocks the appropriate lending step; client/branch/tenant access tests cover evidence and downloads. Do not infer verification from the existing `verifyExternalMember` method name: its current implementation validates local registration inputs and registry scope.

### G02 — Verified cash flow and existing-debt servicing — partial, P0

**Evidence:** `EligibilityService.java:75–109` reads declared income, expenses and other debt repayments and validates required affordability inputs. Line 92 obtains active principal exposure, but that amount is not used to derive monthly debt service in the affordability calculation. Line 134 explicitly saves `DECLARED_NOT_VERIFIED`. Staff-recorded loans retain business/income/expense/evidence fields in `LoanRecordingService` but these are not a complete verified assessment lifecycle.

**Implementation:** introduce a dated assessment with evidence-backed business/household cash flow, existing internal instalment commitments, declared external debts, verified sources and officer findings. Normalize all repayment periods through the shared calculation conventions. Reconcile declared debt against known internal commitments so the same debt is neither omitted nor deducted twice. Record missing coverage and any authorized exception. Reuse this assessment at application/review/disbursement; changed terms require reassessment.

**Done when:** a borrower with existing instalments cannot qualify solely because they declared zero other debt; missing evidence is visibly unresolved; the recorded assessment reproduces the decision and policy version. Institution-wide exposure aggregation must return only explicitly authorized risk information, without opening other branches' registries.

### G03 — Guarantor financial capacity — partial, P0

**Evidence:** `LoanQualificationPolicyService.guarantorFailureReason()` checks active-loan/default rules and active guarantee counts. The external variant accepts `declaredIncome` but does not use it for a capacity decision. `guarantorMaxGuaranteedLoanAmount` is currently interpreted through `positiveGuaranteeCount`, so it must not be silently reinterpreted as money.

**Implementation:** add clearly separate count limits, monetary liability limits and verified disposable-income/commitment checks. Retain each guarantor's agreed liability, existing guarantees, evidence and approved policy. Recheck mutable capacity at the commitment point under narrow concurrency protection. Preserve informed approval, OTP/signature evidence and limited guarantor disclosure.

**Done when:** two simultaneous guarantees cannot exceed an approved capacity limit; count and TZS limits are distinct; a signed approval does not substitute for capacity verification.

### G04 — Collateral lifecycle — declarations present; register not found, P1

**Evidence:** `EligibilityService` checks a declared collateral description/value against product requirements. Staff recording stores collateral text and attachments. No dedicated asset ownership/valuation/custody/release workflow was identified.

**Implementation:** add a collateral register linked to clients and loans, with ownership evidence, valuation history/valuer, approved security documents, encumbrances, custody, revaluation, release and recovery decisions. Prevent the same asset from being pledged beyond policy limits. Legal perfection remains a recorded verified fact, not a checkbox inferred from an uploaded photo.

**Done when:** staff can trace each required security from assessment to release and see previous pledges and valuation evidence.

### G05 — Agreement acceptance and complete pricing disclosure — partial/unverified, P0

**Evidence:** `LoanApplication` and `LoanWorkflowService` retain signature fields and financial/policy snapshots. Recorded charge terms retain agreement/premium evidence and existing-loan terms are frozen. No dedicated complete agreement-template/version/acceptance workflow was identified in this review; this is not a finding that existing PDFs contain no disclosures.

**Implementation:** audit current borrower/guarantor documents first. Bind each acceptance to the exact agreement/template version, language, principal, rate/time basis, fees, repayment and early-payment rules; retain the accepted artifact/checksum, signer, time and delivery evidence. Any annualized cost measure must use an approved calculation definition and include relevant charges. Gate funding on the approved required evidence across both origination paths.

**Done when:** later product/document edits cannot change what a borrower accepted, and terms in acceptance, approval, disbursement and ledger agree.

### G06 — Instalment reminders and collection casework — partial, P1

**Evidence:** `LoanPortfolioRiskStatusService.decideStatus()` uses local ledger overdue balances when available. `RepaymentReminderScheduler.java:49–85` queries `DISBURSED` loans using final due dates and builds maturity reminders. No dedicated assigned collection case, contact timeline, promise-to-pay or dispute workflow was found.

**Implementation:** select unpaid ledger instalments by due date, account for partial payments/rebates and include relevant overdue states. Create collection cases with officer, outstanding components, contact outcomes, next action, promises, disputes and controlled notices. Publish bounded, idempotent notifications after commit; use the application business clock. Promises and follow-up actions must never post money or mark a loan settled.

**Done when:** a missed weekly instalment creates follow-up before final maturity; payment/reversal updates arrears correctly; repeated scheduler runs do not duplicate contacts. Retain recovery evidence and authorized access.

### G07 — Restructuring and recovery — partial servicing; restructuring not found, P1

**Evidence:** `LoanAdjustmentService` implements scoped provisions/releases, eligible principal write-offs, write-backs and untouched disbursement reversals. `LoanPrepaymentService` implements early-payment servicing and interest waivers. These do not establish a restructuring workflow.

**Implementation:** add a proposal with reason, current contractual debt, payment history, revised affordability, proposed schedule/terms, borrower acceptance and required independent decisions. Preserve the old contract and effective-date boundary; use explicit linked adjustments and servicing obligations. Extend post-write-off recovery only through approved accounting treatment; reuse existing write-back/receipt capabilities where sufficient.

**Done when:** prior schedules and decisions remain reproducible; write-off does not forgive contractual debt; restructure/reversal/recovery cannot collect principal or interest twice.

### G08 — Consumer complaints and privacy operations — incidents present; dedicated workflow not found, P1

**Evidence:** `AdminIncident` records category, severity, status, notes and resolution. It does not itself provide a client complaint lifecycle with accountable assignment, response due dates, escalation and appeal. Operational cleanup and protected financial audit history exist, but a complete client consent/privacy request lifecycle was not identified.

**Implementation:** add client-visible complaint submission/tracking and staff handling with ownership, received/acknowledged dates, policy-based deadlines, evidence, responses, escalation, appeal and closure. Add versioned purpose-specific consent and privacy request handling with retention/legal-hold rules. Reuse incident infrastructure only where scope and lifecycle are appropriate. `AdminIncident` has no first-class `stationId`; define trusted branch ownership before exposing a branch complaint registry.

**Done when:** clients can see their own cases, staff cannot browse foreign cases, deadlines and decisions are auditable, and privacy actions preserve mandatory financial history. Confirm applicable requirements rather than hardcoding unverified deadlines.

### G09 — Credit information and regulatory submissions — reports present; submission workflow not found, P1

**Evidence:** current portfolio/management reports and the new statement workspace exist. Dedicated active CRB submission/acknowledgement/correction code was not identified. Old regulatory submission records are retained in `accounting_archive` and are not an active submission service.

**Implementation:** establish current approved recipient formats and definitions; retain required consent/basis, validate scoped data, freeze submission snapshots, independently check them and record delivery, acknowledgement, rejects and linked corrections. Start with a controlled file/manual-delivery workflow when appropriate; an API is optional. Obtain separate output-format authorization where existing export restrictions apply.

**Done when:** each sent figure traces to a dated supported source; a rejected submission is visible; a correction preserves what was previously sent. A management risk bucket must not masquerade as a regulator-approved classification or return.

### G10 — Risk and internal audit case management — partial, P1

**Evidence:** local credit/default/guarantee rules, risk analytics, audit logs, explicit claims and protected financial corrections exist. A complete investigation/finding/remediation workflow was not identified.

**Implementation:** add policy-approved risk indicators and cases for duplicate identities, unusual adjustments, concentration or repeated reversals; distinguish an alert from a proven finding. Add reviewer assignments, evidence, action plans, follow-up dates and closure verification. Reuse permanent audit/outbox patterns and minimal scoped aggregates; do not invent an opaque credit score or label unavailable bureau history clean.

**Done when:** an auditor can trace a finding to source records and verify remediation without changing the original financial evidence.

### G11 — Salary and other repayment products — partial foundations, P2

**Evidence:** payroll employer/instruction records, frozen deductions, PDF/Excel output, feedback imports and verified remittance preparation exist in `PayrollService` and `PayrollFeedbackService`. Posting still goes through loan receipt services. `RepaymentFrequency` supports only `WEEKLY` and `MONTHLY`.

**Implementation:** for salary lending add verified employment/payslips, salary-wide commitments and applicable deduction limits, consent validity and employer exit/short-payment handling. For fortnightly, seasonal or agricultural lending add actual frequency/date and seasonal cash-flow models, appropriate risks and approved schedules. A twelve-month tenure field or payroll export is not full salary underwriting.

**Done when:** expected employer deductions never become receipts before confirmed funds are posted; new schedule models preserve old contracts and exact principal reconciliation.

### G12 — English/Kiswahili onboarding — partial, P1

**Evidence:** bilingual message bundles and many localized workflows exist. `MemberRegistrationService.java:72` defaults new accounts to English. `MemberRegistrationForm` still contains English-only validation text and labels referring to SACCO/station IDs; `LoanType` retains some legacy descriptions.

**Implementation:** review the applicable digital/language policy, localize registration and error paths, use Institution/Branch/Client labels, and retain compatibility identifiers internally. Verify disclosures, receipts and recovery flows in both languages on mobile and desktop. Language changes must preserve the accepted document's original language/version.

**Done when:** the complete selected-language journey, including server validation failures and downloaded documents, is consistent. A system-wide branding rewrite is unnecessary.

## 3. Accounting and reporting: preserve implemented work and respect the reset

The 1 October conversion checklist contains historical absence statements. They no longer accurately describe the full current code. Do not use its unchecked boxes as proof that repayment posting or journals still need to be built.

| Capability | Current position | Missing work and implementation boundary |
| --- | --- | --- |
| General ledger, vouchers and accounts | **Present foundations:** `GeneralLedgerService`, `VoucherService`, versioned COA, product-account links and configured voucher approval workflows. | Reuse balanced posting, explicit claims, history and linked corrections. Preserve direct posting where configured approval is disabled. Do not add another official book or a universal approval path. |
| Authoritative loan balances | **Present for supported loans; incomplete historical coverage.** Local ledgers open in disbursement transactions; unsupported legacy loans need reconciliation. | Design reviewed source openings/cutover only after explicit new authorization. Validate principal, due interest, charges, dates and source control totals. Preserve unknowns rather than manufacturing receipts. **P0, authorization-dependent.** |
| Bank/mobile-money and physical cash reconciliation | **Partial diagnostics.** `ReconciliationService` supplies a read-only SL/GL discrepancy report. This is not independent provider/cash evidence or a reconciliation decision workflow. | The previous workflow was retired. If reauthorized, add bounded statement imports, provenance, duplicate controls, independent matching, cash counts, assigned exceptions and signed-off reconciliation. Imports must not automatically create money. **P0, authorization-dependent.** |
| Complete financial statements | **Present recorded-ledger statements:** position, profit/loss, cash flow, equity, notes, trial and ledger drill-down; saved figures and PDF/print/Excel output. | Current outputs are explicitly **Provisional — recorded ledger only**. Verified full openings, recognition and reconciliation remain unresolved. Saving a snapshot must not certify complete accounts. Extend completeness/finalization only after those dependencies and any retired workflow are explicitly authorized. **P0/P1.** |
| Interest/fee accounting | **Partial.** Collections and frozen charge terms exist. Contractual earned-interest servicing is separate from GL recognition. | Obtain approved accrual/recognition treatment, add only the required posting sources and reconcile them to independent obligations. Financed fees and insurer remittance lifecycle remain separate work. Never book future scheduled interest as received cash. **P0/P1.** |
| Period close/reopen, source openings, suppliers/business entries, assets and accounting release gates | **Retired on 15 October.** Closed-date posting protections and retained history survive. | These are current capability gaps, but the prior request expressly requires new instructions before recreating them. For a future approved slice, specify opening proof, close/post concurrency, invoice/payment separation or asset lifecycle as appropriate. Generic voucher entry is not a supplier or asset subledger. |
| Operational report customization | **Historical schema foundation; no active general builder found.** V46/V49 retain template/run structures; current code references them for retention. Current statement mappings/settings cover a narrower task. | First decide whether to restore/reuse the old foundation under current scope. Then implement approved datasets/fields, typed filters, safe layouts, versioned publication, scoped preview/export and bounded execution. Do not expose SQL or revive old output formats by accident. **P2; coordinate with the accounting reset.** |

Read [accounting-reset.md](accounting-reset.md), [accounting-report-builder-checklist.md](accounting-report-builder-checklist.md), [financial-statement-workspace.md](financial-statement-workspace.md) and [sl-gl-reconciliation.md](sl-gl-reconciliation.md) together. Later narrow restorations do not reactivate all removed accounting workflows.

## 4. Recommended implementation sequence

1. **Verified borrower and guarantor assessment:** G01–G03; retain evidence and traceable debt service, then apply gates across existing origination/review/funding paths. Audit agreements in parallel through G05. The first reviewable slice should cover one individual-business product end to end.
2. **Financial completeness decisions:** settle approved pricing/recognition and request scope for legacy openings/channel reconciliation. Reuse the existing ledger and provisional reporting; keep unsupported coverage visibly unavailable while this proceeds. These decisions can progress alongside assessment work.
3. **Servicing and consumer operations:** G04, G06–G08 and G12, plus basic education delivery records. Deliver instalment reminders/collections before building additional lending products.
4. **Risk and reporting operations:** G09–G10, reconciled financial coverage, institution review and any newly authorized accounting lifecycle work. Submission APIs can follow controlled manual delivery.
5. **Product expansion:** G11, group/housing/leasing/insurance services and business advisory tools. Build a general report designer only when its datasets and scope are settled. Keep savings and standalone transfer services behind a separate explicit scope/licensing decision.

This sequence is a proposed backlog, not authorization to implement removed accounting features, change seeded credit rules or introduce new schema in this review.

## 5. Implementation architecture and acceptance gates

**Layers:** keep the Spring Boot 4.1.0 / Java 25 modular monolith. Add each approved capability as a complete controller/request DTO → service → scoped repository → model/migration slice, with JSP view models and the existing shared shell. Reuse the calculator, ledger, document storage, audit, notification and outbox services. No frontend framework or microservice migration is needed.

**Security:** preserve session authentication and CSRF. Require explicitly assigned operation claims and service-enforced institution/branch/client scope for screens, commands, exports and evidence. Use `LoanApplication.stationId` for historical loan branch ownership. A report template or risk aggregate must not grant cross-branch registry access.

**Database/query design:** add forward migrations only for approved new records. Keep evidence and financial history immutable where required; make corrections linked events. Use bounded paged registers and indexed scoped aggregates, not institution-wide `findAll()` loading. Review date semantics, retention and first-class branch references before adding a feature.

**Concurrency and background work:** use scoped request keys, uniqueness and narrow record locks for monetary or capacity commitments; preserve transactional rollback of journal/subledger/audit writes. Run notifications after commit, and process imports/reminders/reports in bounded batches. Background work must recheck its authorized scope and avoid network/file work under posting locks.

For each completed slice require:

- Allowed/forbidden claim, tenant, branch and client/evidence tests, including forged identifiers and revoked permissions.
- Decision evidence tests: declared versus verified, stale assessments, missing data and concurrent guarantee commitments.
- Financial tests where relevant: partial/advance/early payment, exact rounding, duplicate and changed retries, linked reversal, concurrent posting, backdated dates, failed-audit rollback and loan/GL reconciliation.
- English/Kiswahili JSP and browser checks at 360, 768, 1366 and 1920 pixels, including validation recovery, downloads and existing login/logout/navigation.
- Updated focused tests, appropriate PostgreSQL integration, compile/package checks and a local scoped commit. Record actual results and skipped coverage.
- Representative query/load measurements before any throughput claim; 1,000 requests/second remains unproven by this review.

## 6. Review evidence and limitations

Inspected the supplied research, `AGENTS.md`, `pom.xml`, both principal checklists, the accounting reset and current increment notes. Read targeted entities/services/controllers for registration, eligibility, guarantors, application/recording, repayments, reminders, risk statuses, adjustments, charges, statements, reconciliation, payroll and retained accounting history. Searched current Java/JSP/migration paths for dedicated group, collateral, education, leasing, housing, complaint, restructuring, credit-bureau, transfer and template workflows. Active code was distinguished from historic migrations and archived records.

Key source anchors:

- `src/main/java/com/sacco/mvp/domain/Member.java`
- `src/main/java/com/sacco/mvp/web/form/MemberRegistrationForm.java`
- `src/main/java/com/sacco/mvp/service/MemberRegistrationService.java`
- `src/main/java/com/sacco/mvp/service/EligibilityService.java`
- `src/main/java/com/sacco/mvp/service/LoanQualificationPolicyService.java`
- `src/main/java/com/sacco/mvp/service/LoanWorkflowService.java`
- `src/main/java/com/sacco/mvp/service/LoanProductWorkflowService.java`
- `src/main/java/com/sacco/mvp/accounting/loans/service/LoanRecordingService.java`
- `src/main/java/com/sacco/mvp/service/LoanRepaymentLedgerService.java`
- `src/main/java/com/sacco/mvp/accounting/repayment/service/LoanPrepaymentService.java`
- `src/main/java/com/sacco/mvp/service/RepaymentReminderScheduler.java`
- `src/main/java/com/sacco/mvp/service/LoanPortfolioRiskStatusService.java`
- `src/main/java/com/sacco/mvp/accounting/adjustments/service/LoanAdjustmentService.java`
- `src/main/java/com/sacco/mvp/accounting/charges/service/LoanChargeService.java`
- `src/main/java/com/sacco/mvp/domain/AdminIncident.java`
- `src/main/java/com/sacco/mvp/accounting/statements/service/StatementService.java`
- `src/main/java/com/sacco/mvp/accounting/reconciliation/service/ReconciliationService.java`
- `src/main/java/com/sacco/mvp/accounting/payroll/service/PayrollFeedbackService.java`
- `src/main/java/com/sacco/mvp/accounting/repository/RetainedFinancialHistoryRepository.java`
- `src/main/resources/db/migration/V46__operational_report_templates.sql`
- `src/main/resources/db/migration/V49__durable_report_execution.sql`
- `src/main/resources/db/migration/V63__retire_accounting_workflow_schema.sql`

No application, business-rule, database or UI changes were made. No production datasource was accessed, and no Maven, browser, integration or load tests were run for this documentation-only audit. Validation for this deliverable is limited to source-reference existence and Git whitespace/diff review. Complete runtime behavior, current institutional policy approvals and historical financial completeness remain to be verified during the relevant implementation slices.
