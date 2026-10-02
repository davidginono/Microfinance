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
| A: policy review | `1abbaba`, `ebccce5` (plus local mapping follow-up) | Versioned proposal/independent review, single-authoritative-book gate and current-claim checks. Phase A records 20 passing unit/MVC/PostgreSQL scenarios. Accountant decisions A02–A07 remain unavailable; downstream mapping and integrated acceptance still need verification. See `phase-a-evidence.md`. |
| B: accounts and general ledger | `a412ecb`, `06a223a` | Scoped chart, periods, balanced immutable journals, reviewed opening imports, bridges and transactional accounting outbox. Source journals require the owning transaction and cannot use generic approve/post endpoints. Phase B records 15 passing scenarios, including 11 PostgreSQL scenarios; source/subledger coordination belongs to C. See `phase-b-evidence.md`. |
| F: operational builder | `20223db`, `e2bcd93` | Typed Collections, Disbursements and Loan Portfolio definitions, scoped queries, immutable template versions and independent publication; bounded CSV/XLSX/PDF exports with captured institution branding and bundled Unicode font. Fresh focused verification passed 23 scenarios, including eight PostgreSQL scenarios. Full integrated browser and remaining dataset acceptance are outstanding. See `phase-f-evidence.md`. |
| D: reconciliation and closing | `80dc2cb` | Immutable independent review, close/reopen snapshots, opening provenance, publication locking and conservative cash-flow ambiguity flags. Dedicated final suite passed 18 scenarios, including 12 PostgreSQL scenarios. C historical controls, rendered flows, performance and institution approval remain gated. See `phase-d-evidence.md`. |
| E: posted financial reads | `83bf584` | Scoped trial balance/account activity, full-scope totals, reviewed coverage and effective/recorded cutoffs. Dedicated backend suite passed ten scenarios including four PostgreSQL scenarios; financial pages have a separate 19-scenario integrated check and browser verification in progress. Full E02–E08 remain open. See `phase-e-evidence.md`. |
| H: durable operational runs | `d9581d8` | Frozen paged results, typed immutable artifacts, quotas/retries/cancellation and independent review. Dedicated suite passed 15 scenarios, including ten PostgreSQL scenarios; actual backup/restore verified retained checksums and database immutability. Final-statement/release controls and full integrated H acceptance remain open. See `phase-h-evidence.md`. |
| Shared security and retention | `07e6368`, `67200f9`, `1e50187`, `ebda514`, `0f275a5`, `5c61918`, `5d467ef` | Explicit permissions without default grants, staff-only finance/report route boundary, retained accounting/report audit events and permission-aware sidebar navigation. Institution/member financial-history guards and integrated browser verification are in progress. |

These are verified increments rather than complete phase claims. D delivered its verified increment. G and H are active in their dedicated worktrees. C and E saved work after usage-limit interruptions; the coordinator is progressing integration and E flows. Three attempts to resume the existing C worker were rejected by the agent runtime with “agent thread limit reached”; independent implementation/integration continues. The master checklist remains unchecked where the full requirement and its integrated review evidence are outstanding.

On 2026-10-02 the coordinator verified policy/GL current active institution and branch checks, current staff/claim checks, and early policy/GL deletion guards with `mvn -Dtest=AccountingPolicyServiceTest,AccountingPolicyPostgresTest,GeneralLedgerValidationTest,GeneralLedgerPostgresTest,GeneralLedgerAccessTest,SaccoDataDeletionServiceTest test`: **45 passed, zero failures/errors/skips**, including **15 PostgreSQL scenarios** against the dedicated A/B synthetic databases. V1–V43.1 and V46 validated/applied. An earlier interrupted compilation is not counted as a passing run. PostgreSQL recovered its disposable cluster normally after a process interruption; this observation alone does not complete H13 backup/restore or cutover verification.

All eight dedicated agent identities have now been launched with their own worktrees. E's first turn stopped at the account usage limit; C also stopped at that limit after saving implementation. D resumed after a network-permission error; G and H have been dispatched. F's first fresh export run exposed two assertions tied to outdated metadata row positions. The assertions were corrected to locate the typed data and header rows; the subsequent controller/definition/export and PostgreSQL runs passed all 23 scenarios. Remaining integrated coverage keeps F incomplete.

On 2026-10-02 the coordinator added early institution/member deletion guards for operational report template history. The integrated command `mvn -Dtest=SaccoDataDeletionServiceTest,OperationalReportExportTest,OperationalReportControllerTest test` passed **21 scenarios, zero failures/errors/skips**, finishing at 13:54:42 EAT. The nine deletion scenarios include independent report-history-only references and verify that deletion/file effects do not begin.

Rendered browser verification confirmed staff login, chart-of-accounts creation and a pending synthetic policy proposal. After correcting omitted finance/report locale routes, all 32 account/journal/policy/builder page checks passed in English/Kiswahili at 360/768/1366/1920 pixels without page overflow or JavaScript errors. Actual administrator language was selected through institution settings. Six locale tests also passed. Record interaction, exports, print and remaining phase pages are pending; see browser-verification-20261002.md. That32-page baseline retained a pending proposal. Later E-only disposable browser fixtures independently reviewed a synthetic policy/opening for interaction tests; these simulated decisions have no institutional/compliance significance.

The integrated reconciliation/run retention guard command `mvn '-Dtest=SaccoDataDeletionServiceTest' test` passed **13 scenarios, zero failures/errors/skips**, finishing at 14:56:02 EAT. Institution/member references in reconciliation history or frozen report runs independently block deletion before database/file effects. Main/test compilation included the integrated D/E/H code; no PostgreSQL or full release claim is inferred from this focused guard run.

## Completion rule

Record item-level implemented behavior, commits and actual tests as work is integrated. Leave partial requirements unchecked and list remaining implementation and human acceptance gates explicitly. The overall goal remains incomplete until the checklist's full definition of done is supported by evidence.
