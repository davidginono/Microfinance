# Phase A — policy and architecture evidence

Implementation review date: 2026-10-05; official-source research date: 2026-10-04. Target: Tanzania Mainland, Tier 2 non-deposit-taking microfinance. The user confirmed institution/accountant approvals and opening evidence are unavailable: activation must remain gated. This document records software and research evidence, not accountant sign-off, regulatory approval, or a release decision.

## Current-code audit (A01)

| Area | Reusable implementation | Authority boundary / gap |
| --- | --- | --- |
| Loan repayment | `LoanRepaymentLedgerService`, `loan_ledgers`, instalments, append-only transactions and allocations | Verified due-instalment payments and reversals exist. Fees, advances, early settlement, legacy openings and top-up settlement are not implied by this increment. |
| Operational journal | `loan_journal_entries`, V41 balanced-voucher protections | Symbolic LOAN_PRINCIPAL / DISBURSEMENT_CLEARING / CASH_CLEARING entries are operational clearing evidence. They are not institution-approved accounts or a complete GL. Bridge sources exactly once; preserve originals. |
| Ordinary disbursement | Ledger initialization runs within the actual disbursement transaction | Approval alone is not movement of money. Top-up source settlement needs its own verified transaction and net cash movement. |
| Projections | `LoanPaymentSummarySyncService`, `RepaymentScheduleService`, `LoanPresentationService` | Refresh recalculates projections; scheduled future payments are neither cash received nor today's settlement amount. Unopened legacy balances remain unavailable. |
| Analytics | `LoanAnalyticsService` around `resolveOutstandingBalance` / snapshot helpers | Legacy fallback uses financialSnapshot principalPlusInterest, loanPlusInterest or interestAmount. It must not become financial statement authority. |
| Exports | `LoanReportService` outstanding/interest helpers and XLSX writers | Existing exports reuse snapshot/status-based amounts in some paths; some XLSX cells use doubleValue. Financial definitions, precision and coverage must be verified separately in phases E/H. |
| Permissions | Session/form/CSRF; `AccessControlService`, `SecurityConfig`, principal institution and branch | New accounting claims are explicit, with no seeded financial approvals or grants. Platform identities and client sessions cannot govern policies. |
| Migrations | V1–V40 operational schema; V41 repayment increment | V41 opens no legacy balance. V42 records immutable accounting policies and approval audit only. It does not initialize accounting books. |

Source inspection is targeted. This audit does not assert every existing route, export, or business workflow was browser-tested.

## Software policy controls (A02–A07)

The component is `com.sacco.mvp.accounting.policy`, separated into controller, DTO, service, repository and model packages. Controllers receive DTOs; only the owning service calls its repository. Policy content is plain evidence, never executable SQL, formulas, templates, or financial logic.

Routes: `GET /accounting/policies`, `GET /accounting/policies/new` (optional scoped source for preparing a revised draft), `POST /accounting/policies`, `GET /accounting/policies/{id}`, and CSRF-protected `POST /{id}/approve` / `reject`. Staff supply the authority decision, dates, decision evidence and a typed posting matrix. Draft fields survive validation errors. Previously preserved content can be copied to a new version; changing or deleting the original is prohibited.

Governance claims are `ACCOUNTING_POLICY_VIEW`, `CREATE`, `APPROVE`, `REJECT`. These explicitly authorize institution-wide non-borrower policy metadata. They do not confer cross-branch loan or journal access. The maker and checker must be different identified staff. The origin and reviewer branches are retained. Institution comes solely from the principal, with a nonempty branch; forged scope/unknown form fields are rejected. No permission is inferred from the accountant role label alone.

All 15 policy decisions and all 28 source-event decisions, treatments and evidence references must be present before approval. Enabled events require their minimum account-role mappings. An operation may explicitly remain disabled. A service-level approval is a recorded human assertion by an authorized user: it is not an agent certification that the prose is correct or that supporting documents are authentic. Account existence, tenant ownership, category and permitted debit/credit semantics must also be checked by the GL/source posting services.

One immutable policy version identifies the official LOCAL or EXTERNAL authority. The local GL is the proposed default architecture, but no institution has been given this choice automatically. An EXTERNAL approved choice blocks official local posting; delivery remains unimplemented until acknowledged idempotent outbox integration and reconciliation exist. Switching authorities is deliberately blocked and needs a separate controlled cutover increment.

Initial accountant-reviewed effective/opening dates may be historical, enabling a real cutover after evidence is obtained. A later version must start after today and after the newest approved version, preserving old effective-date interpretations. Previously posted journal entries pin ID, version and SHA-256 hash. The policy content is canonicalized before hashing, so transport map ordering cannot change an exact retry. Draft creation uses an institution-scoped idempotency key and a short governance lock. Exact retries do not duplicate versions or audit. Independent review locks only governance records; ordinary postings do not acquire this lock.

Public downstream API:

- `requireApproved(String saccoId, LocalDate effectiveDate)` returns an immutable approved policy or fails closed.
- `requireLocalPolicy(String saccoId, LocalDate effectiveDate)` also rejects EXTERNAL authority.
- `requireApprovedLocal(String saccoId, UUID policyId, int policyVersion, LocalDate effectiveDate)` validates the pinned applicable local version.
- `requirePostingRule(String saccoId, LocalDate effectiveDate, AccountingEvent event)` also rejects a disabled or missing source event.
- `requireOpeningPolicy(String saccoId, LocalDate cutoff)` resolves the first approved LOCAL policy server-side; its exact approved opening date and enabled OPENING_BALANCE decision must match. The pinned overload `(String saccoId, UUID policyId, int policyVersion, LocalDate cutoff)` also verifies preserved ID/version. This is solely an initial-opening exception, not authorization for arbitrary historical source events.
- `approvedOpeningDate(String saccoId, LocalDate effectiveDate)` returns the applicable approved opening boundary. The cutoff represents balances at the end of that date; ordinary source movement must be strictly after it. Revisions must preserve the approved opening date.
- `ApprovedAccountingPolicy` contains ID, version, effective date, authority, content hash, decisions and posting rules. `PostingRule` contains enabled, typed role-to-account-code map, treatment and evidence reference.

These are trusted internal accounting APIs: callers still authorize their own operation and institution/branch/record scope. An approved enabled event does not authorize unsupported calculations. Recognition, accrual, fees, early settlement, impairment and other calculations must use deterministic reviewed modes supported by the owning posting service; arbitrary policy prose must never select or execute a formula.

## Candidate posting matrix for accountant review (A04–A06)

This table describes review questions and candidate accounting identities. It is unapproved; it does not seed enabled operations, financial values or favourable policy decisions. Final account codes, recognition basis, tax, evidence and reversal rules must be supplied and checked independently.

| Source event | Candidate debit | Candidate credit | Required review / invariant |
| --- | --- | --- | --- |
| Verified ordinary disbursement | Loan principal | Actual payment account or approved clearing | Confirm cash movement and subledger control; approval is insufficient. |
| Due repayment | Actual receiving account | Due receivable components | Interest, approved outstanding fees, then due principal; collection must not recognize accrued income twice. |
| Interest accrual | Interest receivable | Recognized interest income | Contract schedule and accounting recognition are separate; effective-interest/amortized-cost applicability requires review. |
| Permitted fee | Approved receivable/expense as applicable | Approved fee income/liability | Confirm legal allowance, taxes and recognition timing; no automatic fee seed. |
| Unmatched/advance money | Actual receiving account | Suspense/client advance liability | No false loan receipt or unearned revenue; resolve through independent review. |
| Refund | Approved liability | Actual payment account | Evidence of obligation and refund; never erase the source receipt. |
| Early/full settlement | Receiving account and approved adjustments | Due receivables/principal | Approved recalculation; exclude unearned future interest and unsupported penalties. |
| Top-up/internal settlement | New principal/control as applicable | Source principal and verified net cash | Preserve source contract and posted history; internal settlement is not a bank receipt. |
| Restructuring | Reviewed adjustment accounts | Reviewed adjustment accounts | Preserve contractual versions; modification recognition needs accountant decision. |
| Impairment | Impairment expense | Allowance | Financial impairment and regulatory provisions remain distinguishable. |
| Write-off/recovery | Allowance / actual cash | Principal / reviewed recovery income | Write-off is not automatic debt forgiveness; retain recovery linkage. |
| Invoice/direct expense/payment | Expense/asset or payable | Payable or cash | Recognizing an invoice and paying an existing payable are distinct. |
| Owner capital/distribution | Actual cash or approved equity | Owner capital or actual cash | Borrower contributions are not owner capital; distribution authority needs evidence. |
| Funding receipt/repayment/interest | Actual cash / liability / interest expense | Funding liability / actual cash | Funding principal is not operating income or expense. |
| Asset purchase/depreciation/disposal | Asset / depreciation / reviewed disposal accounts | Cash/payable / accumulated depreciation / asset | Approved asset register, useful life and disposal treatment. |
| Tax/prepayments/accruals | Reviewed asset/expense/liability | Reviewed liability/cash/income | No payroll or tax-filing integration implied. |
| Branch transfer/shared expense | Internal balancing / approved allocation | Internal balancing / approved allocation | Institution elimination prevents double-counting; retain historical branch dimensions. |
| Opening/clearing bridge/manual journal | Reviewed account debits | Reviewed account credits | Verified opening evidence; no unexplained plug; no manual bypass of loan controls. |
| Linked reversal | Original credits | Original debits | Preserve the original; independent approval, source linkage and one reversal only. |

## Official-source review and human gate

Sources were retrieved on 2026-10-04; current applicability must be confirmed by the institution's accountant/compliance reviewer before activation.

1. [BoT GN679, 2019 Tier 2 regulations](https://www.bot.go.tz/Publications/Acts%2C%20Regulations%2C%20Circulars%2C%20Guidelines/Regulations/en/2020021122490967551.pdf), regulations 24–27, 42, 45 and 50: traceable books and underlying evidence, January–December financial year, audited annual accounts within four months, four main statements and ledger/subledger reconciliation. The published text allocates receipts to due interest, outstanding fees/charges, then due principal; allows early repayment without penalty or remaining-period interest on full early repayment; and uses ordinary Tier 2 DPD bands 0–5 / 6–30 / 31–60 / 61–90 / over 90 with minimum provisions 1 / 5 / 25 / 50 / 100 percent. Management ageing buckets are not official classifications.

2. [NBAA Technical Pronouncement 1 of 2018](https://www.nbaa.go.tz/2019/march/techpro2019.pdf): applicability depends on public-interest status, entity circumstances and prescribed thresholds. The software does not assume IFRS for SMEs merely because the institution is small. [NBAA current technical updates](https://www.nbaa.go.tz/pages/technical-updates-2) include later pronouncements, so reporting and disclosure applicability requires a current professional decision.

3. [BoT 2024 fee guidelines for microfinance providers](https://www.bot.go.tz/Publications/Acts%2C%20Regulations%2C%20Circulars%2C%20Guidelines/Guidelines/en/2024071716375097.pdf): pricing governance, disclosure and prohibited charge categories require separate review of actual product fees. Existing application/processing labels are not approval to charge both.

4. [BoT September 2026 Tier 2 submission guidance](https://www.bot.go.tz/Publications/Acts%2C%20Regulations%2C%20Circulars%2C%20Guidelines/Guidelines/en/2026092118400398.pdf) refers in some forms to 2026 non-deposit-taking regulations. The [official regulations catalog](https://www.bot.go.tz/Publications/Filter/38?lang=en) inspected still lists the 2019 Mainland text. A published replacement text was not verified. The reviewer must resolve this discrepancy, current prescribed returns and submission requirements before regulatory release. No future draft, third-party summary, or another jurisdiction's regulations are treated as binding policy.

## Architecture, cutover and recovery (A08)

Keep the modular monolith and existing session/CSRF/JSP shell. The authoritative GL owns accounts, posting periods, immutable journals and lines, source uniqueness, cutover records and a transactional outbox. The existing loan component remains owner of contracts and loan subledger records. Monetary source commands commit GL, subledger, audit and outbox together; no network/file work runs under financial locks. Reconciliation owns imported statement provenance and matching decisions. Reporting reads posted sources with coherent effective and recorded-data cutoffs, approved versions and explicit coverage; templates never change those definitions.

V42 introduces `accounting_policy` with unique institution/version and institution/request-key indexes, an approved effective-date lookup index, immutable content and maker/checker checks; `accounting_policy_audit` is append-only and scoped. No policy approval is seeded. GL migrations belong to phase B and later owning phases; the planning document alone is not justification for changing operational schemas.

Cutover sequence: obtain signed source evidence and accountant policy decisions; approve account mappings and a bounded reviewed opening import; reconcile every loan control, cash/bank/channel and supplier/funding balance; identify unknown legacy loans and exclude them with coverage disclosure; record the effective boundary and each existing clearing source accounted for once; independently approve cutover; run replay/duplicate/reversal/concurrent-close checks and agreement reports; then explicitly authorize activation. The opening represents end-of-cutoff balances. Preserve lineage for verified earlier clearing sources as covered by that opening; do not replay them as new money. Only uncovered post-cutoff sources may create a clearing bridge, subject to the approved applicable policy and source uniqueness. Zero unknown balances must be demonstrated, not inferred from missing rows.

Backout before posting: disable the new action permissions/activation and retain migrations and draft evidence. Backout after posting: stop new financial commands, preserve committed books, audit, outbox and finalized report artifacts, reconcile in-flight sources, and use approved reversals/adjustments or controlled cutover. Do not restore an old binary that can mutate money outside the GL boundary; do not delete journals or fabricate replacement receipts. Test recovery on a disposable copy with ledger continuity and source uniqueness before reopening.

Retention duration, access archive policy, legal hold, encryption, backup ownership, recovery point/time objectives and restoration sign-off remain explicit institution decisions in `AUDIT_RETENTION_AND_RECOVERY`. Until approved, do not purge financial records, policy audit or referenced accounts. Record backups and restoration results separately from operational cleanup. An external choice requires secure credentials, versioned mappings, acknowledged idempotent outbox delivery, bounded retries and visible failed/undelivered states; export alone is not posted external accounting.

## Acceptance status

| Item | Software/evidence status | Human acceptance gate |
| --- | --- | --- |
| A01 | Targeted audit recorded above | Institution review of legacy coverage and actual source records remains open. |
| A02 | One immutable authority, date-applicable version, external/local exclusion implemented | Institution selection is absent. |
| A03 | Versioned decisions, complete-before-approval and independent checker implemented | Framework/year/accounts/opening/rounding/recognition/closing approval is absent. |
| A04 | Typed explicit event decisions and mapping gate; candidate matrix documented | Accountant-approved posting matrix is absent. |
| A05 | Contract/recognition boundary retained; dependent mode implementation required | Applicable recognition/effective-interest policy is absent. |
| A06 | Fees/tax/impairment/non-performing/settlement/regulatory evidence versioned | Accountant/compliance approval and current-law discrepancy resolution are absent. |
| A07 | Distinct claims, trusted scope, independent maker/checker and immutable audit implemented | Approved institution responsibility/approval matrix is absent. |
| A08 | Migration/index/data-boundary/cutover/backout/retention/recovery plan documented | Cutover, retention and recovery objectives/sign-off are absent. |

## Verified software checks

On 2026-10-05 at 07:36 EAT, the focused command `mvn -Dtest=AccountingPolicyPostgresTest,PolicyFormTest,AccountingPolicyControllerTest,JspRecordAccessorCompatibilityTest test` completed with BUILD SUCCESS: 24 tests, zero failures, errors or skips. Main and test sources compiled for Java 25. The opt-in PostgreSQL suite used only `jdbc:postgresql://127.0.0.1:55449/microfinance_accounting_a_test` and synthetic institutions/staff. PostgreSQL 17.9 validated all 42 migrations on the existing disposable cluster without reset; V42 remained unchanged.

- Eleven database tests verified fail-closed absence and external authority; historical initial policy; exact reviewed opening before policy effectiveness without a generic historical posting exception; unchanged cutoff on revisions; complete decision and minimum mapping gates; independent maker/checker; stale fingerprint and retrospective revision rejection; immutable content/audit; scoped access; concurrent exact retries; canonical hashes under reordered input maps; and bounded 25-row metadata listing.
- Six controller tests verified claim enforcement, session/CSRF behavior, authorized service scope, malformed/missing fields and forged scope rejection, preserved invalid inputs, and localized independent-review errors. Five form tests verified strict fields/types/lengths, no favourable missing defaults and presence of static English/Kiswahili JSP labels. Two JSP contract tests verified record access compatibility. These are server/static checks, not rendered browser evidence.
- `git diff --check` passed. Foundation implementation is commit `87b7bd5`; reviewed opening API, stable content hashing and compact list projection are commit `3a6cbb1`. Follow-up controller/JSP/form/tests and this evidence are committed separately for integration. No permission grants or human approvals were seeded.

The listing selects five metadata columns with an institution-indexed bounded query and never reads every policy JSON document on the request path. Governance locks are short and isolated from ordinary journal posting. Connection/socket timeouts bound the disposable database test harness. These controls are architectural evidence, not a throughput measurement.

Rendered JSP/mobile/EN/SW checks, assembled login/logout/navigation verification, the full test suite and integrated release verification remain acceptance gates for phase H. No 1,000-RPS load test has been executed; capacity remains unproven. Institution/accountant/compliance approval and opening evidence remain absent, so financial activation remains gated.
