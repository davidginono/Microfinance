# Microfinance research: current implementation gaps and delivery plan

KYC implementation update (27 October 2026): G01 now has staff-assisted individual-client onboarding, scoped versioned evidence, independent review, duplicate identity checks and branch-configured submission/funding enforcement. See [activation, supported scope and verification](client-onboarding-kyc.md). This does not establish live identity-provider verification or complete organization due diligence, privacy-request handling, retention disposal or underwriting.

Implementation update (27 October 2026): ordinary/external guarantor assessments, registered collateral and ledger-backed collections now have scoped implementations. See [activation, supported boundaries and verification](credit-capacity-collateral-collections.md). The findings below remain the preceding audit record; other underwriting, legacy cutover, document automation and recovery gates are not marked complete by this increment.

Reviewed: 27 October 2026. Final source baseline: `129cb547`.

This source review compares the supplied **Research Summary: Microfinance Activities** with current Java/JSP code, migrations, tests and maintained delivery notes. It updates the findings in [the 23 October audit](microfinance-research-implementation-audit-2026-10-23.md), especially group lending. It proposes implementation work; it does not implement features or certify institutional compliance or production readiness.

**Main finding:** lending, local repayments, vouchers, financial report previews and three group-lending models already have substantial implementations. The largest unfinished operational capabilities are verified underwriting, collateral management, general collections casework, restructuring, complaints, regulatory submission and full financial coverage. Savings and standalone money transfers are separate scope decisions.

## How to read the status

- **Implemented within a supported boundary:** active code exists for the stated use case; this review did not rerun its tests.
- **Partial:** useful code exists, but the named missing controls or lifecycle still need work.
- **Not found:** no dedicated active workflow was identified in the inspected source. An upload, label, historical migration or generic voucher does not establish that workflow.
- **Retired:** previously removed at the user's request. The historical checklist is not permission to restore it.
- **Outside current scope:** a separate product/operator authorization decision is needed.

P0 means protect credit decisions or financial truth; P1 means complete controlled operations; P2 means product expansion or broader automation. These are proposed engineering priorities, not regulatory deadlines.

## 1. All ten activities in the research

| Activity | Current position | Missing work and how to implement it |
| --- | --- | --- |
| Giving loans | **Implemented core; partial operational completeness.** Products, decimal quotes, applications, guarantors, product-selected reviews, funding and supported recorded-loan top-ups exist. | Complete verified onboarding, cash flow, guarantor capacity, collateral and accepted-contract evidence across existing application and staff-recording paths. Reuse the current workflow and calculator. See G01–G05. **P0/P1.** |
| Collecting savings | **Outside current non-deposit-taking scope.** `SavingsAccount` retains legacy available/shares/deposits balances; it is not a deposit/withdrawal transaction service. | Keep public savings collection outside the current lending backlog. A separately authorized product would require a deposit ledger, withdrawal controls, statements and reconciliation. Never add savings/share prerequisites to lending. |
| Loan repayment | **Implemented for supported ledger-backed loans; partial portfolio/channel coverage.** Partial and advance-principal payments, early settlement, receipts, charges, batches and linked independent corrections exist. | Extend only approved boundaries: verified legacy cutover, unmatched/excess funds, refunds and independent channel settlement. Preserve exact allocation, original contracts and append-only history. See G11–G12. **P0.** |
| Group lending | **Implemented within supported models.** Shared debt with member attribution; individual debts with directed guarantees; organization debt with authorized representatives. Registry, dated membership, frozen cycles, agreements, funding, collections, coverage cases, corrections and statements exist. | Do not rebuild group lending. Complete approved institutional configuration and acceptance, then extend only separately requested capabilities such as provider confirmation, unmatched funds or seasonal schedules. Reuse existing capacity, disclosure and casework patterns where appropriate elsewhere. |
| Microinsurance | **Partial.** Loan-linked premium terms, insurer/evidence checks and premium accounting exist. | Add policy/certificate issuance and status, coverage dates/persons, renewals/cancellations, insurer remittance matching and claims. Link premiums to insurer-confirmed cover; payment alone must not imply cover or a paid claim. **P1 for traceability if premiums are collected; P2 for expansion.** |
| Money transfers | **Standalone customer transfer service not found; outside current lending scope.** Payment details and bank/mobile-money channel labels exist. | Separate loan-channel integration from a transfer product. For lending, add authenticated provider events/imports, durable duplicate control, matching and settlement exceptions. Standalone remittances require their own scope, agreements, limits, fees and lifecycle. **P2 for APIs; financial reconciliation is earlier.** |
| Financial education | **Dedicated workflow not found.** An education-loan product label is not financial literacy delivery. | Add approved English/Kiswahili materials, session/delivery records, attendance or acknowledgement, facilitator and review dates. Start with staff-led/offline delivery records; an online course platform is optional. **P1.** |
| Microleasing | **Dedicated workflow not found.** An equipment loan does not establish lease ownership or accounting. | Add equipment/supplier records, ownership and delivery acceptance, approved lease schedules, maintenance/insurance duties, residual value, purchase option and termination/recovery treatment. **P2.** |
| Housing finance | **Generic product foundation; dedicated controls not found.** | Start with approved housing terms, then add property ownership/security, valuations and inspections. Construction lending needs controlled staged funding before offering tranches. **P2.** |
| Business support | **Dedicated advisory workflow not found.** Application business descriptions are credit inputs. | Add advisor assignment, appointments/visits, agreed actions, referrals, follow-up and outcomes. Advice is not evidence that income was verified. **P2.** |

Group evidence: [completion audit](group-lending-completion-audit.md), `grouplending/service/GroupCycleService.java`, `GroupOrganizationService.java`, `GroupSharedLoanService.java`, `GroupCoverageService.java` and `GroupDisclosure.java`. The 23 October statement that dedicated group lending was absent is superseded. Group financial-capacity reservations and immutable agreement evidence also mean the older general guarantor/agreement findings cannot be applied to every group path unchanged.

## 2. Internal functions: concrete implementation slices

### G01 — Verified client onboarding and KYC — partial, P0

**Existing evidence:** `domain/Member.java` and `service/MemberRegistrationService.java` provide identity labels, contact, branch, account access, phone verification/signatures and account uniqueness checks. They do not establish a structured verified identity/address/business lifecycle.

**Implement:** add verification/evidence records linked to the existing client identifier, with first-class institution/branch scope, document type/expiry, verifier, date, result, reason and review due date. Reuse protected upload storage. Add staff-assisted onboarding and reviewed duplicate-identity matching where the approved policy requires them. Preserve declared versus verified status and explicit unavailable data.

**Done when:** expired/missing required evidence blocks the appropriate product step; staff can trace who verified each fact; evidence and downloads pass foreign-tenant/branch/client denial tests. Contact OTP is not business or income verification.

### G02 — Verified cash flow and complete debt service — partial, P0

**Existing evidence:** `service/EligibilityService.java` validates required income/expense/debt inputs and normalized repayment, but records `DECLARED_NOT_VERIFIED`. It now includes a lower bound from `SharedCreditExposureService.monthlyPayment()`; that repository reads the shared-facility obligation projection. Ordinary active principal exposure is also reported, but principal is not monthly debt service. `LoanRecordingService` retains assessment/evidence fields and incorporates shared obligations on the relevant path.

**Implement:** create a dated evidence-backed assessment with business and household cash flow, ordinary individual-loan instalment commitments, shared obligations, guarantees and external debts. Reconcile declarations against known obligations with an explicit rule that avoids omission and double counting. Record coverage, policy version, officer findings and approved exceptions. Recheck stale assessments and changed terms before funding through each origination path.

**Done when:** known ordinary instalments cannot disappear because the borrower declares zero other debt; a shared payment is counted once; missing history remains unresolved; the stored inputs reproduce the decision. Institution-wide risk aggregation must not expose other branches' registries.

### G03 — Guarantor capacity consistently across products — partial, P0

**Existing evidence:** ordinary `LoanQualificationPolicyService.guarantorFailureReason()` enforces active-loan/default/count rules. Its external variant accepts `declaredIncome` without using it for a financial-capacity decision. The legacy amount-named setting is interpreted as a count. Newer `GroupCycleService` and `GroupPersonalGuaranteeService` already enforce monetary capacity reservations, existing commitments and serialization for their supported paths.

**Implement:** extend/reuse the supported commitment and capacity approach for ordinary/external guarantees; keep count, maximum liability and verified disposable-income limits distinct. Record assessment evidence, validity and agreed liability, and recheck mutable commitments under narrow locking at approval. Preserve group safeguards and limited guarantor disclosure. Do not silently reinterpret the legacy count field as TZS.

**Done when:** simultaneous guarantees cannot exceed the approved budget, existing commitments are included and count/money limits have separate regression tests. Signature/OTP acceptance is not financial verification.

### G04 — Collateral register and security lifecycle — declarations only, P1

**Existing evidence:** `EligibilityService` checks description/value against product coverage; recording and group assessment retain collateral inputs. No dedicated ownership/valuation/custody/release register was identified.

**Implement:** add assets linked to clients and loans, ownership evidence, valuation history/valuer, encumbrances, approved security documents, custody, revaluation, release and recovery decisions. Detect duplicate or excessive pledges according to approved policy. Record legal verification separately from document upload.

**Done when:** a required security can be traced from assessment through release, including its other pledges and valuation evidence.

### G05 — Accepted agreements and pricing disclosure across all paths — partial, P0

**Existing evidence:** ordinary loans retain financial/policy snapshots and signatures. Group organization/shared/personal-guarantee paths additionally retain source documents, checksums, language, acceptance and delivery evidence. `GroupDisclosure` freezes configurable requirements and validates supplied, evidenced annual-cost percentages; it does not calculate a general APR.

**Implement:** first audit ordinary borrower/guarantor PDFs and acceptance records against the approved requirements. Fill only the gaps: exact accepted document/version/hash/language, principal, rate/time basis, dated fees, repayment and early-payment terms, signer/time and delivery. Reuse established group evidence patterns without replacing historical contracts. Any cost calculation needs an approved methodology and dated charges.

**Done when:** later template/product edits cannot change accepted terms; acceptance, approval, disbursement and ledger agree. Keep institution policy approval distinct from an application's staff approval assertion.

### G06 — Instalment reminders and general collections casework — partial, P1

**Existing evidence:** ledger-backed arrears and risk statuses exist. `RepaymentReminderScheduler` selects `DISBURSED` loans by final due dates, rather than every unpaid instalment. Group coverage already records served notices, promises, responses and actual posted-payment links; it is a specialized liability workflow.

**Implement:** select unpaid due instalments with partial payments, exclusions and waivers accounted for. Add scoped collection assignments, contact outcomes, next actions, promises, disputes and approved notices for general loan servicing. Reuse suitable group casework patterns while retaining their separate liability semantics. Use the application business clock and bounded, idempotent, post-commit notification work.

**Done when:** a missed weekly instalment creates follow-up before maturity; later payments/reversals update arrears; retries do not duplicate notices. A promise or employer deduction request must never post money.

### G07 — Restructuring and broader recovery — restructuring not found; adjustments present, P1

**Existing evidence:** `LoanAdjustmentService` provides eligible provisions, write-offs, write-backs and disbursement reversals; `LoanPrepaymentService` handles supported early-payment servicing/waivers. These are not a restructuring contract lifecycle.

**Implement:** add a proposal with reason, current debt, payment history, revised verified affordability, proposed dated obligations, borrower acceptance and authorized decisions. Preserve the prior contract and schedule; introduce explicit effective-date and linked accounting treatment. Specify post-write-off recovery treatment before extending it.

**Done when:** original/revised obligations remain reproducible, transitions cannot collect amounts twice and write-off is not treated as automatic forgiveness.

### G08 — Client complaints and privacy operations — dedicated lifecycles not found, P1

**Existing evidence:** `domain/AdminIncident.java` supports incidents and resolution notes. Group agreements contain complaint contacts. Neither establishes client case submission, accountable handling deadlines, response, escalation and appeal. `AdminIncident` has no first-class branch reference.

**Implement:** add an institution/branch-owned complaint case with own-client submission/tracking, assignment, received/acknowledged dates, approved deadlines, evidence, responses, escalation, appeal and closure. Establish trusted branch ownership before reusing incident records. Add purpose/version-specific consents and privacy requests with approved retention/legal holds.

**Done when:** clients see only their cases, staff access is scoped, deadlines/decisions are auditable and privacy actions preserve required financial history. Confirm applicable deadlines rather than inventing them.

### G09 — Credit information and regulatory submissions — active lifecycle not found, P1

**Existing evidence:** management/portfolio reports and statements exist. `RetainedFinancialHistoryRepository` protects old archived regulatory submissions; retained history is not an active submission service.

**Implement:** establish current approved recipients, schemas, definitions and consent/legal basis. Validate scoped source data, freeze each submission, record checking/delivery/acknowledgement/rejects and preserve linked corrections. A controlled file/manual-delivery workflow can precede an API. Obtain required output-format authorization separately from existing PDF/print/Excel exceptions.

**Done when:** every sent amount traces to its source and cutoff, rejects remain visible and corrected submissions preserve the original. Management risk buckets are not automatically regulator-approved returns.

### G10 — Risk investigations and internal audit workpapers — partial, P1

**Existing evidence:** credit rules, risk reports, audit logs, access claims and linked financial corrections exist. A dedicated finding/investigation/remediation lifecycle was not identified.

**Implement:** add approved scoped indicators and cases, reviewer assignments, evidence, findings, actions, due dates and independent closure verification. Use bounded aggregates for concentration/exception analysis; retain alert-versus-confirmed-finding status.

**Done when:** a reviewer can reproduce an alert, substantiate a finding and verify remediation without altering source transactions. Do not infer a clean credit record from unavailable bureau data.

### G11 — Legacy balances, independent settlement and excess funds — partial/retired boundaries, P0

**Existing evidence:** `LoanRepaymentLedgerService` and `LoanRecordingService` own supported posting/corrections. `ReconciliationService` provides read-only SL/GL and member-attribution discrepancies. Matching internal books do not prove bank/mobile-money settlement or a physical cash count. Legacy opening/import and reconciliation decision workflows were retired.

**Implement after the required new scope decision:** reviewed source openings with provenance and control totals; statement imports or provider events with duplicate controls; matching, cash counts, assigned exceptions and sign-off. Design suspense/unapplied amounts, refund and correction states before accepting unmatched or excess cash. Preserve owning ledger/GL posting and explicit channel evidence.

**Done when:** supported loan, receipt, GL and independent money-source totals reconcile; unverified coverage remains unavailable; imports never silently invent repayment receipts. Do not restore retired workflows merely to tick the historical checklist.

### G12 — Complete accounting, statements and report customization — partial/retired boundaries, P0/P1

**Existing evidence:** GL, multi-row vouchers, configured approvals, versioned COA, product-account links, charges and supported loan adjustments exist. `StatementService` freezes recorded-ledger figures and exports; `StatementCalculator` always includes a coverage check. Current messages say **Draft** and ask staff to check starting balances/payments. Safe report previews are implemented; complete financial coverage is not established. Historical template/run tables do not provide an active general operational builder.

**Implement:** approve required recognition/accrual and impairment treatment, reconcile openings and money sources, and add only newly authorized posting/finalization controls. A general report builder needs allowlisted datasets/fields, typed filters, safe bounded layouts, versioned publication and protected runs/downloads. Existing statement mappings cover a narrower task. Preserve existing output permissions and format restrictions.

**Done when:** accepted statements reconcile to verified complete sources; reports reproduce their dated definitions and never alter financial meaning. Period management, source openings, supplier/business-entry/asset lifecycles and release gates remain retired unless explicitly requested again. Generic vouchers are not supplier invoices or an asset register.

Read [accounting reset](accounting-reset.md), [accounting/report checklist](accounting-report-builder-checklist.md), [statement workspace](financial-statement-workspace.md) and [SL/GL report](sl-gl-reconciliation.md) together. Later narrow restorations do not restore every old capability.

### G13 — Complete English/Kiswahili journeys — partial, P1

**Existing evidence:** bilingual bundles and group disclosures/documents exist. `MemberRegistrationForm` still has hardcoded English validation and SACCO/station wording; registration creates English preferences.

**Implement:** localize registration/error recovery and Institution/Branch/Client labels; review the applicable default-language policy. Test complete selected-language journeys, including documents and failures, while retaining accepted historical contract language and compatibility identifiers.

**Done when:** client onboarding, acceptance, repayments, complaints and recovery work coherently in both languages and at mobile widths.

### G14 — Additional repayment/product capabilities — partial foundations, P2

**Existing evidence:** payroll employers, instructions, frozen PDF/Excel schedules, feedback imports and verified remittance preparation exist. They delegate actual receipt posting; they are not full salary underwriting. `RepaymentFrequency` supports only `WEEKLY` and `MONTHLY`.

**Implement:** salary products need verified employment/income, salary-wide commitments, applicable limits, consent expiry and employer exit/short-payment handling. Fortnightly/agricultural/seasonal lending needs real dated schedules, seasonal cash flow, policy and risk evidence. Reuse the shared decimal calculator where appropriate and version new conventions.

**Done when:** employer expectations never become cash receipts before posting confirmed funds; new dates/frequencies reconcile principal exactly and preserve old contracts.

## 3. Mapping the eight internal functions in the research

| Internal function | Assessment and implementation work |
| --- | --- |
| Customer management | Registration exists; complete verified profiles, evidence, consents and complaint/privacy operations: G01, G08, G13. |
| Loan management | Core individual and group workflows exist; close assessment/security/agreement and restructuring gaps: G02–G05, G07. |
| Credit management | Declared affordability and shared exposure exist; complete evidence-backed total debt and consistent guarantor capacity: G02–G03. |
| Collections | Receipts, arrears and group coverage exist; add instalment reminders and general assigned cases: G06–G07. |
| Accounting | Posting, accounts, charges and corrections exist; resolve full financial coverage and separately authorized retired capabilities: G11–G12. |
| Risk management | Rules/analytics exist; add verified inputs and investigation/remediation: G02–G03, G10. |
| Reporting | Portfolio/management/statement outputs exist; add approved submissions, complete source coverage and any requested general builder: G09, G11–G12. |
| Compliance and audit | Claims, audit trails and protected corrections exist; complete accountable complaints/privacy, regulatory delivery and audit workpapers: G08–G10. |

## 4. Recommended delivery order

1. **Verified individual-business lending:** G01–G03, with the ordinary agreement audit G05 in parallel. Deliver one product end to end through client application, staff recording, review and funding. Reuse newer group evidence/capacity controls where they fit.
2. **Servicing and customer protection:** G04, G06–G08, G13 and basic financial-education records. Extend general collections while preserving the already implemented group coverage workflows.
3. **Financial completeness:** obtain the necessary scope/policy/opening decisions for G11–G12; these decisions can progress alongside steps 1–2. Keep incomplete financial coverage visible in the meantime.
4. **Risk and submissions:** G09–G10, insurer remittance traceability where premiums are collected, and institutional acceptance of reconciled outputs.
5. **Approved expansion:** full insurance lifecycle, G14, housing, leasing, business advisory and a general report builder. Savings and standalone transfers need separate scope decisions.

Do not start by rewriting loan origination, repayment, accounting or group lending. The first practical development task is a verified assessment slice linking a client verification record to reproducible cash flow and known debt service, with explicit gates on the existing routes.

## 5. Architecture, security and acceptance requirements

**Architecture/layers:** retain Spring Boot 4.1.0, Java 25, JSP and the modular monolith. New approved slices use controller/request DTO → owning service → scoped repository → model/migration, with view DTOs. Reuse calculator, ledger, protected uploads, audit and outbox. Do not add a parallel financial engine or a frontend migration.

**Security:** session authentication and CSRF remain. Explicitly assign operation claims; enforce institution, branch, record and owner scope in services and downloads. Prefer `LoanApplication.stationId` for historical loan scope. Aggregate risk permission must not open cross-branch transaction registries.

**Database/queries:** new records need approved forward migrations, scope references, bounded pages and indexed aggregates. Preserve exact decimals and immutable financial/acceptance evidence. Unknown amounts are not zero. Reuse legacy identifiers and avoid institution-wide list loading.

**Threading/concurrency:** monetary and capacity commitments need scoped idempotency, uniqueness and narrow locks; journal/subledger/audit changes must roll back together. Keep imports and scheduled jobs bounded and observable, and move notification delivery after commit. Do not place provider/file work under posting locks.

**Verification for each implemented slice:** service rules, allowed/forbidden claims, cross-scope access, CSRF, stale evidence, retries, concurrent commitments and PostgreSQL transaction tests as applicable. Financial changes also need rounding, partial/advance/early payments, linked corrections, backdates, failure rollback and reconciliation. Browser-check English/Kiswahili at 360, 768, 1366 and 1920 pixels, including edited-value recovery, documents, navigation and login/logout. Record actual compile/test/package results and skipped coverage.

## 6. Review evidence and limitations

Reviewed the supplied research, project instructions, principal gap/accounting checklists, reset/increment notes, current group completion evidence and targeted active source. Searched Java/JSP/migration/test paths for dedicated collateral, restructuring, complaints, education, leasing, housing, bureau/submission and report-template workflows. Read registration, ordinary eligibility/guarantors, shared exposure projection, recording, repayment/reminders, adjustments, charges, payroll, statements and reconciliation. Source paths in sections 1–2 are relative to `src/main/java/com/sacco/mvp` unless stated otherwise.

Official scope references checked on 27 October: [BoT regulations and guidance listing](https://www.bot.go.tz/BankSupervision/Regulations) and the published [Tier 2 non-deposit-taking regulations](https://www.bot.go.tz/Publications/Acts%2C%20Regulations%2C%20Circulars%2C%20Guidelines/Regulations/sw/2020021122490967551.pdf). The listing includes digital-lender guidance; exact current obligations, approved contracts, pricing methods and the operator's permissions still need a focused institution/compliance review. The research's broad activity list is not authorization to offer every product.

Unresolved working-copy conflicts were observed during inspection, then disappeared in the concurrent commit `129cb547`, which adds scoped coverage workspaces. The final conflict-marker scan and Git status were clean before this report was added. The report therefore does not list those transient conflicts as an outstanding feature gap.

**Files changed by this task:** this report only. No application, business-rule, UI or database changes were made and no operational datasource was accessed. Existing test files/delivery logs were inspected as evidence; Maven, browser, integration and load tests were not run for this documentation-only review. Validation consists of source-link existence, conflict-marker checks and Git whitespace review. Runtime correctness and complete institutional acceptance remain unverified here. No performance measurements were made; 1,000-RPS capacity remains unproven.
