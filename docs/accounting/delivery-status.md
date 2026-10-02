# Accounting delivery status

Date: 2026-10-02. Source: `docs/accounting-report-builder-checklist.md`, copied without changing its requirements from the user-specified Desktop checkout.

The user authorized eight dedicated agents, each in its own worktree. The execution runtime permits three worker agents alongside the coordinator, so work is scheduled in dependency waves. No operational database is used for verification or cutover.

## Human evidence gate

The user confirmed that accountant-approved policies, posting matrix, chart of accounts and verified opening balances are **not available yet** and instructed us to implement gated workflows. Creating software approval controls is not accountant approval. Institutions without approved evidence must remain restricted; no final complete financial statement or release approval may be fabricated.

## Ownership and integration

| Phase | Dedicated agent | Worktree directory | Reserved migration | Dependency |
| --- | --- | --- | --- | --- |
| A | a_policy | accounting-a-policy/Microfinance | V42 | Current-code audit |
| B | b_ledger | accounting-b-ledger/Microfinance | V43 | Approved-policy gate API from A |
| C | c_business | accounting-c-business/Microfinance | V44 | A/B accounting contracts |
| D | d_close | accounting-d-close/Microfinance | V45 | A/B/C posting and period contracts |
| E | e_reports | accounting-e-reports/Microfinance | V48 | B/D posted entries and coverage |
| F | f_builder | accounting-f-builder/Microfinance | V46 | Verified operational datasets |
| G | g_statements | accounting-g-statements/Microfinance | V47 | E/F definitions and coverage |
| H | h_verification | accounting-h-verification/Microfinance | V49 | Integrated A–G |

The coordinator owns the master checklist, shared navigation integration, release evidence and final integration. Workers own feature-local evidence files under `docs/accounting/`. Migration numbers reserve ownership; they do not imply that a migration has been implemented or approved.

All institution and branch identifiers retain existing String `saccoId` / `stationId` compatibility. Existing repayment journals remain immutable operational clearing entries until reviewed mapping and cutover. No automatic permissions, policy approvals, official books or opening balances are seeded.

## Integrated increments

| Area | Integrated local commits | Evidence and remaining work |
| --- | --- | --- |
| A: policy review | `1abbaba`, `ebccce5` | Versioned proposal/independent review, single-authoritative-book gate and current-claim checks. Phase A records 20 passing unit/MVC/PostgreSQL scenarios. Accountant decisions A02–A07 remain unavailable; downstream mapping and integrated acceptance still need verification. See `phase-a-evidence.md`. |
| B: accounts and general ledger | `a412ecb`, `06a223a` | Scoped chart, periods, balanced immutable journals, reviewed opening imports, bridges and transactional accounting outbox. Source journals require the owning transaction and cannot use generic approve/post endpoints. Phase B records 15 passing scenarios, including 11 PostgreSQL scenarios; source/subledger coordination belongs to C. See `phase-b-evidence.md`. |
| F: operational builder | `20223db` | Typed Collections, Disbursements and Loan Portfolio definitions, scoped queries, immutable template versions and independent publication; bounded CSV/XLSX/PDF exports. Logo/font and further PostgreSQL verification are being completed in the F worktree. See `phase-f-evidence.md`. |
| Shared security and retention | `07e6368`, `67200f9`, `1e50187`, `ebda514` | Explicit permissions without default grants, staff-only finance/report route boundary, retained accounting/report audit events and permission-aware sidebar navigation. Institution/member financial-history guards and integrated browser verification are in progress. |

These are verified increments rather than complete phase claims. C and D agents are active in their dedicated worktrees. E, G and H will follow as worker slots become available. The master checklist remains unchecked where the full requirement and its integrated review evidence are outstanding.

On 2026-10-02 the coordinator verified policy/GL current active institution and branch checks, current staff/claim checks, and early policy/GL deletion guards with `mvn -Dtest=AccountingPolicyServiceTest,AccountingPolicyPostgresTest,GeneralLedgerValidationTest,GeneralLedgerPostgresTest,GeneralLedgerAccessTest,SaccoDataDeletionServiceTest test`: **45 passed, zero failures/errors/skips**, including **15 PostgreSQL scenarios** against the dedicated A/B synthetic databases. V1–V43.1 and V46 validated/applied. An earlier interrupted compilation is not counted as a passing run. PostgreSQL recovered its disposable cluster normally after a process interruption; this observation alone does not complete H13 backup/restore or cutover verification.

All eight dedicated agent identities have now been launched with their own worktrees. E's first turn stopped at the account usage limit; C also stopped at that limit after saving implementation. D resumed after a network-permission error; G and H have been dispatched. F's fresh run reports eight passing PostgreSQL tests and six passing controller tests, with two export assertions still under investigation. These failures and remaining integration/browser checks keep F incomplete.

## Completion rule

Record item-level implemented behavior, commits and actual tests as work is integrated. Leave partial requirements unchecked and list remaining implementation and human acceptance gates explicitly. The overall goal remains incomplete until the checklist's full definition of done is supported by evidence.
