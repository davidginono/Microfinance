# Guarantor capacity, collateral and collections

Implementation date: 27 October 2026. This increment implements the operational capabilities identified as G03, G04 and G06 in the [research audit](microfinance-research-implementation-audit-2026-10-27.md). It is not completion of verified borrower onboarding, all underwriting, statutory recovery proceedings or the accounting backlog.

## 1. Scope and activation

The sidebar has **Guarantor capacity**, **Collateral**, **Collections** and **Branch credit controls**. All routes are under `/finance/credit`. Staff use their selected institution and branch; request fields cannot choose a different scope. Staff with only one of the new view claims have a corresponding login destination.

Assign the required new claims explicitly through the existing access editor. No default role bundle gains them:

| Workspace | Claims |
| --- | --- |
| Branch credit controls | `CREDIT_CONTROLS_VIEW`, `CREDIT_CONTROLS_CONFIGURE` |
| Guarantor capacity | `GUARANTOR_CAPACITY_VIEW`, `GUARANTOR_CAPACITY_CREATE` |
| Collateral | `COLLATERAL_VIEW`, `COLLATERAL_CREATE`, `COLLATERAL_UPDATE`, `COLLATERAL_RELEASE`, `COLLATERAL_RECOVERY` |
| Collections | `COLLECTIONS_VIEW`, `COLLECTIONS_CREATE`, `COLLECTIONS_UPDATE` |

Commands require their workspace's view claim as well as the action claim. The access matrix maps collateral release to its Approve action and recovery-decision recording to its Initiate action. Existing platform/staff scope restrictions remain.

In **Branch credit controls**, record the institution's approved policy reference and confirm the evidence check. Independently enable ordinary guarantor assessments and registered collateral for secured products. Optional instalment reminders require an explicitly entered lead time (0–30 days) and repeat interval (1–30 days). New branch controls and reminder delivery are not silently activated or populated with invented policy decisions. Their absence is not evidence of verified creditworthiness.

## 2. Architecture and operating paths

The feature lives in `com.sacco.mvp.lending.controls`, with request/view DTOs, a controller, an owning transactional service and a scoped JDBC repository. The reminder service owns short outbox-queuing transactions; the existing scheduler and publisher handle scheduling and delivery. JSPs reuse the ERP shell, resource tables, native forms, searchable choices and discard protection. There is no new frontend framework or lending engine.

### Guarantor capacity

1. Create/select the guarantor through the existing loan application workflow. Its request appears in the scoped capacity register.
2. Record verified monthly income, expenses and total monthly debt payments, the approved **total monetary guarantee limit**, assessment/expiry dates, supporting evidence and the approved policy reference. Zero expenses/debt require an explicit entered zero; missing inputs do not pass. An officer cannot assess their own guarantee.
3. The approval path rechecks current assessment validity, available income, known ordinary/shared debt service, existing guarantees and shared contingent liability. Count limits retain their existing meaning; the old amount-named count setting is not repurposed as money.
4. Local and external guarantor identities reserve capacity under scoped locks. External identities use normalized institution/external-branch/client-number identity, not an assumed clean integration response. Unresolved foreign-branch or missing supported history blocks the required check.
5. A successful new approval retains a permanent link to the assessment version and its contractual maximum liability. Later assessment revisions do not rewrite that evidence. Existing approved guarantees are not backfilled with fabricated verification.

Where a current assessment already exists, its limit is respected even if the branch does not require assessments for every ordinary request. Existing group assessment, consent, reservation and liability rules remain authoritative; new ordinary assessments also constrain subsequent group/personal guarantees where applicable. The existing stricter personal-guarantee budget remains protected.

The assessment's total limit is an institution-approved amount supplied by the authorized officer. Software does not invent a salary multiplier or automatically verify external income, identity or credit-bureau history. Known internal instalments cannot be omitted merely by entering zero declared debt. Review evidence must cover total debt without adding the same commitment twice.

### Collateral

Register the asset's stable reference, description, owner and checked ownership evidence. References are normalized and unique within the institution. Record valuations, other secured claims, validity and subsequent custody or recovery-decision evidence as append-only events.

Pledges reserve specific verified value for an authorized loan. The sum of active pledges cannot exceed the latest valuation less other secured claims. Concurrent or stale requests cannot commit the same available value twice. Revaluations may record an actual decrease below existing pledges: the shortfall is visible and further excessive pledges/funding are blocked, rather than hiding the lower valuation.

When the branch enables the secured-product gate, funding requires current registered coverage against the product's required ratio. New individual and recorded applications retain collateral terms in their policy snapshots. Older applications without those snapshot fields use the existing product fallback; an unavailable required ratio blocks the gate rather than inventing a percentage. Review their actual approved requirements before activating the gate. Funding holds the loan/policy/asset locks while checking and posting, with a database transition guard as a second protection.

Release requires a settled or cancelled/rejected/reversed loan, a reason and a different authorized officer from the pledge recorder. A `PAID` flag alone is insufficient: supported ledger settlement, instalments and charges must agree. Releasing security never rewrites the asset's original record or financial history. A later financial reversal cannot physically or legally reclaim released security automatically; staff must review the resulting position.

Recovery records retain a reviewed decision and evidence for an eligible delinquent pledged loan. They do not initiate repossession, auction, sale, fees or cash posting. Evidence fields retain checked document/reference details; this increment does not add a separate collateral document-upload repository, statutory notice generator, lien registry API or approved substitution/auction workflow.

### Collections

Choose a ledger-backed loan with unpaid overdue instalments, assign an active officer in the branch and set its next action. One open case per loan is enforced under the loan lock. The case and each follow-up retain their original evidence and actor.

Record contacts, promises, disputes, served-notice evidence, assignments, dispute resolutions, closing and reopening. Promises have amounts and dates; they do not reduce balances. Disputes survive ordinary contact updates until explicitly resolved/closed and pause automatic reminders. Closing a case never marks the loan paid. Reopening cannot create a second open case.

Principal, due interest, future interest, outstanding charges, overdue instalments and due dates come from supported ledger projections. Partial payments and independent linked reversals appear in current arrears. Unsupported/unknown balances remain unavailable. Instalment arrears are distinguished from separately displayed charges; no new fee due dates or late penalties are assumed.

Configured reminders use the earliest unpaid instalment, including overdue loans, rather than final maturity only. Dates use `ApplicationClock`. Deduplication covers a loan's existing reminders and the configured interval. Each short transaction queues a reminder and its outbox event together. Delivery remains with the existing notification publisher; a queued reminder is not proof of contact or received cash. The batch is 200 records, with at most ten batches per scheduled run. This replaces the old maturity-only scheduler; branches must explicitly configure reminder delivery. Organization-only borrowers without a personal loan recipient are not assigned an invented notification recipient.

## 3. Security review

All reads and commands retain session authentication, CSRF and current claim checks. The owning service rechecks active staff, institution and branch access through the existing access service. Loan scope comes from `LoanApplication.stationId`; a client moving branches does not move historical loan visibility. Evidence and registries are staff-only. Borrowers continue using their existing limited guarantor decision routes; no assessment income/expense records are exposed to them.

Binders allowlist editable fields. Actor, tenant, branch, history, balances and status are derived on the server. Templates escape names/evidence and both English and Kiswahili labels are present. Existing approval/funding pages translate the new safe error codes. No operational export, cash-posting permission or automatic role grant is introduced.

## 4. Database and query review

`V132__credit_capacity_collateral_collections.sql` adds policy revisions, guarantor assessments and approval evidence, collateral assets/events, collection cases/events and reminder deduplication records. Views project current valuations/pledges; functions guard capacity and funding. Forward migration preserves legacy identifiers, contracts, approvals and all existing money records.

Evidence rejects updates, deletes and truncation. Institution/branch and source foreign keys, uniqueness, amount/date checks and scoped insert guards provide additional protection. Metadata corrections use new revisions/events; a new financial journal or repayment is never created by a case, promise, valuation or pledge.

Registers, choices and histories use 25 rows plus lookahead; request pages are capped. Sort keys map to explicit server expressions before pagination. Searches remain scoped. The capacity register avoids calling the detailed exposure function for every row; selectors do not calculate unused financial histories. A partial unpaid-instalment index supports reminder selection. Representative production-size query plans and saturation measurements remain a separate verification task.

## 5. Transactions, concurrency and scheduling

Scoped request locks and payload hashes return exact retries and reject changed reuse. Capacity locks reuse the existing guarantor serialization; assessment revisions and decision references are immutable. Funding and collateral operations use loan → asset order, with narrow locks and stale-sequence checks. Case creation/reopening serializes on the loan. Audit failures roll back metadata writes.

The scheduler calls a separate transactional reminder bean, avoiding proxy self-invocation. Candidate rows use `SKIP LOCKED`; interval checks and unique reminder keys prevent duplicate queuing. No per-request executor, unbounded queue or network/file operation is introduced under financial locks. Notifications still run through the existing publisher after commit.

## 6. Files changed

- New backend: `src/main/java/com/sacco/mvp/lending/controls/`.
- New views/assets: `src/main/webapp/WEB-INF/jsp/credit/`, `credit-controls.js`, `credit-controls.css`.
- Migration, bilingual bundles, shared sidebar and claim/landing definitions.
- Existing owning loan repositories/services and approval/funding error handling integrate the new transition guards; financial posting calculations and routing are reused.
- New PostgreSQL/MVC tests, the recording test fixture, the manager clock fixture and two stale test-harness assumptions: the removed JSP directory and the existing ninth group permission. JSP discovery now includes all new credit views.

## 7. Verification commands and results

Validation is performed against a fresh disposable PostgreSQL 17.9 cluster at loopback port 55461 and a database named `microfinance_recording_test`. No operational datasource or credentials are used. The test fixture enables all claims only for synthetic staff; this is not a production permission grant.

The focused selection includes `CreditControlPostgresTest`, `CreditControlMvcTest`, recorded loans, group cycles/personal guarantees/coverage/shared exposure, ordinary loan workflow, manager and qualification services, claim assignment and Jasper compilation of the five new JSPs. The test URL is supplied using `MICROFINANCE_RECORDING_TEST_URL`; never point it at operational data.

Affected regression: **255 tests passed, zero failures/errors/skips**, including **151 actual PostgreSQL cases**, seven new MVC/security checks, ordinary workflow/manager/qualification/claim tests and Jasper compilation of the five new JSPs. It includes duplicate/changed retries, competing guarantees and pledges, verified internal debt, external identities, scope/revoked permissions, audit rollback, current valuations, independent release, partial payments/reversals, disputes and reminder deduplication.

After the final missing-collateral-ratio safeguard, the migration was reapplied to a fresh disposable database and all **nine `CreditControlPostgresTest` cases passed** again.

The final `mvn -q test` selected **1,867 tests: three failures, zero errors, 590 skipped**. The remaining failures are existing contracts outside the credit workspaces: `AwsConsoleViewContractTest.authenticatedPagesExposeAwsHeadersAndRegisterContracts` (account versions header), `AwsConsoleViewContractTest.workspaceLabelsAndFiltersUseTheSharedResponsiveLayoutContract` (approval configuration filter), and `TableSortingCoverageTest.everyColumnHeaderTableDeclaresWorkingSortingOrItsFixedOrderPurpose` (existing tables elsewhere). They are not a passing full-suite result. Two stale test-harness assumptions were repaired: a removed JSP directory and the existing ninth group permission.

Follow-up: the reported failures and fixture error are resolved. The new full run passed with zero failures/errors; see [UI-contract and borrower identity regression fixes](ui-contract-regression-fixes.md) for counts, changes and remaining validation limits.

`mvn -q -DskipTests package`, JavaScript syntax validation and `git diff --check` passed. The packaged WAR started against the disposable database on loopback port 8089; the login form and new JS/CSS returned HTTP 200, and the protected credit route redirected unauthenticated requests to login. The temporary app process was stopped after this check. Browser-session acquisition timed out; no authenticated browser, mobile rendering, provider delivery or screenshot acceptance is claimed. Both-language label coverage and actual Jasper compilation are distinct from rendered interaction testing.

## 8. Measured results

A reminder query `EXPLAIN (ANALYZE, BUFFERS)` on the synthetic fixture (188 loans, 195 instalments, seven policy rows) returned one result under the 200-row limit: planning 279.501 ms, execution 11.899 ms. This small fixture is not representative of production volume.

No load, spike or soak benchmark was run. The 1,000-requests/second target remains unproven. Correctness/concurrency results do not establish throughput or institutional policy approval.

## 9. Remaining rollout boundaries

- Explicitly assign new claims and configure the approved branch policy/cadence before using enforcement/reminders. Configure only policies supported by the institution's actual evidence.
- Complete authenticated English/Kiswahili desktop/mobile acceptance when browser access is available.
- Verify the institution's evidence, lien/security terms and recovery procedure independently. The published [BoT Tier 2 regulations](https://www.bot.go.tz/Publications/Acts%2C%20Regulations%2C%20Circulars%2C%20Guidelines/Regulations/en/2020021122490967551.pdf), including collateral, guarantor disclosure and recovery provisions, were consulted; recording a decision here does not certify compliance or authorize physical enforcement.
- Legacy cutover, full verified borrower underwriting, collateral substitutions/sales and their proceeds, new penalties, regulatory submissions and provider reconciliation remain separately controlled capabilities.
