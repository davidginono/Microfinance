# Accounting delivery status

Updated: 2026-10-03. Source: `docs/accounting-report-builder-checklist.md`, copied without changing its requirements from the user-specified Desktop checkout.

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
| A: policy review | `1abbaba`, `ebccce5`, `ec572e6` | Versioned proposal/independent review, validated account mappings and current scope/claims. Latest focused policy suite: 22 tests, including six PostgreSQL cases. Human decisions A02–A07 are unavailable and remain required. See `phase-a-evidence.md`. |
| B: accounts and general ledger | `a412ecb`, `06a223a` | Scoped chart, periods, balanced immutable journals, reviewed opening imports, bridges and transactional accounting outbox. Source journals require the owning transaction and cannot use generic approve/post endpoints. Phase B records 15 passing scenarios, including 11 PostgreSQL scenarios; source/subledger coordination belongs to C. See `phase-b-evidence.md`. |
| F: operational builder | `20223db`, `e2bcd93` | Typed Collections, Disbursements and Loan Portfolio definitions, scoped queries, immutable template versions and independent publication; bounded CSV/XLSX/PDF exports with captured institution branding and bundled Unicode font. Fresh focused verification passed 23 scenarios, including eight PostgreSQL scenarios. Full integrated browser and remaining dataset acceptance are outstanding. See `phase-f-evidence.md`. |
| C: loan and business sources | `c4e9ee3`, `4171094`, `7f28a0e` | Reviewed business source documents, supplier/assets/control movements and immutable independently checked cancellations/reversals. Latest correction suite: 59 tests, including 19 PostgreSQL cases; ordinary loan collaborators in that fixture are mocked. Live release leaf, actual loan/GL agreement, typed reviewed openings and advanced settlement/provision/recovery flows remain unfinished. See `phase-c-evidence.md`. |
| D: reconciliation and closing | `80dc2cb`, `94dfd56`, `8da3a09` | Immutable source cancellation/correction support, independent close/reopen, ordered historical publication locks and typed source/cash-flow provenance. Latest dedicated suite: 39 tests, including 33 PostgreSQL cases. C/E owning adapters, institution aggregate source, rendered acceptance and human approval remain separate gates. See `phase-d-evidence.md`. |
| E: financial reads and cash-flow review | `83bf584`, `a5fa274`, `410b5f1` | Posted trial balance/account activity, exact reviewed compound cash-flow allocations and scoped current-claim checks. Allocation suite: 30 tests, including seven PostgreSQL cases; earlier populated bilingual browser matrices remain recorded separately. Source exports, management metrics, owning D adapter and full E02–E08 acceptance are unfinished. See `phase-e-evidence.md`. |
| G: statement designer | `c0df67c`, `ca46cab` | Approved bounded mappings/calculations, frozen close-driven statements and protected regulatory submissions. Latest verified dedicated suite: 36 tests, including 18 PostgreSQL cases. Compound cash-flow v2, explicit institution aggregates, rendered acceptance and official applicable regulatory definitions are unfinished. See `phase-g-evidence.md`. |
| H: retained runs, statement files and release review | `d9581d8`, `71459a9`, `bf8e15b` | Immutable typed files, independent accountant/compliance/staff release workflow and dependent-source invalidation. Latest dedicated suite: 46 tests, including 26 PostgreSQL cases; fresh recovery, exact opening/manual replay, 20,000-row exports and bilingual portrait/landscape PDF QA passed. C activation, full integrated regression/package, representative concurrency and actual institution acceptance remain required. See `phase-h-evidence.md`. |
| Shared security and retention | `0f275a5`, `5c61918`, `e8d8b3d` | Explicit permissions without default grants, active staff/institution/branch checks and early retained-history guards. Latest root suite: 55 tests, including 21 deletion protections, three reconciliation-validation checks and locale/access/claim checks. Shared navigation, permission labels and print acceptance are pending rendered verification. See `release-integration-verification-20261003.md`. |

These are verified increments, not completed phases. All eight dedicated identities and worktrees exist; dependency work currently continues in D/E/G and coordinator-owned C/integration. Verified human-facing workflows remain separate from unavailable institution approvals. The master checklist stays unchecked wherever the complete requirement and integrated acceptance evidence are outstanding.

On 2026-10-02 the coordinator verified policy/GL current active institution and branch checks, current staff/claim checks, and early policy/GL deletion guards with `mvn -Dtest=AccountingPolicyServiceTest,AccountingPolicyPostgresTest,GeneralLedgerValidationTest,GeneralLedgerPostgresTest,GeneralLedgerAccessTest,SaccoDataDeletionServiceTest test`: **45 passed, zero failures/errors/skips**, including **15 PostgreSQL scenarios** against the dedicated A/B synthetic databases. V1–V43.1 and V46 validated/applied. An earlier interrupted compilation is not counted as a passing run. PostgreSQL recovered its disposable cluster normally after a process interruption; this observation alone does not complete H13 backup/restore or cutover verification.

All eight dedicated agent identities have now been launched with their own worktrees. E's first turn stopped at the account usage limit; C also stopped at that limit after saving implementation. D resumed after a network-permission error; G and H have been dispatched. F's first fresh export run exposed two assertions tied to outdated metadata row positions. The assertions were corrected to locate the typed data and header rows; the subsequent controller/definition/export and PostgreSQL runs passed all 23 scenarios. Remaining integrated coverage keeps F incomplete.

On 2026-10-02 the coordinator added early institution/member deletion guards for operational report template history. The integrated command `mvn -Dtest=SaccoDataDeletionServiceTest,OperationalReportExportTest,OperationalReportControllerTest test` passed **21 scenarios, zero failures/errors/skips**, finishing at 13:54:42 EAT. The nine deletion scenarios include independent report-history-only references and verify that deletion/file effects do not begin.

Rendered browser verification confirmed staff login, chart-of-accounts creation and a pending synthetic policy proposal. After correcting omitted finance/report locale routes, all 32 account/journal/policy/builder page checks passed in English/Kiswahili at 360/768/1366/1920 pixels without page overflow or JavaScript errors. Actual administrator language was selected through institution settings. Six locale tests also passed. Record interaction, exports, print and remaining phase pages are pending; see browser-verification-20261002.md. That32-page baseline retained a pending proposal. Later E-only disposable browser fixtures independently reviewed a synthetic policy/opening for interaction tests; these simulated decisions have no institutional/compliance significance.

The integrated reconciliation/run retention guard command `mvn '-Dtest=SaccoDataDeletionServiceTest' test` passed **13 scenarios, zero failures/errors/skips**, finishing at 14:56:02 EAT. Institution/member references in reconciliation history or frozen report runs independently block deletion before database/file effects. Main/test compilation included the integrated D/E/H code; no PostgreSQL or full release claim is inferred from this focused guard run.

## Completion rule

Record item-level implemented behavior, commits and actual tests as work is integrated. Leave partial requirements unchecked and list remaining implementation and human acceptance gates explicitly. The overall goal remains incomplete until the checklist's full definition of done is supported by evidence.

On2026-10-02 the integrated statement retention guard command `mvn -Dtest=SaccoDataDeletionServiceTest test` passed **15 scenarios, zero failures/errors/skips**, finishing15:51:10 EAT. Institution/member history found only in statement mappings, results or regulatory submissions blocks deletion before row/file effects. Integrated main318/test150 compilation succeeded; this focused run does not claim PostgreSQL or full release coverage.

On 2026-10-03, H release/artifact retention and C correction safeguards were integrated without fabricating policy, opening or staff acceptance records. Root current-claims/locale/retention verification passed 52 tests with zero failures/errors/skips at 21:12:37 EAT. The focused command selects no PostgreSQL suites; dedicated phase evidence must not be added together as unique test counts. New D provenance integrated as `8da3a09`; integrated optional-provider fixture compilation and browser acceptance are still being verified.

Root optional-provider integration subsequently compiled342main/158test sources and passed55 focused checks at21:38:48EAT with zero failures/errors/skips. This verifies constructor/validation/access integration, not full PostgreSQL or browser acceptance.
