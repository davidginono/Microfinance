# Accounting policy and architecture review

Prepared 2026-10-02. **Proposed architecture; no institution policy, opening balance, account mapping, recognition treatment or approval has been supplied.** The software records human decisions; agents do not make them. No policy is seeded and every event needs an explicit permitted/disabled decision.

## Current-code audit (A01)

| Area | Reuse and authoritative boundary | Remaining distinction |
| --- | --- | --- |
| V41, `LoanRepaymentLedgerService`, loan-ledger/installment/transaction/allocation repositories | New ordinary disbursement initializes dated contractual obligations; verified due-instalment payments and independent linked reversals commit allocations, clearing journal and audit together. Immutable triggers, decimal precision and scoped keys are reusable. | Loan journal codes are operational clearing entries. They do not identify verified bank/mobile-money accounts, account for contractual interest recognition, or establish official financial statements. |
| `ManagerService` disbursement/top-up | Ordinary loan opening participates in disbursement transaction; ledger-backed top-up source cannot be marked paid without a settlement transaction. | Untracked legacy top-ups retain status-based handling. This needs cutover/release controls; never backfill payments from `PAID`. |
| `LoanAmortizationCalculator`, `RepaymentScheduleService` | Versioned decimal periodic schedules, principal conservation and due-date conventions can describe accepted contracts. | Contractual pricing and HALF_UP implementation conventions are awaiting institutional approval; they do not choose amortized-cost/effective-interest accounting recognition. |
| `LoanPaymentSummarySyncService`, `LoanPresentationService`, `LoanAnalyticsService` | Tracked loan refresh can derive payment projections and arrears from local obligations/allocations. | Other analytics still group status `PAID` and read financial-snapshot totals, including projected principal-plus-interest fallbacks (`resolveOutstandingAmount`). Operational trend counts cannot be represented as audited GL financial totals. |
| `LoanReportService`, AccountantController reports, export limiter | Existing branch-scoped workflow datasets, bounded reports and export permission controls can serve operational reporting. | Reviewed/approved/rejected loan summaries concern workflow decisions. They are not income statements, trial balances, bank reconciliation or statement of financial position. |
| `AppUserPrincipal`, `AccessControlService`, `UserClaimService` | Session/CSRF authentication, atomic claims, explicit per-user claims and tenant/branch identities remain compatible. | Session claims can be stale. Policy service additionally refreshes current active member, institution and effective claims for every read/write. Policy powers are institution-wide and need deliberate assignment. |
| Audit and migrations | Existing AuditService persists events inside callers' transactions; Flyway owns schema evolution. V41 prevents financial history deletion. | Approved audit retention, disaster recovery, backup restore test and reconciliation to verified legacy sources remain missing. Root integration must preserve all `ACCOUNTING_*` audit entities from operational cleanup. |

## A02: single authoritative books

The proposed default is one integrated local GL per institution with branch dimensions, supported by append-only loan subledger transactions. `LOCAL_GL` must still be explicitly selected and independently approved. `EXTERNAL_GL` can be recorded only as a decision: local posting is then disabled. There is no external connector, endpoint, network saving check, dual official ledger or automatic acknowledgement. A later change of authoritative ledger/opening date is blocked until a separately reviewed migration is implemented.

The initial approved version must be effective on its explicitly verified opening date; otherwise no policy would cover opening journals. Later approved versions retain that opening date, move the effective boundary forward, and cannot retroactively change earlier policy definitions.

## A03, A05, A06: accountant decisions required

Complete every policy field with a specific decision and supporting evidence, or a specific documented restriction. Required fields are reporting framework/applicability, financial year, chart, mappings, rounding, accounting interest recognition, contractual-interest boundary, fees/taxes, impairment/provisions, non-performing interest, early settlement, closing/reopening, authorization matrix, cutover/backout, retention/recovery and external-integration boundary.

The contractual loan schedule is an input to the accounting assessment, never an automatic accrual rule. The accountant must decide recognition timing, day-count/periodic basis, effective interest/amortized cost if applicable, financed versus expensed fees, collection-versus-accrual treatment, impairment measurement, tax basis, suspended/non-performing interest and settlement treatment. Existing contracts and seeded lending rules remain unchanged.

Official sources were rechecked on 2026-10-02. [BoT Tier 2 regulations 2019](https://www.bot.go.tz/Publications/Acts,%20Regulations,%20Circulars,%20Guidelines/Regulations/en/2020021122490967551.pdf), regulations 24–26, address supporting accounting records, January–December financial year, audited submission timing and statements. [NBAA Technical Pronouncement No.1 of 2018](https://www.nbaa.go.tz/2019/march/techpro2019.pdf) distinguishes reporting categories. These inform review; they do not establish this operator's applicable framework, current licence or approval. Reviewers must check subsequent amendments, regulator-specific requirements and the current [NBAA technical updates](https://www.nbaa.go.tz/pages/technical-updates-2). No legal applicability is certified by this document.

## A04: posting-matrix review worksheet

For **each** row, the approver must record whether the event is allowed, approved debit/credit accounts and branch dimensions, recognition/effective date, amount/currency/precision, source evidence and uniqueness key, maker/checker powers, reversal treatment and control/subledger reconciliation. Where unsupported, record disabled with reason. This worksheet deliberately contains no invented debits, credits, prices or default permission.

| Event | Required reviewer evidence |
| --- | --- |
| Opening balances | Verified cutover/source trial balance, imported line reconciliation, unknown exclusions, independent checking; no unexplained balancing plug. |
| Disbursement | Loan principal/net cash/financed costs treatment and accepted contract; verified cash/bank account and control mapping. |
| Repayment | Principal/due interest/fees/applied versus unapplied funds; real channel evidence and scoped uniqueness. |
| Interest accrual | Framework-specific recognition, effective-interest treatment where applicable, contractual-versus-GL reconciliation and impaired interest. |
| Fees | Permitted fee/legal evidence, tax basis, timing and financing treatment. |
| Refund / advance | Unapplied-funds liability and actual channel evidence; no invented ordinary-repayment allocation. |
| Settlement / top-up | Verified settlement of source, waived/earned amounts, net cash and surviving obligations. |
| Expense | Verified invoice/receipt, payable/cash/accrual/tax treatment and checking authority. |
| Funding / capital | Lender financing versus owner/shareholder equity, principal/interest obligations; borrower contributions are not capital. |
| Provision / write-off / recovery | Approved risk methodology, required separate approval and tracking; accounting write-off does not imply debt forgiveness. |
| Reversal | Linked original, unchanged history, independent checker and approved correction date. |
| Manual journal | Approved accounts/purpose, evidence and checker; cannot bypass loan control accounts or rewrite subledger balances. |
| Operational bridge | Reviewed V41-to-GL mapping, cutover coverage and one-time source uniqueness; preserve original clearing entries. |

Account mappings are structured `sourceCode -> GL account UUID` values, immutable with the approved policy. A03 review must validate the scoped chart; Phase B must enforce mapped account existence/scope, activity and correct posting/control designation. The policy component never creates fake accounts to fill missing mappings.

## A07: proposed approval and scope matrix

| Capability | Software boundary | Human approval needed |
| --- | --- | --- |
| Read policy versions | `ACCOUNTING_POLICIES_VIEW`, current active staff and exact institution | Explicit permission to read institution policy; no transaction portfolio exposed. |
| Propose complete immutable version | `ACCOUNTING_POLICIES_CREATE`, current active staff and exact institution | Authorized policy maker and evidence ownership. |
| Approve/reject version | `ACCOUNTING_POLICIES_APPROVE`, current active staff, exact institution, checker different from maker, confirmed decision, evidence and reason | Accountant/compliance designation and delegated institution-wide authority. |
| Account administration, journal maker/check/post/reverse, opening import/check | Separate Phase B claims and scope guards; approval must refer to applicable approved local policy and event permission | Detailed account-specific delegation and maker/checker matrix. |
| Reconciliation, close/reopen | Separate Phase D claims and institution/branch dimensions | Independent matching/review, close checklist and exceptional reopen powers. |
| Report design, publish, run, export and institution aggregation | Separate Phase E–G claims; server scope and immutable definitions | Explicit audience and publication/aggregation authority. |

No new accounting claim is assigned to a role automatically. A branch role alone does not grant institution policy authority. Claim assignment and accountant designation are separate human work. Submitted tenant, maker, version and approval metadata are not accepted from forms.

## A08: migration, indexes, cutover/backout and recovery

- Feature boundary: `com.sacco.mvp.accounting.policy` owns DTO/form/controller, service, entities and repositories. Other components call the public policy service and immutable snapshot; no direct repository access. Session/CSRF, JSP shell and PostgreSQL remain in place.
- V42 creates immutable scoped policy proposals and independent immutable approval records. This schema supports an explicitly requested approval workflow, not a schema change merely for planning. Institution and member FKs block invalid scope/deletion; scoped request keys and versions block duplicates; checked decisions plus a composite approval FK preserve identity.
- Proposals get a unique institution version. Concurrent proposals may return a conflict and require an intentional retry; they cannot silently share a version. Concurrent policy decisions take a short institution-specific advisory transaction lock, so competing effective boundaries/official books cannot both pass. This rare administrative lock is absent from ordinary read/posting routes.
- Indexes support `sacco_id, policy_version` for a 25-row version list; `sacco_id, effective_from DESC, policy_version DESC` for one applicable approved policy; and partial approved-decision date lookups. The list batches its 25 approval rows in one query, never N+1 queries or unbounded portfolio loads. Representative plans/load measurement remain H verification work.
- Initial activation requires verified opening date/amounts and supporting records from Phase B, not V41 snapshots/statuses. Reconcile chart, source trial balance, loan control totals, each verified channel and cutover event coverage. Keep unknown balances explicit. Approve only after documented exceptions are resolved.
- Rehearse migration on an isolated backup, verify row/count/checksum evidence and restore procedure; compare subledger to GL, review initial published statement versions, and record accountant sign-off. No live opening import or production cutover is performed by this component.
- Backout before activation can restore the rehearsal copy or deploy the prior application while leaving new tables unused. After posting, retain all original journals and policy versions; correct by approved linked entries and a reviewed forward migration. Dropping financial tables/undoing posted originals is not backout.
- Institution must approve retention periods, evidence storage/access, encryption, backups, recovery-point/recovery-time objectives and restore drills. Immutable policy/approval rows retain decision evidence independently of operational audit cleanup; this does not replace an approved lifecycle. External integrations, if selected, need authority, idempotent handoff/acknowledgement, reconciliation, failure ownership and a cutover plan before any connector is enabled.

## Activation decision

All institutional decisions and source opening evidence are presently unavailable. Software implementation can be reviewed and tested independently. A02–A07 human approvals, A08 approved cutover/recovery plan and dependent live activation remain outstanding. This document is neither approval nor release authorization.
