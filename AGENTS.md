# AGENTS.md

This file gives coding agents project-specific guidance for working in `Microfinance`.

## Project Overview

- Project: `Microfinance Loan Management System`
- Target: credit-policy-backed, non-deposit-taking microfinance in Tanzania Mainland.
- Start with individual business lending; salary, group, and agricultural lending are separate product capabilities, not assumed existing features.
- The conversion backlog and implementation gates live in `docs/microfinance-gap-checklist.md`.
- The accounting package and institution-specific report customization goal, dependency order, and acceptance gates live in `docs/accounting-report-builder-checklist.md`. Read it before accounting or report-builder work; checklist creation is not implementation completion.
- Stack:
  - Spring Boot 4.1.x (check `pom.xml` before choosing APIs)
  - Java 25
  - Spring MVC + JSP/JSTL
  - Spring Security
  - Spring Data JPA + Hibernate
  - PostgreSQL
  - Shared local CSS and Tailwind CSS in JSP pages
- Auth model:
  - Session-based authentication
  - Form login
  - CSRF enabled
  - Not JWT-based

## Microfinance Domain

- Use `Institution` for the tenant, `Branch` for its operating location, and `Client` or `Borrower` for the person borrowing.
- Preserve existing implementation identifiers unless a migration is explicitly requested:
  - `sacco_id` / `saccoId` = institution / tenant
  - `station_id` / `stationId` = branch
  - `Member` / `memberId` / `memberNo` = existing client or user record / ID / client number
  - `com.sacco.mvp`, existing routes, permission keys, and localization keys remain compatibility identifiers, not user-facing branding.
- Do not require savings, shares, membership contributions, or deposit loading before a loan application or guarantor approval.
- Assess borrowing using verified income and expenses, business or employment details, active debt, repayment history, affordability, guarantor capacity, documents, and collateral where the product requires it.
- Loan products control limits, interest, tenure, fees, repayment frequency, guarantors, documents, collateral, and the approval path. Do not hardcode one approval path for every product.
- Foresight integrations have been removed. Do not reintroduce their clients, configuration, endpoints, dependencies, or network-based savings checks.
- Local schedules and JSON financial snapshots are not proof of posted repayments. Do not present balances as ledger-backed until repayment posting and reconciliation exist.
- Separate verified information from client declarations and unavailable data. Never treat missing history or an unavailable integration as a clean credit record.
- Research and forum discussions inform product discovery; only verified official sources and the institution's approved policy establish compliance requirements. Recheck current Tanzanian requirements for changes to pricing, contracts, reporting, or recovery.

## Run And Build

- Development run:
  - `mvn spring-boot:run`
- Compile:
  - `mvn -DskipTests compile`
- Full test run:
  - `mvn test`
- Packaged run:
  - `mvn -DskipTests package`

Default local URL:
- `http://localhost:8080`

## Key Application Areas

- Client pages:
  - `src/main/webapp/WEB-INF/jsp/app`
- Admin pages:
  - `src/main/webapp/WEB-INF/jsp/admin`
- Manager pages:
  - `src/main/webapp/WEB-INF/jsp/manager`
- Board pages:
  - `src/main/webapp/WEB-INF/jsp/board`
- Chairperson pages:
  - `src/main/webapp/WEB-INF/jsp/chairperson`
- Shared layout fragments:
  - `src/main/webapp/WEB-INF/jsp/fragments`

## Important Backend Files

- Security:
  - `src/main/java/com/sacco/mvp/config/SecurityConfig.java`
  - `src/main/java/com/sacco/mvp/security/AppUserDetailsService.java`
  - `src/main/java/com/sacco/mvp/security/AppUserPrincipal.java`
  - `src/main/java/com/sacco/mvp/security/AuthzService.java`
- Main client workflow:
  - `src/main/java/com/sacco/mvp/web/AppController.java`
  - `src/main/java/com/sacco/mvp/service/LoanWorkflowService.java`
- Manager workflow:
  - `src/main/java/com/sacco/mvp/web/ManagerController.java`
  - `src/main/java/com/sacco/mvp/service/ManagerService.java`
- Board workflow:
  - `src/main/java/com/sacco/mvp/web/BoardController.java`
  - `src/main/java/com/sacco/mvp/service/BoardService.java`
- Admin:
  - `src/main/java/com/sacco/mvp/web/AdminController.java`
  - `src/main/java/com/sacco/mvp/service/AdminService.java`

## Workflow Notes

- Loan workflow logic is sensitive. Do not change statuses or transitions casually.
- Current loan flow includes:
  - draft
  - sent to guarantors
  - all guarantors approved
  - review by manager
  - review by board
  - approval / rejection paths
- Guarantor and board rules are business-critical.
- Board review quorum and guarantor approval logic must remain consistent with service-layer rules.
- Keep disbursement, repayment, arrears, default, restructuring, and settlement distinct. Do not mark a loan paid merely because its final due date passed or a top-up was approved.
- Monetary mutations need transactional consistency, idempotency, auditability, and concurrency protection. Corrections to posted financial records must preserve history rather than overwrite or delete it.

## Credit Calculation Rules

- Use `LoanAmortizationCalculator` for quotes and newly generated repayment estimates/schedules; do not add separate double-based financial formulas in controllers, JSPs, or presentation services.
- New quotes use `DECIMAL_PERIODIC_V1`. Preserve stored contracts and unversioned legacy period counts; do not reprice a disbursed loan by refreshing its product.
- Derive draft and submission financial snapshots on the server. Borrower-supplied snapshot JSON is not an authoritative rate, principal, repayment, interest, or policy result.
- Distinguish periodic instalments from monthly affordability amounts. Use the largest scheduled payment and normalize weekly payments consistently; retain exact final principal reconciliation.
- A passing declared cash-flow estimate is not verified creditworthiness. Missing or invalid repayment/cash-flow values must fail the required assessment; do not assume missing expenses or debt are zero.
- Disbursement must not silently change assessed repayment amounts, tenure, or frequency. Keep changed terms on an explicit reassessment/approval path.
- Read `docs/credit-estimate-increment.md` for conventions, compatibility, and open policy/underwriting gates.

## Security Notes

- The app uses Spring Security form login, not JWT.
- CSRF is enabled and used in JSP forms and AJAX requests.
- Role access is enforced in:
  - `SecurityConfig`
  - `@PreAuthorize`
  - `AuthzService`
- Do not weaken route or ownership checks without explicit instruction.
- Institution and branch scope must be enforced on the server for reads, writes, exports, receipts, and document downloads. Do not trust hidden form fields or UI filters as access control.
- Clients see their own records; guarantors receive only the information needed to understand and approve their liability.

## Project UI Skills

Read the relevant project-local skills before UI work:

- `.agents/skills/microfinance-ui-governance/SKILL.md`: page flows, navigation, lending information, localization, and financial-action states.
- `.agents/skills/microfinance-ui-style/SKILL.md`: shared ERP shell, controls, forms, modals, and responsive presentation.
- `.agents/skills/microfinance-table-standard/SKILL.md`: client registries, review queues, repayment records, collections, reports, and pagination.

The root `SKILL.md` routes to these maintained instructions. These are the project-specific UI references for this repository; old SACCO-specific skills are not its domain or branding specification. Keep reusable shell patterns without importing SACCO savings, share, or membership assumptions.

## UI Conventions

### Workspace rendering and branding (2026-10-21)

- Use the classic AWS navy top bar (`#232f3e`) and white text-led sidebar, with blue active links and no legacy dark/orange submenu selection. The Microfinance wordmark ends in a tick; the supplier mark uses a transparent SVG without a dark background or dark dot. Institution names come from the scoped registry, never a shared hardcoded tenant name.
- On initial loads and workspace transitions, keep unfinished layouts hidden until their styles and component initialization are ready. Retain individual spinners in measured destination sections; when geometry is unavailable or components are being initialized, place the workspace spinner at the top left. Never flash native/legacy controls, duplicate toolbars or an earlier layout before the current components appear.
- Shared UI initialization uses `WorkspaceNavigation.onReady`, independently of unrelated image/frame/window-load completion. Keep shared styles connected, prepare destination styles before replacing content, and preserve native financial forms, CSRF, permissions, edited values and local error recovery. Apply this contract across all shared-shell workspaces.
- Financial statements use the title Reports Preview and bottom-right Save Draft action. Tab requests replace only the report section, with its own spinner; preserve the workspace, tab bar, view settings and action forms, updating their selected-report context after success.

### Versioned chart of accounts (2026-10-18)

- Account creation, metadata edits, deactivation and reactivation use account versions with institution-wide automatic or independent approval configuration. Save draft retains an incomplete proposal; Save version validates and either approves/applies it automatically or freezes it for a different authorized reviewer. The current account remains effective until approval. This supersedes older direct-only COA guidance.
- Preserve submitted snapshots, reasons, real reviewer decisions and permanent audit events. New configuration/review claims require explicit assignment. Account codes, classification, balance type, purpose and hierarchy retain their existing immutability protections. Existing account baselines do not establish historical approval; old journal lines without version evidence remain unchanged. See `docs/account-version-approvals.md`.

### Configurable voucher approvals (2026-10-18)

- The user's newer instruction enables approval for **individual ordinary receipt/payment/journal vouchers and batches according to branch configuration**. This supersedes the no-approval restriction below only for configured scopes. Preserve direct posting when individual approval is disabled and preserve specialized loan receipt/repayment safeguards. All workspaces and actions require explicit claims; do not grant them automatically. See `docs/voucher-approval-workspaces.md`.

### Payroll schedule Excel export (2026-10-21)

- The user authorizes employer-ready Excel exports of frozen payroll deduction schedules alongside PDF/print. Preserve the frozen snapshot, institution/branch scope and explicit payroll VIEW/EXPORT claims. Exporting never posts repayments or records employer results. This exception does not enable Excel/CSV for vouchers or other operational reports. See `docs/payroll-deductions.md`.

### Core accounting mandate (2026-10-08)

- Receipt vouchers, payment vouchers and journal vouchers support multiple transactions and accountant **direct posting**. Do not add submission, verification, maker/checker, approval or release-gate steps to these core workflows or fabricate approval evidence. Server-side accounting validation, scope, closed-period protection, audit, idempotency and linked reversals remain mandatory.
- Core vouchers and existing operational reports remain **PDF and print only**. The user's 2026-10-15 financial-statement mandate specifically enables PDF/print and Excel for the new statement workspace, its notes, trial balances and ledger drill-downs. Details controls appear only in screen previews, never in either export. This exception does not enable CSV, legacy non-PDF artifacts or Excel for vouchers/other reports; imports remain separate from exports.
- Ordinary core vouchers use ordinary posting accounts and cannot bypass loan controls. The receipt form's explicit Loan repayment purpose delegates to `LoanRecordingService` and `LoanRepaymentLedgerService`; its immutable receipt envelope groups their postings without creating another journal. Preserve due-date allocations, transactional all-row posting, scope and independent linked corrections. See `docs/unified-receipt-vouchers.md`. Existing lending approvals and historical specialized accounting records retain their own safeguards.
- Permanent UI architecture references: [Cloudscape create](https://cloudscape.design/patterns/resource-management/create/), [table view](https://cloudscape.design/patterns/resource-management/view/table-view/), and [details](https://cloudscape.design/patterns/resource-management/details/). Apply their behavior using the existing JSP/shared ERP shell; these references do not authorize a React/framework migration.
- Use separate receipt, payment and journal registers; bounded server-side filters/pagination, single-row selection, disabled selection-dependent toolbar actions, independent Create, focused full-page multi-transaction creation, and record-specific details with PDF/print. Clear selection on query, sort, page or history restoration; no row Actions column.
- The feature architecture and limitations are recorded in `docs/core-accounting-vouchers.md`. This mandate supersedes earlier checklist approval gates for core vouchers only; it does not declare the complete accounting/report-builder backlog finished.

- Shared shell styling lives primarily in:
  - `src/main/webapp/WEB-INF/jsp/fragments/header.jspf`
  - `src/main/webapp/WEB-INF/jsp/fragments/sidebar.jspf`
- Treat the sidebar as the only global navigation for staff and client workspaces.
- Do not add duplicate navigation actions inside page bodies for destinations that already exist in the sidebar.
- Keep dashboard routes focused on summary cards and high-level KPIs. Do not place registry tables, cross-workspace listings, or alternate route launchers on dashboards unless explicitly requested.
- Keep portfolio views and registry views separate when both exist:
  - `Institutions` should remain a focused portfolio/workspace view
  - `Institution Registry` should be its own sidebar destination and view
- Keep visual changes consistent with the current ERP-style staff/client layout. Rebranding does not authorize a framework migration or a wholesale visual redesign.
- Keep panel-header and toolbar action buttons compact by default. Do not stretch actions such as Restore Default Permissions to full width unless a specific mobile form flow explicitly calls for it.
- Workspace table values align with their column headings at the left leading edge, especially Debit/Credit and amounts; do not right-align amounts across wide columns. Retain tabular numerals, precision and contained horizontal scrolling. Standalone PDF/print financial layouts are separate.
- Resource table IDs/codes link directly to their existing authorized details destination using the shared blue `resource-table-id-link` style. These native navigation links do not require checkbox selection; selection still controls toolbar commands. Keep missing or unauthorized destinations as plain text and preserve the no-row-action-button rule. See the project table skill.
- Use one shared modal language across the app. Default to the shared modal shell with calm white surfaces, modest system-aligned corner radii, thin grey borders, light shadows, right-aligned footer actions, and a simple top-right close icon.
- Reduce short-term memory overload:
  - keep page-header explanations brief
  - keep card-header helper text brief or omit it when the title already explains the section
  - avoid stacking multiple instructional sentences when one short line is enough
- Prefer plain-language labels for non-technical microfinance staff and clients:
  - avoid exposing internal codes or technical identifiers unless they are truly needed for the task
  - choose wording a layperson can understand at first glance
- Show principal, due interest, fees, arrears, and total outstanding separately. Do not label the sum of future scheduled payments as today's settlement amount.
- Use `TZS` for monetary context and distinguish transaction dates from instalment due dates. Keep approved rates and their time basis visible where clients make lending decisions.
- Use the existing English/Kiswahili message bundles; do not hardcode English-only labels or invent untranslated regulatory disclosures. The default-language and digital-lending release requirements are tracked in the conversion backlog.
- Keep mandated pricing, consent, guarantor liability, error, and complaint information even when reducing helper copy.
- Preserve responsiveness across:
  - small mobile screens
  - tablets
  - laptops
  - ultra-wide screens
- If changing modal behavior, ensure overlays are appended to `document.body` and cover the full viewport.

## Editing Rules

- Prefer minimal, targeted changes.
- After completing and verifying a user-requested change, automatically create a local Git commit unless the user explicitly asks not to commit.
  - stage only files changed for the current request; never include unrelated or pre-existing worktree changes
  - use a concise commit message that describes the completed change
  - do not amend an existing commit and do not push unless the user explicitly requests it
  - if the current change cannot be separated safely from unrelated edits, leave it uncommitted and clearly report why
- Always consider RESPONSIVENESS FOR MOBILE DEVICES.
- Reuse existing services and controller flows instead of duplicating logic.
- Avoid changing database structure unless explicitly required.
- Avoid changing seeded business rules unless explicitly requested.
- Keep labels and UI wording aligned with existing domain terminology:
  - `Loan ID`
  - `Awaiting Guarantors`
  - `All Guarantors Approved`
  - `On Review By Manager`
  - `On Review By Board`
- The institution is the tenant boundary; branch scope is the default workspace boundary for branch-bound roles:
  - filter workflows, registries, reports, client lists, incidents, and loan listings by institution and the selected branch
  - `LoanApplication.stationId` is the first-class branch reference for loan workflow, reporting, archive, queue, and loan-document visibility paths; prefer it over rebuilding scope from the applicant record
  - if a record lacks a first-class branch reference, call that out clearly before widening access
  - institution-wide risk aggregation needs explicit authorization and minimal returned information; it does not authorize cross-branch registry access

## Testing Checklist

After workflow or UI changes, validate as applicable:

- Client:
  - `/app/dashboard`
  - `/app/loan-products`
  - `/app/loan-applications`
  - `/app/guarantee-requests`
- Admin:
  - `/admin/dashboard`
  - `/admin/users`
  - `/admin/settings-controls`
- Manager:
  - `/manager/loan-applications`
- Board:
  - `/board/assigned`
- Accountant and disbursement:
  - validate the affected queues and record-specific actions when those areas change

Also check:
- login still works
- logout still works
- sidebar/mobile nav still works
- notification and profile dropdowns still behave correctly
- no horizontal overflow appears on common screen sizes
- responsiveness for mobile devices
- institution/branch isolation and client ownership on affected routes
- for financial changes: partial payments, rounding, duplicate submissions, reversals, concurrent posting, backdated payments, early settlement, and reconciliation

## Practical Guidance For Agents

- If the user asks about authentication, answer in terms of session-based Spring Security unless the code has been changed.
- If the user asks about a workflow status, inspect `LoanWorkflowService` first.
- If the user asks about a page layout issue, inspect shared fragments before editing individual JSPs.
- If a modal is visually broken, check whether it is trapped inside the page shell instead of being attached to `document.body`.
- For repayment or balance work, start with `LoanRepaymentLedgerService` and `docs/repayment-ledger-increment.md`, then inspect `RepaymentScheduleService`, `LoanPaymentSummarySyncService`, `LoanPresentationService`, and `LoanAnalyticsService`. Refresh recalculates projections; it does not receive money.
- `V41__local_repayment_ledger.sql` opens no legacy balances. New ordinary disbursements initialize their ledger within the disbursement transaction. Legacy and top-up loans require separately verified opening/settlement work.
- Repayment history, allocations, and journal entries are append-only. Corrections use linked reversals by a different authorized staff member. Never bypass their database protections or assign repayment claims automatically.
- Do not delete ledger-backed loans, institutions, or referenced accounts. Deactivate access instead; repayment audit events are excluded from routine operational cleanup pending an approved financial-record lifecycle.
- The user's 2026-10-19 instruction permits verified TZS repayments of arbitrary positive amounts up to remaining principal, interest due and supported outstanding charges. Preserve due-interest/charge priority, then allocate excess to future principal. Full early repayment records future-interest exclusions separately through V79, without changing contractual interest or recording excluded interest as cash; linked independent reversals restore them. Overpayments/unapplied credit, new fees and channel reconciliation remain separate capabilities. See `docs/repayment-ledger-increment.md`.
- For affordability or guarantor changes, inspect `EligibilityService` and `LoanQualificationPolicyService` before modifying application or review pages.

## Safe Defaults

- Assume business logic should remain unchanged unless the user explicitly asks for a workflow change.
- Assume responsiveness matters for every UI change.
- Assume user-facing IDs and labels should remain readable and domain-friendly.
- Assume production changes must preserve a path toward at least 1,000 requests per second:
  - never add unbounded `findAll()` or institution-wide list loading on request paths
  - prefer institution/branch-scoped `Pageable`, `count`, `exists`, `top`, and aggregate queries
  - keep loan submit and review transactions short, indexed, and free of avoidable external or file work
  - avoid hot-row locks where a small allocation block, queue, or async worker preserves correctness
  - keep outbox/report/notification work batchable and configurable
- This performance target is an architectural constraint, not a verified throughput claim.
