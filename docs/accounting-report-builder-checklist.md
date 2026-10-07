# Accounting Package And Report Customization Goal

Created: 2026-10-02. Target: Tanzania Mainland Tier 2, non-deposit-taking microfinance, initially individual business lending.

This is an agent implementation checklist, not completed functionality, accountant approval, or compliance certification. All implementation items start unchecked. It supplements the [conversion backlog](microfinance-gap-checklist.md), [repayment ledger boundaries](repayment-ledger-increment.md), and [credit calculation conventions](credit-estimate-increment.md).

## Goal For Agents

Deliver an institution-isolated Finance & Accounting package connected to the existing loan subledger, with verified opening balances, controlled double-entry posting, cash/bank/mobile-money reconciliation, period closing, financial statements, and versioned institution-specific report customization. Staff must be able to trace every financial total to posted records and supporting evidence. Customization must never change approved financial definitions, bypass permissions, or present unknown balances as zero.

Deliver through small, tested increments using the existing Spring Boot, PostgreSQL, session/CSRF security, JSP shell, and local UI skills. Preserve loan contracts, approval paths, legacy identifiers, and append-only repayment history. Do not reintroduce Foresight or borrower savings/share prerequisites.

## How To Use This Checklist

- Work in dependency order. Mark an item complete only with its commit, tests, and review evidence recorded below.
- Separate software implementation from institution/accountant/compliance approval. Agents must not approve accounting policies on behalf of people.
- If a policy or opening balance is unavailable, record the missing decision and restrict dependent functionality. Continue independent work; do not invent favourable defaults.
- Reinspect the current code before each increment. Existing clearing journals and analytics are not automatically an authoritative general ledger or financial statement source.
- A documentation update, passing unit suite, or attractive report preview does not complete the overall goal.
- Follow `AGENTS.md` for scoped local commits. Do not push or create separate chats without explicit user authorization.

## Delivery Order And Responsibility

| Phase | Lead responsibility | Dependency | Evidence needed to pass |
| --- | --- | --- | --- |
| A. Policy and architecture | Accounting lead + institution accountant | Current-code audit | Approved decisions and posting matrix |
| B. Accounts, journal, and opening balances | Accounting backend agent | A | Migrations, balanced postings, reviewed cutover |
| C. Loan and business accounting | Accounting backend agent | B | Subledger/control-account agreement |
| D. Reconciliation and period close | Accounting backend + QA | B-C | Statement matching and safe closing tests |
| E. Financial and management reports | Reporting backend agent | B-D for financial statements | Reconciled totals and traceable drill-down |
| F. Operational report builder | Reporting backend + JSP UI agent | Approved datasets; financial definitions from E | Scoped, versioned templates and equivalent exports |
| G. Financial statement designer | Reporting + accounting agents | E-F | Approved mappings and stable statement versions |
| H. Integrated release verification | Security/performance/QA + institution reviewers | A-G | Acceptance evidence and explicit release decision |

Operational datasets and builder UI can progress alongside accounting work when their source data is verified. Financial-statement functionality remains gated on the accounting prerequisites. These are responsibility areas, not permission to launch additional agents or chats.

## A. Policy And Architecture

- [ ] A01 Audit existing ledger, journal, repayment, disbursement, analytics, exports, permissions, and migrations; record what can be reused and which totals still depend on snapshots/statuses.
- [ ] A02 Decide one authoritative general ledger per institution: integrated local GL by default, or an approved external GL integration. Never maintain two competing official books.
- [ ] A03 Have the accountant approve the reporting framework, financial year, chart of accounts, opening date, account mappings, rounding, recognition, and closing policy. Do not automatically assume IFRS for SMEs applies.
- [ ] A04 Approve a posting matrix for disbursement, repayment, interest accrual, allowed fees, refunds, advances, settlement/top-ups, expenses, funding, provisions, write-offs, and recoveries, including reversals.
- [ ] A05 Define contractual interest versus accounting recognition, including effective-interest/amortized-cost treatment where applicable. A contractual schedule is not itself an accrual policy.
- [ ] A06 Confirm allowed fees, taxes, impairment/provisioning, non-performing interest treatment, and early settlement with accounting/compliance reviewers; version decisions without silently changing existing contracts or seeded rules.
- [ ] A07 Approve the scope/approval matrix for account administration, posting, checking, reversal, reconciliation, closing/reopening, report design, publication, run, and export. Institution-wide aggregation requires explicit authorization.
- [ ] A08 Record the migration plan, data model, indexes, cutover/backout procedures, audit retention, recovery requirements, and external-integration boundary if selected. Apply no schema changes merely to complete this planning document.

## B. Accounts, Journal, And Opening Balances

- [ ] B01 Add institution-scoped accounts with unique codes, types, normal balances, parent/group structure, active state, and posting versus heading/control distinctions. Block hierarchy cycles and cross-institution links.
- [ ] B02 Define actual cash, bank, mobile-money, clearing/suspense, loan principal, interest receivable, approved fee, allowance, payable, funding, capital, income, expense, and fixed-asset accounts as applicable. Borrower contributions are not capital; owner/shareholder equity is distinct.
- [ ] B03 Use branch dimensions within an institution's books. Preserve first-class historical branch references; a client moving branches must not silently move old loan/journal visibility.
- [ ] B04 Implement draft/approved/posted/reversed journal states, source references, evidence, effective date, recording timestamp, currency, policy version, and responsible staff. Deactivate referenced accounts instead of deleting history.
- [ ] B05 Enforce positive valid amounts, allowed decimal precision, balanced debit/credit totals, institution consistency, valid accounts, and open accounting periods in both service/database protections as appropriate. Verify end-to-end decimal precision, including JSON parsing/imports.
- [ ] B06 Post a source event once using scoped idempotency and source uniqueness. Commit local journal, relevant subledger changes, audit, and outbox together; no external/file work under posting locks.
- [ ] B07 Preserve original posted entries and use linked reversals/adjustments with reasons and independent checking. Block generic manual journals from bypassing loan control accounts or rewriting loan balances.
- [ ] B08 Implement reviewed opening-balance imports with evidence, validation/preview, duplicate controls, maker/checker approval, and reconciliation to source records. Do not fabricate legacy repayments or use an unexplained plug to force balance.
- [ ] B09 Bridge existing operational clearing entries to approved GL accounts without editing/deleting historical journals or posting the same source twice. Reconcile the cutover boundary explicitly.
- [ ] B10 Reject or quarantine incomplete legacy loans/accounts from authoritative totals; disclose coverage and unavailable balances. Missing history is not zero.
- [ ] B11 If an external GL is selected, use a transactional outbox, acknowledged idempotent delivery, mapping versions, retry/reconciliation states, and visible failures. Do not call an undelivered export a posted external GL entry.

## C. Loan And Business Accounting

- [ ] C01 Map confirmed ordinary disbursements to loan principal and the actual payment/clearing account; approval alone is not a payment. Reconcile principal to the loan subledger.
- [ ] C02 Allocate verified repayments to due interest, approved outstanding fees/charges, and due principal under the approved applicable rules. Preserve the receipt, effective date, channel reference, and linked source entries.
- [ ] C03 Implement approved accrual/recognition and collection mappings without double-counting income. Principal receipts reduce assets; unearned future scheduled interest is not automatically revenue.
- [ ] C04 Implement unmatched money, advance payments, refunds, and full/partial early settlement with approved liability/suspense treatment, recalculation, audit, and reconciliation. Do not issue false loan receipts for rejected money already received.
- [ ] C05 Implement top-up/internal settlement using explicit source-loan settlement and net new cash movement. Do not fabricate a bank receipt or mark the source paid merely because the top-up was approved.
- [ ] C06 Implement approved restructuring, impairment/provisions, write-offs, and post-write-off recoveries with preserved contractual history. Keep regulatory provision calculations distinguishable from financial-reporting impairment; write-off is not automatic debt forgiveness.
- [ ] C07 Record suppliers, expense invoices/payables, direct expenses, staff reimbursements, partial payments, credits, and reversals with evidence and approvals. Separate expense recognition from paying an existing payable.
- [ ] C08 Record owner capital, distributions, institutional borrowing, lender repayments, and funding interest with supporting decisions. Funding principal is not operating income or expense.
- [ ] C09 Add a fixed-asset register, approved depreciation/disposal entries, prepayments/accruals, and tax liabilities where applicable. Full payroll/tax filing integrations are separate approved scope; their relevant liabilities still need accounting.
- [ ] C10 Agree allocation rules for shared/head-office expenses and branch transfers. Use balancing internal accounts and eliminations so institution totals do not double-count internal movements.

## D. Reconciliation And Period Closing

- [ ] D01 Import approved bank/mobile-money statement formats with source account, dates, amounts, references, statement opening/closing balances, file provenance, bounded size, and idempotent import.
- [ ] D02 Match receipts, disbursements, transfers, settlements, and charges using controlled rules. Support reviewed splits/batches; never double-count gross collections and channel settlement.
- [ ] D03 Provide unmatched, duplicate, partial-match, reversed, timing-difference, and channel-fee exception states with assigned review, evidence, and independent approval. Imports must not silently create money or delete unmatched items.
- [ ] D04 Reconcile physical cash and cashbook, bank/mobile-money accounts, clearing balances, loan subledger versus GL controls, supplier/funding balances, and openings; retain reviewed differences.
- [ ] D05 Implement close checks, approved adjustments/accruals/provisions, reviewer sign-off, closing snapshots, and period locks. Later corrections require controlled reopening or an approved subsequent-period adjustment.
- [ ] D06 Define backdating/effective-date treatment and report restatement versions. Preserve what was reported before late corrections; never silently change a finalized report.
- [ ] D07 Make closing and posting concurrency-safe: a transaction cannot slip into a period after it is locked. Restrict override/reopening permissions and log the full decision.

## E. Financial And Management Reports

- [ ] E01 Produce trial balance and general-ledger account activity from posted entries, with opening, debit/credit movement, closing balance, source drill-down, and explicit dates.
- [ ] E02 Produce Profit and Loss, Balance Sheet, Cash Flow, Changes in Equity, and supporting notes/schedules using approved recognition and account mappings. Internal transfers must not inflate cash flow.
- [ ] E03 Include gross loan principal, interest receivable, allowances/net loans, actual cash/bank balances, payables, funding, capital, recognized income, and expenses in the correct statement sections. Unknown openings prevent a finalized complete statement.
- [ ] E04 Produce loan portfolio, disbursements, collections, ageing/arrears, PAR, classification/provision, write-off/recovery, reconciliation exceptions, and authorized branch-performance reports using verified sources.
- [ ] E05 Publish metric definitions: principal versus total outstanding, paid versus scheduled, effective versus recorded date, days past due, PAR threshold/denominator, and reporting coverage. PAR uses exposed outstanding principal, not just overdue instalment amounts or an average of branch percentages.
- [ ] E06 Validate current regulator-prescribed loan classifications/provisions and report formats separately from management ageing buckets. Do not label a customizable management report an official regulatory return.
- [ ] E07 Reconcile reports to each other and their sources: trial balance balances; assets equal liabilities plus equity; loan controls equal subledger totals; cash flow agrees with the approved cash definition; equity movements agree with accounts.
- [ ] E08 Support comparative periods and approved budgets where implemented, branch dimensions, TZS context, English/Kiswahili labels, accounting basis, as-of dates, reconciliation status, and draft/final status. A generated report is not an audited opinion.

## F. Operational Report Builder

- [ ] F01 Create an approved dataset catalog. Declare each dataset's source, row grain (loan/payment/journal line), date semantics, availability, lineage, permissions, and stable field/metric versions.
- [ ] F02 Create a typed field catalog with labels/descriptions, sensitivity, permitted filters/operators, sorting/grouping, units/precision, null treatment, and approved aggregations. Do not expose arbitrary database tables or internal identifiers by default.
- [ ] F03 Let designers select/reorder/show/hide columns, customize safe labels/widths/formats, choose authorized filters, sort/group, and add approved totals. Prevent label changes from hiding currency, period basis, or a metric's meaning.
- [ ] F04 Support safe logo/title/footer, English/Kiswahili, portrait/landscape, repeated headers, page numbering, and PDF column overflow handling. Use owned assets; no arbitrary remote URLs, executable templates, SQL, HTML, or scripts.
- [ ] F05 Store institution-owned templates with creator, visibility, stable field IDs, validated configuration/schema, version, draft/published/retired state, and approval history. Keep approved system templates available to clone.
- [ ] F06 Separate design, publish, run, export, and sharing permissions. Sharing a template never grants dataset or branch access; enforce record/field authorization at preview, generation, job execution, and download.
- [ ] F07 Validate configuration on the server: supported fields/types/operators, allowed groupings/aggregates, maximum columns/date spans, safe text, decimal precision, and consistent units. Reject unsupported definitions rather than silently replacing them.
- [ ] F08 Use allowlisted query structure and parameterized values with mandatory trusted institution/branch restrictions; no user-entered SQL, joins, or unrestricted formulas. Prevent multi-payment/guarantor joins from multiplying loan amounts.
- [ ] F09 Show bounded previews, totals computed across the authorized filtered result rather than the current page, empty/error/loading states, and explicit data completeness. Preview and export must use the same validated definition and cutoff.
- [ ] F10 Save new immutable versions on publication; record dependencies. Reject or explicitly migrate incompatible retired fields/metrics while retaining the old definition for archived runs.
- [ ] F11 Deliver Collections, Disbursements, and Loan Portfolio templates first. Do not offer ledger/financial fields before their source and scope are ready.

## G. Financial Statement Designer

- [ ] G01 Provide a separate row-based designer with headings, approved account groups, subtotals, notes, comparison columns, display signs, and units; do not model financial statements as arbitrary loan lists.
- [ ] G02 Version and approve account-to-line mappings. Check omitted or overlapping mappings, double-counting, invalid signs, and undefined accounts; exceptions require explicit approved treatment.
- [ ] G03 Allow only controlled subtotal/ratio expressions over approved lines, with type checking, bounded depth, cycle detection, divide-by-zero handling, and stable calculation versions. No arbitrary evaluation or SQL.
- [ ] G04 Preserve statement reconciliation when rows are moved, renamed, collapsed, or hidden. Required disclosures and reconciliation totals cannot disappear from finalized statements.
- [ ] G05 Keep official regulatory templates separately versioned and protected from institution edits to prescribed fields/calculations. Record approved submission format, period, deadline, validation, reviewer, file, and corrections; initial manual export is acceptable where permitted.

## H. Report Execution, Security, And Release Tests

- [ ] H01 Persist runs with institution, authorized scope, requesting user, template/dataset/metric/mapping versions, parameters, effective cutoff, recorded-data cutoff, status, and source/reconciliation coverage. Use a coherent data snapshot across multi-query results.
- [ ] H02 Retain finalized result/artifact versions and approved restatements; record checksum, generation time, and approval. Rerunning an old template against today's data is not reproduction of an old financial report.
- [ ] H03 Generate screen, XLSX, CSV, and PDF from consistent typed results; preserve cents, dates, group/overall totals, null/unknown states, and signs. Escape output and prevent spreadsheet-formula injection from user text.
- [ ] H04 Queue large exports with bounded pagination/streaming, institution/user quotas, indexes, batch sizes, time/memory/row limits, cancellation, retry controls, and protected artifact retention. Preserve authorization when permissions change; no unbounded portfolio `findAll()` or long posting locks.
- [ ] H05 Protect cached results, jobs, previews, artifacts, and downloads with institution/scope/field-aware keys and access checks. Audit design/publication/run/export/download without logging unnecessary borrower data.
- [ ] H06 Test exact/partial payments, remaining cents, zero-interest loans, high amounts, fees, accrued/unaccrued interest, advances, refunds, early settlement, top-up settlement, write-offs, recoveries, and matching reversals.
- [ ] H07 Test duplicate requests/imports, changed-payload retries, concurrent posting/reversals, failed journal/audit/outbox writes, worker retries, close-versus-post races, backdating, reopening, and report restatements against disposable PostgreSQL.
- [ ] H08 Test hostile template/filter/field IDs, tenant/branch tampering, own versus foreign clients, sensitive-field requests, sharing/guessed download IDs, revoked permission, cached results, queued jobs, SQL/formula injection, and cross-institution mappings.
- [ ] H09 Test joins/grouping without double-counting, empty and incomplete datasets, totals across pagination, division by zero, inconsistent units, invalid/cyclic statements, old template versions, and preview/export equivalence.
- [ ] H10 Test every affected JSP flow in English/Kiswahili at 360, 768, 1366, and 1920 pixels: navigation, filters, keyboard column ordering, preview, publication, queues, errors, downloads, wide reports, and print layouts. No page overflow or misleading success states.
- [ ] H11 Verify existing login/logout, CSRF, ownership, guarantor/quorum/approval, quotes/contracts, disbursement, receipts, and repayment protection tests still pass. Run `mvn test` and `mvn package`; record exact results and skipped opt-in coverage.
- [ ] H12 Measure representative posting/report concurrency with realistic portfolio sizes; inspect query plans, export memory, worker fairness, and request latency. Maintain a path to 1,000 requests/second but make no throughput claim without reproducible measurements.
- [ ] H13 Exercise migration, reviewed cutover, backup/restore, replay/recovery, incident handling, and restricted rollout on synthetic data before any operational data changes.
- [ ] H14 Obtain institution accountant/compliance approval and staff acceptance of sample financial statements plus at least two different institution report layouts. Resolve or explicitly gate every unexplained reconciliation difference.

## Scope Boundaries

- Start with verified TZS accounting and the existing individual-loan workflow. Multi-currency, group/agricultural accounting, full payroll, tax filing, external-channel APIs, and arbitrary BI/database exploration need separate approved scope.
- CSV/statement import and controlled manual evidence can precede a channel API; reconciliation and truthful status cannot be deferred because an API is unavailable.
- This goal does not complete all client KYC, verified underwriting, legal agreements, complaints, privacy, or digital-lending gates in the conversion backlog. Unrestricted live lending remains subject to those separate requirements.

## Overall Definition Of Done

- [ ] Approved account/policy mappings and reviewed openings exist for each enabled institution; incomplete institutions remain visibly restricted.
- [ ] Financial events are balanced, idempotent, append-only, scoped, auditable, and reconciled across loan subledger, official GL, and money accounts.
- [ ] Expenses, funding/capital, accruals, impairment, settlement, corrections, and closing work under approved policies; no silent edits to posted history or closed periods.
- [ ] Financial statements reconcile and drill down to sources; finalized versions remain reproducible after later corrections.
- [ ] Two institutions can publish different report layouts over the same approved definitions without accessing each other's records or changing financial meaning.
- [ ] Operational builder, statement designer, protected regulatory templates, preview/export, and publication/version workflows pass their security, accuracy, localization, and responsive tests.
- [ ] Build/integration/rendered/recovery/performance evidence and human approval are recorded, and outstanding broader conversion gates are disclosed. Do not mark this implementation goal complete for a partial increment.

## Agent Handoff Prompt

> Implement the Accounting Package And Report Customization Goal in `docs/accounting-report-builder-checklist.md`. Read `AGENTS.md`, the conversion backlog, and current ledger/credit increment documents first. Audit current code and work in dependency order through small verified increments. Preserve institution/branch isolation, contracts, session/CSRF security, append-only records, and compatibility identifiers. Reuse the existing JSP/UI/export conventions. Record implemented item IDs, tests, commits, required accountant/compliance decisions, and remaining gates after each increment. Never invent opening balances, accounting policy approval, or compliance evidence. Do not call the goal complete until its overall definition of done is satisfied.

## Evidence Log

For each implementation increment append: date; item IDs; commit; migrations; automated commands/results; disposable database tests; rendered widths/languages; financial reconciliation evidence; security/performance/recovery evidence; reviewer decision; unresolved gates.

No implementation completion is asserted by the creation of this checklist.

### 2026-10-04 — Delivery coordination and authorization foundation

- The user requested a separate agent/worktree for every phase A–H. Eight managed worktrees have been created. A (policy), B (ledger), and F (operational builder) are running first; dependent phases will follow as execution slots and prerequisite contracts become available.
- The user confirmed that approved institution policies and verified opening-balance evidence are unavailable, and explicitly instructed that activation remain gated. No institution/accountant/compliance approval or opening amount has been inferred from this implementation request.
- Baseline at `20ae9ad`: `mvn test` passed with 747 tests, zero failures/errors, and five opt-in PostgreSQL tests skipped. Those five tests subsequently passed against a fresh disposable PostgreSQL 17 instance at loopback port 55449, with all 41 baseline migrations applied. The operational datasource was not used.
- Commit `47af6f0` adds explicit policy, account, journal, opening, cutover, period, template, and report-run claims with no automatic role grants; a staff-only scoped finance route gate preserves session/CSRF controls. Focused permission/login/CSRF checks: 26 tests, zero failures/errors/skips. Policy foundation commit `87b7bd5` is integrated as `18323de`; its phase-specific verification is pending.
- No checklist phase or overall acceptance gate is marked complete by this coordination entry. Full financial, rendered, security, recovery, performance, and human review evidence will be recorded separately as increments are verified.

### 2026-10-07 — Direct accountant COA onboarding

- User instruction supersedes approval requirements for COA setup: the authorized accountant creates active main groups, subgroups, account families, and posting/control accounts directly. Existing journal, repayment, policy, opening, reconciliation, and closing processes are outside this increment. No opening amounts or accounting-policy decisions were inferred.
- Partial B01/B02 foundation: institution-owned six-digit hierarchy, parent-derived classification, bilingual metadata, contra balances, account lifecycle protections, audited atomic setup, and bounded registry/parent search. Architecture and usage are documented in `docs/chart-of-accounts-architecture.md`. The activity/transaction library is an architecture proposal only.
- Migration V51 adds optional account metadata and lookup indexes, and supplies the three COA claims to existing active scoped accountants while preserving other claims. Session refresh requires signing in again. Current explicit claim revocation remains authoritative.
- Verification: `mvn -DskipTests compile` passed. The final `mvn -Dtest=GeneralLedger*Test,ChartOfAccounts*Test,FinanceWorkspaceSecurityTest,LoginCsrfAccessDeniedTest,UserClaimTest,UserClaimServiceTest test` passed with 55 tests, zero failures/errors/skips, including Java 25 recompilation, both JSP views with shared includes, the V50-to-V51 permission upgrade, and 17 live ledger/hierarchy/concurrency/rollback tests. Both disposable PostgreSQL 17 databases applied all 67 migrations through V51; the operational datasource was not used. `git diff --check` passed.
- The broader suite was interrupted after 15 minutes; it had reported 405 tests, zero assertion failures/errors, and 210 skipped checks before interruption. This is not a full-suite pass. Packaging was not rerun. Commit: the local commit containing this evidence entry, titled `Add direct accountant chart-of-accounts onboarding`.
- Browser timeouts prevented final rendered English/Kiswahili and mobile-width checks. Responsive CSS and localized labels are implemented; rendered verification remains pending. No throughput benchmark, recovery exercise, financial reconciliation, or overall accounting-package completion is claimed.

### 2026-10-07 — Direct accountant activity and transaction library

- Partial B01/B02 configuration foundation: institution-owned activities with multiple transaction codes, direct accountant creation/deactivation/reactivation, bilingual names, accounting-event selection, and immutable versioned debit/credit templates. The user authorized accountant setup without approval workflows. This library saves definitions only; existing posting, repayment, opening, policy and reconciliation processes are not changed.
- V52 creates the activity, transaction-code, template-version and template-line tables with tenant foreign keys, unique codes, immutable identities/history, active posting-account checks and deferred component balance/version checks. Shared COA claims, current actor/branch validation, session authentication, CSRF, scoped 25-row pagination, allowlisted fields, audit rollback and stale-revision/idempotent-retry protection remain authoritative.
- Verification: `mvn -Dtest=AccountingCodeLibrary*Test,ChartOfAccountsJspCompilationTest,ChartOfAccountsMvcTest,GeneralLedger*Test,FinanceWorkspaceSecurityTest,LoginCsrfAccessDeniedTest test` passed with 56 tests, zero failures/errors/skips, including eight live library PostgreSQL tests, 17 existing live ledger tests, MVC/security tests and all six accounting JSPs. After the final browser-driven JSP/JavaScript/label corrections, `mvn -Dtest=ChartOfAccountsJspCompilationTest test` passed again. Java 25 compilation passed in the focused run. All 68 migrations applied to fresh disposable PostgreSQL 17 databases for library, ledger and rendered UI checks; the operational datasource was not used. `git diff --check` passed.
- Rendered synthetic-data checks: activity and transaction creation, balanced template save, immutable history display, current-version prefill, dynamic pair add/select/remove, login and logout succeeded. Activity/transaction registries, both code forms, template editor and version detail were checked in English and Kiswahili at 360, 768, 1366 and 1920 pixels. Document width stayed within the viewport; wide tables scroll inside their containers. Browser checks corrected UTF-8 rendering, code labels and cloned enhanced-select behavior.
- Commit: the local commit containing this entry, titled `Add accountant activity and transaction code library`. Full-suite testing and packaging were not rerun for this increment. No money was posted by the library, and no throughput benchmark, operational cutover, recovery exercise, financial reconciliation or overall accounting-package completion is claimed. Future journal integration must retain the selected immutable version and verified source amounts; see `docs/chart-of-accounts-architecture.md`.

### 2026-10-07 — Preserve applied V52 migration history

- Reproduced the reported `jpaSharedEM_entityManagerFactory` startup failure against the configured local PostgreSQL database. Its root cause was Flyway V52 checksum validation: the database had applied checksum `1498718278`, while the corrected file resolved to `-1474970041`. V52 had been applied during development before its trigger correction was finalized.
- Restored the exact original V52 checksum and moved the trigger correction to forward migration V53. Separate PL/pgSQL branches select fields from the version and line trigger records. Balance, active-account, tenant, immutable-history and current-version checks remain in place; no validation bypass, schema-history repair, or financial-record changes are introduced.
- `mvn -Dtest=AccountingCodeLibraryPostgresTest test` passed: nine tests, zero failures/errors/skips. A fresh disposable PostgreSQL 17 database applied the 68 original migrations through V52, verified its recorded checksum, then applied V53 normally. Tests cover both trigger record shapes, component balance rejection, tenant isolation, version immutability, audit rollback and concurrent/idempotent saves. Java 25 test compilation passed. Full-suite testing, packaging, rendered UI and throughput benchmarks were not rerun for this migration-only fix.
- Configured local PostgreSQL 18.3 startup succeeded: V52 validated without history repair, V53 applied, JPA initialized and Tomcat started on port 8081. `/login` returned HTTP 200 with the password input. SMS, outbox scheduling and retention cleanup were disabled only for this verification launch. The verification process and disposable test database server were stopped afterward to leave the normal development launcher free.
- Commit: the local commit containing this entry, titled `Preserve applied accounting migration checksum`. The user's existing server-port configuration edit is excluded.

### 2026-10-07 — Transactions Config registers and modal onboarding

- Partial B01/B02 UX increment: Transactions Config has Activities and Transactions sidebar children. Each register has a top-right Add New button and the shared modal shell. Transaction onboarding selects an activity code and saves the transaction plus its first debit/credit template atomically. Success remains in the modal until X closes it; validation preserves the entered fields. Subsequent immutable template versions and lifecycle controls remain available. No approval workflow or monetary posting is added.
- COA and library onboarding now use one Name input, retaining other fields and existing stored Kiswahili metadata. Contextual breadcrumb ancestors link to their pages. Breadcrumbs and page headers are direct children of the content frame, enabling the existing shell promotion and sticky breadcrumb styling below the top bar and above the title. English and Kiswahili navigation, modal labels and validation messages are localized.
- Verification: `mvn -Dtest=AccountingCodeLibrary*Test,ChartOfAccountsJspCompilationTest,ChartOfAccountsMvcTest,FinanceWorkspaceSecurityTest,LoginCsrfAccessDeniedTest test` passed with 37 tests, zero failures/errors/skips, including 11 live disposable PostgreSQL library tests. Coverage includes atomic creation/rollback, 25-row bounded registers across activities, institution isolation, current claims, CSRF, binding limits and field preservation. Java 25 compilation and `git diff --check` passed. After the final breadcrumb placement correction, `mvn -Dtest=ChartOfAccountsJspCompilationTest test` passed again: one test compiling all six accounting JSPs, zero failures/errors/skips.
- Rendered verification was attempted against a disposable institution/database on port 8087. The application started and the staff login rendered, but repeated browser-control timeouts prevented completion of modal, scrolling and viewport checks. No responsive/rendered pass or screenshot is claimed for this increment. The verification app was stopped. Full-suite testing, packaging, money reconciliation and throughput benchmarks were not rerun.
- Commit: the local commit containing this entry, titled `Improve transaction configuration onboarding workflows`. The user's existing server-port configuration edit is excluded. No database migration is required and broader accounting release gates remain unchanged.

### 2026-10-07 — Activity-filtered transaction navigation

- Activity row Transactions actions now open the canonical Transactions register with `activityId` selected automatically. The activity context is shown in a visible Activity filter alongside search/status; the separate line above the register is removed. Search and pagination retain scope, All removes activity scope, and Clear resets filters. The selected activity remains available even outside the bounded first page of filter options, including inactive activities.
- Existing activity-detail links redirect after institution ownership verification, retaining search/status/page context. Existing scoped transaction queries, claims, modal creation and immutable templates remain unchanged. No migration or financial mutation is introduced.
- `mvn -Dtest=AccountingCodeLibraryMvcTest,ChartOfAccountsJspCompilationTest test` passed: 17 tests, zero failures/errors/skips, including all six JSP compilation checks. Coverage includes the redirect, preselected activity model, search/page persistence, empty/all activity selection and foreign-activity denial. Java 25 compilation and `git diff --check` passed. Live browser verification remains pending: the available Chrome connection reported the user unavailable and the earlier disposable UI server is stopped. No mobile/rendered pass is claimed.
- Commit: the local commit containing this entry, titled `Show activity scope in transaction register filters`. The user's existing port configuration edit is excluded; broader accounting acceptance gates are unchanged.

## Official Review References

Reviewed for planning on 2026-10-02; verify amendments and institution-specific instructions again at implementation/release.

- [BoT Mainland Tier 2 regulations, 2019](https://www.bot.go.tz/Publications/Acts%2C%20Regulations%2C%20Circulars%2C%20Guidelines/Regulations/en/2020021122490967551.pdf): regulations 24-27 address records, annual accounts, financial statements, and reconciliation oversight; 42 covers allocation and early repayment; 45 covers classification/provisions; 50 leaves periodic-report format/frequency to BoT instructions. Software output is not an external audit.
- [NBAA Technical Pronouncement No. 1 of 2018](https://www.nbaa.go.tz/2019/march/techpro2019.pdf): financial-reporting framework depends on entity classification. Obtain the accountant's documented applicability decision instead of assuming every non-deposit-taking lender uses IFRS for SMEs.
- [NBAA Technical Pronouncement No. 1 of 2026](https://www.nbaa.go.tz/uploads/text-editor/files/Technical_Pro_1_2026_1787305313.pdf): sustainability-reporting amendments are effective for annual periods beginning 2027-01-01, with early adoption encouraged. Review applicable governance disclosures separately; this checklist does not mandate a new standalone sustainability module for every institution.
- [BoT fees guidelines, 2024](https://bot.go.tz/Publications/Acts%2C%20Regulations%2C%20Circulars%2C%20Guidelines/Guidelines/en/2024071716375097.pdf): verify permissible fees and actual third-party costs before activating legacy/new fee mappings, following the review gates in the conversion backlog.
- [OWASP authorization guidance](https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html) and [SQL injection prevention](https://cheatsheetseries.owasp.org/cheatsheets/SQL_Injection_Prevention_Cheat_Sheet.html): validate access on every request and use controlled query structure with parameterized values.
